package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.dto.BalanceOperationRequest;
import com.bankflow.transactionservice.entity.AuditEventType;
import com.bankflow.transactionservice.entity.LedgerEntry;
import com.bankflow.transactionservice.entity.LedgerEntryType;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Moves money on one account and records that it happened.
 *
 * Every balance change in the bank goes through here, so three things are
 * guaranteed together and cannot drift apart: the account balance is updated,
 * a ledger entry is written, and an audit event records the movement with the
 * balance before and after.
 *
 * Operation identifiers are supplied by the caller and derived from the
 * transaction reference, which is what makes a retried booking idempotent —
 * account-service refuses to apply the same operation identifier twice.
 */
@Service
public class LedgerPoster {

    private final AccountClient accountClient;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    public LedgerPoster(
            AccountClient accountClient,
            LedgerEntryRepository ledgerEntryRepository,
            AuditService auditService) {

        this.accountClient = accountClient;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
    }

    public LedgerEntry debit(
            Transaction transaction,
            Long accountId,
            BigDecimal amount,
            String operationId) {

        return post(
                transaction,
                accountId,
                amount,
                operationId,
                LedgerEntryType.DEBIT
        );
    }

    public LedgerEntry credit(
            Transaction transaction,
            Long accountId,
            BigDecimal amount,
            String operationId) {

        return post(
                transaction,
                accountId,
                amount,
                operationId,
                LedgerEntryType.CREDIT
        );
    }

    private LedgerEntry post(
            Transaction transaction,
            Long accountId,
            BigDecimal amount,
            String operationId,
            LedgerEntryType entryType) {

        BalanceOperationRequest request =
                new BalanceOperationRequest(
                        amount,
                        entryType.name(),
                        transaction.getCurrency().name(),
                        operationId
                );

        AccountResponse result =
                accountClient.applyBalanceOperation(accountId, request);

        LedgerEntry entry = new LedgerEntry();

        entry.setTransaction(transaction);
        entry.setEntryReference(newEntryReference());
        entry.setAccountId(accountId);
        entry.setOperationId(operationId);
        entry.setEntryType(entryType);
        entry.setAmount(amount);
        entry.setCurrency(transaction.getCurrency());

        entry.setBookingDate(
                transaction.getBookingDate() != null
                        ? transaction.getBookingDate()
                        : LocalDate.now()
        );

        entry.setValueDate(
                transaction.getValueDate() != null
                        ? transaction.getValueDate()
                        : LocalDate.now()
        );

        LedgerEntry saved = ledgerEntryRepository.save(entry);

        recordMovement(transaction, saved, result, operationId);

        return saved;
    }

    private void recordMovement(
            Transaction transaction,
            LedgerEntry entry,
            AccountResponse result,
            String operationId) {

        boolean isDebit = entry.getEntryType() == LedgerEntryType.DEBIT;

        BigDecimal balanceAfter = result != null ? result.balance() : null;

        /*
         * The resulting balance comes back from account-service; the prior one
         * is derived from it, so the trail answers "what was this account
         * before and after" without replaying every earlier entry.
         */
        BigDecimal balanceBefore =
                balanceAfter == null
                        ? null
                        : isDebit
                                ? balanceAfter.add(entry.getAmount())
                                : balanceAfter.subtract(entry.getAmount());

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("accountId", entry.getAccountId());
        details.put("direction", entry.getEntryType().name());
        details.put("amount", entry.getAmount());
        details.put("currency", entry.getCurrency().name());
        details.put("operationId", operationId);
        details.put("entryReference", entry.getEntryReference());
        details.put("balanceBefore", balanceBefore);
        details.put("balanceAfter", balanceAfter);

        auditService.record(
                isDebit
                        ? AuditEventType.ACCOUNT_DEBITED
                        : AuditEventType.ACCOUNT_CREDITED,
                transaction.getTransactionReference(),
                "Account",
                String.valueOf(entry.getAccountId()),
                "Account %d %s %s %s".formatted(
                        entry.getAccountId(),
                        isDebit ? "debited" : "credited",
                        entry.getAmount(),
                        entry.getCurrency()
                ),
                details
        );
    }

    private String newEntryReference() {

        return "LED-" + LocalDate.now() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
