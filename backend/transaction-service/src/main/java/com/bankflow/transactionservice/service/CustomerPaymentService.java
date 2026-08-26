package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.dto.TransferRequest;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Payments a customer makes for themselves.
 *
 * The control that matters here is ownership. A teller acts for the bank and
 * may debit any account; a customer may debit only their own, and without that
 * check any authenticated customer could move money out of any account simply
 * by knowing its ID. That was why the payment endpoints were staff-only until
 * now.
 *
 * Once the instruction is validated it goes through the same engine a teller's
 * payment does — including the approval threshold. Self-service is a narrower
 * doorway into the same building, not a second building.
 */
@Service
public class CustomerPaymentService {

    private final AccountClient accountClient;
    private final CustomerPaymentProperties properties;
    private final OutboundPaymentService outboundPaymentService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    public CustomerPaymentService(
            AccountClient accountClient,
            CustomerPaymentProperties properties,
            OutboundPaymentService outboundPaymentService,
            TransactionService transactionService,
            TransactionRepository transactionRepository) {

        this.accountClient = accountClient;
        this.properties = properties;
        this.outboundPaymentService = outboundPaymentService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction send(TransferRequest request) {

        if (!properties.isSelfServiceEnabled()) {
            throw new BusinessException(
                    "Self-service payments are not currently available"
            );
        }

        AuthenticatedUser customer = SecurityUtils.getCurrentUser();
        Set<Long> ownAccounts = ownedAccountIds(customer.userId());

        /*
         * The check this whole class exists for.
         */
        if (request.getSourceAccountId() == null
                || !ownAccounts.contains(request.getSourceAccountId())) {

            throw new AccessDeniedException(
                    "You can only pay from your own accounts"
            );
        }

        PaymentType paymentType =
                request.getPaymentType() != null
                        ? request.getPaymentType()
                        : PaymentType.INTERNAL;

        if (!properties.permits(paymentType)) {
            throw new BusinessException(
                    "%s is not available for self-service. Please visit a branch."
                            .formatted(paymentType.getDisplayName())
            );
        }

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException("Enter an amount greater than zero");
        }

        BigDecimal limit = properties.limitFor(request.getCurrency());

        if (request.getAmount().compareTo(limit) > 0) {
            throw new BusinessException(
                    ("Self-service payments are limited to %s %s. "
                            + "Please visit a branch for a larger payment.")
                            .formatted(limit, request.getCurrency())
            );
        }

        /*
         * An internal transfer to another of our customers still names a
         * destination account; paying yourself is not a payment.
         */
        if (!paymentType.isExternal()) {

            if (request.getDestinationAccountId() == null) {
                throw new BusinessException(
                        "Choose an account to pay into"
                );
            }

            if (request.getDestinationAccountId()
                    .equals(request.getSourceAccountId())) {

                throw new BusinessException(
                        "The paying and receiving accounts must be different"
                );
            }
        }

        request.setPaymentType(paymentType);

        return paymentType.isExternal()
                ? outboundPaymentService.send(request)
                : transactionService.createTransfer(
                        request.getSourceAccountId(),
                        request.getDestinationAccountId(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getEndToEndId(),
                        request.getInstructionId(),
                        paymentType,
                        request.getChargeBearer(),
                        request.getPurposeCode(),
                        request.getRemittanceInformation()
                );
    }

    /**
     * The customer's own payment history.
     *
     * Filtered by the accounts they hold rather than by who created the
     * payment, so a transfer a teller made on their behalf still appears — it
     * is their money either way.
     */
    @Transactional(readOnly = true)
    public List<Transaction> ownHistory() {

        AuthenticatedUser customer = SecurityUtils.getCurrentUser();
        Set<Long> ownAccounts = ownedAccountIds(customer.userId());

        if (ownAccounts.isEmpty()) {
            return List.of();
        }

        return transactionRepository
                .findBySourceAccountIdInOrDestinationAccountIdInOrderByCreatedAtDesc(
                        ownAccounts, ownAccounts
                );
    }

    /**
     * One payment, but only if it touches an account the customer holds.
     */
    @Transactional(readOnly = true)
    public Transaction ownTransaction(String transactionReference) {

        AuthenticatedUser customer = SecurityUtils.getCurrentUser();
        Set<Long> ownAccounts = ownedAccountIds(customer.userId());

        Transaction transaction =
                transactionRepository
                        .findByTransactionReference(transactionReference)
                        .orElseThrow(() -> new BusinessException(
                                "Unknown payment: " + transactionReference
                        ));

        boolean theirs =
                ownAccounts.contains(transaction.getSourceAccountId())
                        || ownAccounts.contains(transaction.getDestinationAccountId());

        if (!theirs) {
            /*
             * Refused as forbidden rather than not-found, since the payment
             * does exist — it is simply none of their business.
             */
            throw new AccessDeniedException(
                    "That payment does not belong to you"
            );
        }

        return transaction;
    }

    @Transactional(readOnly = true)
    public Set<Long> ownedAccountIds(Long userId) {

        return accountClient.accountsForUser(userId)
                .stream()
                .map(AccountResponse::id)
                .collect(Collectors.toSet());
    }
}
