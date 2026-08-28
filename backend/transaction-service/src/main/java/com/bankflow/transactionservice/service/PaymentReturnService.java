package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.entity.*;
import com.bankflow.transactionservice.pacs.*;
import com.bankflow.transactionservice.recall.RecallDirection;
import com.bankflow.transactionservice.recall.RecallRequestRepository;
import com.bankflow.transactionservice.recall.RecallStatus;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends settled payments back, and takes them back when they come.
 *
 * A return is not a rejection. A rejection says the payment was never
 * accepted, so the customer is put back exactly where they were, charges
 * included. A return says the payment was accepted, made, and is now being
 * undone by a second payment travelling the other way — so the money comes
 * back but the charge for having made it does not, and the returned amount may
 * be less than the original where the other bank kept its own charges.
 *
 * Both directions book through the ledger poster, so a return reconciles like
 * anything else.
 */
@Service
public class PaymentReturnService {

    private final TransactionRepository transactionRepository;
    private final LedgerPoster ledgerPoster;
    private final SuspenseAccountResolver suspenseAccounts;
    private final Pacs004Builder pacs004Builder;
    private final PacsSchemaValidator validator;
    private final PacsMessageRepository messageRepository;
    private final MessageIdGenerator messageIdGenerator;
    private final AuditService auditService;
    private final RecallRequestRepository recallRepository;

    public PaymentReturnService(
            TransactionRepository transactionRepository,
            LedgerPoster ledgerPoster,
            SuspenseAccountResolver suspenseAccounts,
            Pacs004Builder pacs004Builder,
            PacsSchemaValidator validator,
            PacsMessageRepository messageRepository,
            MessageIdGenerator messageIdGenerator,
            AuditService auditService,
            RecallRequestRepository recallRepository) {

        this.recallRepository = recallRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerPoster = ledgerPoster;
        this.suspenseAccounts = suspenseAccounts;
        this.pacs004Builder = pacs004Builder;
        this.validator = validator;
        this.messageRepository = messageRepository;
        this.messageIdGenerator = messageIdGenerator;
        this.auditService = auditService;
    }

    /**
     * A payment we sent has come back.
     *
     * Booked as the mirror of sending it: the suspense position that recorded
     * the outflow is unwound, and the debtor is credited with whatever actually
     * returned.
     */
    @Transactional
    public Transaction receiveReturn(ParsedMessage parsed) {

        Transaction original = locate(parsed);

        if (original.getDirection() != PaymentDirection.OUTBOUND) {
            throw new BusinessException(
                    "%s is not an outbound payment and cannot be returned to us"
                            .formatted(original.getTransactionReference())
            );
        }

        if (original.getStatus() != TransactionStatus.SETTLED
                && original.getStatus() != TransactionStatus.SENT) {

            throw new BusinessException(
                    "Payment %s is %s and cannot accept a return".formatted(
                            original.getTransactionReference(),
                            original.getStatus()
                    )
            );
        }

        BigDecimal returned =
                parsed.amount() != null ? parsed.amount() : original.getAmount();

        if (returned.compareTo(original.getAmount()) > 0) {
            throw new BusinessException(
                    "A return of %s exceeds the original payment of %s".formatted(
                            returned, original.getAmount()
                    )
            );
        }

        String reference = original.getTransactionReference();
        Currency currency = original.getCurrency();

        ledgerPoster.debit(
                original,
                suspenseAccounts.suspenseAccountId(currency),
                returned,
                reference + "-RTR-SUSPENSE"
        );

        ledgerPoster.credit(
                original,
                original.getSourceAccountId(),
                returned,
                reference + "-RTR-CREDIT"
        );

        original.setStatus(TransactionStatus.RETURNED);

        if (parsed.reasonCode() != null) {
            original.setReasonCode(parsed.reasonCode().getCode());
            original.setRejectionReason(parsed.reasonCode().getDescription());
        }

        Transaction saved = transactionRepository.save(original);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("amountReturned", returned);
        details.put("originalAmount", original.getAmount());
        details.put("creditedTo", original.getSourceAccountId());
        details.put(
                "reasonCode",
                parsed.reasonCode() == null ? "none" : parsed.reasonCode().getCode()
        );

        /*
         * Said explicitly because it is the part people query: the charge is
         * not refunded on a return, only on a rejection.
         */
        details.put("feeRefunded", BigDecimal.ZERO);

        auditService.record(
                AuditEventType.PAYMENT_RETURNED,
                reference,
                "Transaction",
                reference,
                "Returned by the beneficiary bank; %s %s credited to the debtor"
                        .formatted(returned, currency),
                details
        );

        closeRecallAnsweredBy(reference, returned, currency);

        return saved;
    }

    /**
     * Sends back a payment that reached us.
     *
     * Used when the beneficiary cannot or will not keep the money — a closed
     * account found after the fact, or an accepted recall. The beneficiary is
     * debited and the funds go back out through suspense, carried by a
     * pacs.004.
     */
    @Transactional
    public Transaction returnInboundPayment(
            String transactionReference,
            ReasonCode reason,
            String note) {

        Transaction original = transactionRepository
                .findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException(
                        "No payment with reference " + transactionReference
                ));

        if (original.getDirection() != PaymentDirection.INBOUND) {
            throw new BusinessException(
                    ("%s did not come from another bank. Only a payment we "
                            + "received can be returned this way.")
                            .formatted(transactionReference)
            );
        }

        if (original.getStatus() != TransactionStatus.SETTLED) {
            throw new BusinessException(
                    "Payment %s is %s and cannot be returned".formatted(
                            transactionReference, original.getStatus()
                    )
            );
        }

        BigDecimal amount = original.getAmount();
        Currency currency = original.getCurrency();

        /*
         * Build and validate before booking. A message that will not satisfy
         * the scheme's schema must not leave money moved on our side with
         * nothing going out to match it.
         */
        String rail = original.getPaymentType().schemaRail();
        String messageId = messageIdGenerator.newMessageId();
        String returnId = messageIdGenerator.newMessageId();

        String xml = pacs004Builder.build(
                messageId, returnId, original, amount, reason, rail
        );

        PacsSchemaValidator.ValidationResult validation =
                validator.validate(rail, PacsMessageType.PACS_004, xml);

        if (!validation.valid()) {
            throw new BusinessException(
                    "The return message fails the KIPS %s schema: %s".formatted(
                            rail, String.join("; ", validation.errors())
                    )
            );
        }

        ledgerPoster.debit(
                original,
                original.getDestinationAccountId(),
                amount,
                transactionReference + "-RTR-DEBIT"
        );

        ledgerPoster.credit(
                original,
                suspenseAccounts.suspenseAccountId(currency),
                amount,
                transactionReference + "-RTR-SUSPENSE"
        );

        PacsMessage message = new PacsMessage();

        message.setMessageId(messageId);
        message.setMessageType(PacsMessageType.PACS_004);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setTransactionReference(transactionReference);
        message.setEndToEndId(original.getEndToEndId());
        message.setRawXml(xml);
        message.setStatus(PacsMessageStatus.GENERATED);

        messageRepository.save(message);

        original.setStatus(TransactionStatus.RETURNED);
        original.setReasonCode(reason.getCode());
        original.setRejectionReason(
                note == null || note.isBlank() ? reason.getDescription() : note
        );

        Transaction saved = transactionRepository.save(original);

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("amountReturned", amount);
        details.put("debitedFrom", original.getDestinationAccountId());
        details.put("reasonCode", reason.getCode());
        details.put("messageId", messageId);
        details.put("returnedBy", actor.username());

        auditService.record(
                AuditEventType.PAYMENT_RETURNED,
                transactionReference,
                "Transaction",
                transactionReference,
                "Returned to the sending bank by %s (%s); %s %s sent back"
                        .formatted(actor.username(), reason.getCode(), amount, currency),
                details
        );

        return saved;
    }

    /**
     * A return is the answer to a recall we asked for.
     *
     * The counterparty does not tell us twice: money coming back with a
     * pacs.004 is them agreeing, and there is no separate acceptance message
     * to wait for. Leaving the request open afterwards would show a recall
     * still awaiting a reply that has already arrived — and offer to chase an
     * answer we are holding.
     */
    private void closeRecallAnsweredBy(
            String reference, BigDecimal returned, Currency currency) {

        recallRepository
                .findByTransactionReferenceAndStatus(reference, RecallStatus.REQUESTED)
                .filter(recall -> recall.getDirection() == RecallDirection.OUTBOUND)
                .ifPresent(recall -> {

                    recall.setStatus(RecallStatus.ACCEPTED);
                    recall.setDecidedAt(LocalDateTime.now());
                    recall.setDecisionNote(
                            "Answered by a payment return of %s %s".formatted(
                                    returned, currency));

                    recallRepository.save(recall);

                    auditService.record(
                            AuditEventType.RECALL_ACCEPTED,
                            reference,
                            "RecallRequest",
                            recall.getCancellationId(),
                            ("The beneficiary bank agreed to our recall and "
                                    + "returned %s %s").formatted(returned, currency),
                            Map.of(
                                    "cancellationId", recall.getCancellationId(),
                                    "answeredBy", "pacs.004",
                                    "amountReturned", returned
                            )
                    );
                });
    }

    /**
     * Finds the payment a return refers to.
     *
     * The end-to-end reference is the one identifier both banks agree on and
     * carry unchanged, so it is tried first; the transaction reference is our
     * own and only matches when the counterparty echoed it faithfully.
     */
    private Transaction locate(ParsedMessage parsed) {

        if (parsed.endToEndId() != null) {

            var byEndToEnd =
                    transactionRepository.findByEndToEndId(parsed.endToEndId());

            if (byEndToEnd.isPresent()) {
                return byEndToEnd.get();
            }
        }

        if (parsed.transactionId() != null) {

            var byReference = transactionRepository
                    .findByTransactionReference(parsed.transactionId());

            if (byReference.isPresent()) {
                return byReference.get();
            }
        }

        throw new BusinessException(
                "The return references a payment we do not hold: " + parsed.endToEndId()
        );
    }
}
