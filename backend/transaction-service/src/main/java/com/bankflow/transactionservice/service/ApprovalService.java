package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.entity.AuditEventType;
import com.bankflow.transactionservice.entity.PaymentDirection;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four-eyes control.
 *
 * A payment at or above the threshold is parked before anything is booked and
 * before any message is built, so an unapproved payment leaves no trace on the
 * ledger and nothing reaches the scheme. Approval is what releases it.
 *
 * The rule that gives the control its value is that the approver cannot be the
 * maker. Without that it is a second click by the same person, not a second
 * pair of eyes — so it is enforced here rather than left to procedure.
 */
@Service
public class ApprovalService {

    private final TransactionRepository transactionRepository;
    private final ApprovalProperties properties;
    private final AuditService auditService;
    private final OutboundPaymentService outboundPaymentService;
    private final TransactionService transactionService;
    private final FundsCheck fundsCheck;

    public ApprovalService(
            TransactionRepository transactionRepository,
            ApprovalProperties properties,
            AuditService auditService,
            FundsCheck fundsCheck,
            @Lazy OutboundPaymentService outboundPaymentService,
            @Lazy TransactionService transactionService) {

        this.transactionRepository = transactionRepository;
        this.properties = properties;
        this.auditService = auditService;
        this.fundsCheck = fundsCheck;
        this.outboundPaymentService = outboundPaymentService;
        this.transactionService = transactionService;
    }

    public boolean requiresApproval(Transaction transaction) {

        return properties.requiresApproval(
                transaction.getCurrency(),
                transaction.getAmount()
        );
    }

    /**
     * Parks a payment for approval. Called before any booking.
     */
    @Transactional
    public Transaction park(Transaction transaction) {

        transaction.setStatus(TransactionStatus.PENDING_APPROVAL);

        Transaction parked = transactionRepository.save(transaction);

        auditService.record(
                AuditEventType.PAYMENT_SUBMITTED_FOR_APPROVAL,
                parked.getTransactionReference(),
                "Transaction",
                parked.getTransactionReference(),
                ("Payment of %s %s is at or above the %s approval threshold "
                        + "and awaits a second approver").formatted(
                                parked.getAmount(),
                                parked.getCurrency(),
                                properties.thresholdFor(parked.getCurrency())
                        ),
                Map.of(
                        "amount", parked.getAmount(),
                        "currency", parked.getCurrency().name(),
                        "threshold",
                        properties.thresholdFor(parked.getCurrency()),
                        "createdBy",
                        String.valueOf(parked.getCreatedByUsername())
                )
        );

        return parked;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN', 'TELLER')")
    public List<Transaction> awaitingApproval() {

        return transactionRepository.findByStatusOrderByCreatedAtAsc(
                TransactionStatus.PENDING_APPROVAL
        );
    }

    /**
     * Releases a parked payment.
     *
     * Execution happens only after the approval is recorded, and follows the
     * same path the payment would have taken had it never been parked.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    public Transaction approve(String transactionReference) {

        Transaction transaction = requirePending(transactionReference);
        AuthenticatedUser approver = requireDifferentApprover(transaction);

        /*
         * Verify the account can still cover it before recording anything.
         *
         * Execution checks again at the point of booking, but audit events are
         * written in their own transaction and survive a rollback — so failing
         * after the approval is recorded would leave a PAYMENT_APPROVED event
         * for an approval that never took effect. Checking first keeps the
         * trail honest.
         */
        fundsCheck.assertCanCover(transaction);

        transaction.setApprovedByUserId(approver.userId());
        transaction.setApprovedByUsername(approver.username());
        transaction.setApprovedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PENDING);

        Transaction approved = transactionRepository.save(transaction);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("amount", approved.getAmount());
        details.put("currency", approved.getCurrency().name());
        details.put("maker", approved.getCreatedByUsername());
        details.put("checker", approver.username());

        auditService.record(
                AuditEventType.PAYMENT_APPROVED,
                approved.getTransactionReference(),
                "Transaction",
                approved.getTransactionReference(),
                "Approved by %s (created by %s)".formatted(
                        approver.username(),
                        approved.getCreatedByUsername()
                ),
                details
        );

        return approved.getDirection() == PaymentDirection.OUTBOUND
                ? outboundPaymentService.execute(approved)
                : transactionService.executeInternal(approved);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    public Transaction decline(String transactionReference, String reason) {

        Transaction transaction = requirePending(transactionReference);
        AuthenticatedUser approver = requireDifferentApprover(transaction);

        transaction.setStatus(TransactionStatus.DECLINED);
        transaction.setApprovedByUserId(approver.userId());
        transaction.setApprovedByUsername(approver.username());
        transaction.setApprovedAt(LocalDateTime.now());

        transaction.setRejectionReason(
                reason == null || reason.isBlank()
                        ? "Declined by approver"
                        : reason
        );

        Transaction declined = transactionRepository.save(transaction);

        auditService.record(
                AuditEventType.PAYMENT_DECLINED,
                declined.getTransactionReference(),
                "Transaction",
                declined.getTransactionReference(),
                "Declined by %s: %s".formatted(
                        approver.username(),
                        declined.getRejectionReason()
                ),
                Map.of(
                        "maker",
                        String.valueOf(declined.getCreatedByUsername()),
                        "checker", approver.username(),
                        "reason", declined.getRejectionReason()
                )
        );

        /*
         * Nothing was booked while the payment waited, so declining it needs no
         * reversal — there is nothing to undo.
         */
        return declined;
    }

    private Transaction requirePending(String transactionReference) {

        Transaction transaction =
                transactionRepository
                        .findByTransactionReference(transactionReference)
                        .orElseThrow(() -> new BusinessException(
                                "Unknown payment: " + transactionReference
                        ));

        if (transaction.getStatus() != TransactionStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Payment %s is %s and is not awaiting approval".formatted(
                            transactionReference,
                            transaction.getStatus()
                    )
            );
        }

        return transaction;
    }

    /**
     * The whole point of the control: whoever created the payment cannot be the
     * one who releases it.
     */
    private AuthenticatedUser requireDifferentApprover(Transaction transaction) {

        AuthenticatedUser approver = SecurityUtils.getCurrentUser();

        if (transaction.getCreatedByUserId() != null
                && transaction.getCreatedByUserId().equals(approver.userId())) {

            throw new AccessDeniedException(
                    "A payment cannot be approved by the person who created it"
            );
        }

        return approver;
    }

    /**
     * Rebuilds the pricing decided when the payment was created.
     *
     * Deliberately not recalculated: a payment must be charged the tariff that
     * applied when it was instructed, not whatever is live when an approver
     * happens to get to it.
     */
    static FeeAssessment storedFee(Transaction transaction) {

        return new FeeAssessment(
                orZero(transaction.getFeeAmount()),
                orZero(transaction.getDebtorFeeAmount()),
                orZero(transaction.getCreditorFeeAmount()),
                transaction.getCurrency(),
                null,
                "Tariff applied when the payment was created"
        );
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
