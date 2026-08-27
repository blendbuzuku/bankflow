package com.bankflow.transactionservice.statement;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.LedgerEntry;
import com.bankflow.transactionservice.entity.LedgerEntryType;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionType;
import com.bankflow.transactionservice.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Produces account statements from the ledger.
 *
 * The ledger is the source, not the transaction table, because the ledger is
 * what actually moved the balance. Deriving the statement from payments would
 * silently omit everything booked another way — a charge, a cash operation,
 * the returning leg of a recall — and a statement that does not explain the
 * balance is worse than none.
 *
 * The opening balance is worked backwards from where the account stands today
 * rather than stored: there is no balance snapshot per day, and reversing the
 * ledger from the present is exact as long as every movement has an entry,
 * which is the invariant end-of-day already proves.
 */
@Service
public class StatementService {

    private final LedgerEntryRepository ledgerEntries;
    private final AccountClient accountClient;

    public StatementService(
            LedgerEntryRepository ledgerEntries,
            AccountClient accountClient) {

        this.ledgerEntries = ledgerEntries;
        this.accountClient = accountClient;
    }

    @Transactional(readOnly = true)
    public Statement forAccount(Long accountId, LocalDate from, LocalDate to) {

        if (from.isAfter(to)) {
            throw new BusinessException(
                    "The statement period ends before it begins"
            );
        }

        AccountResponse account = accountClient.getAccount(accountId);

        Currency currency = Currency.valueOf(account.currency());

        List<LedgerEntry> all = ledgerEntries.findByAccountId(accountId)
                .stream()
                .sorted(
                        Comparator.comparing(LedgerEntry::getBookingDate)
                                .thenComparing(LedgerEntry::getId)
                )
                .toList();

        /*
         * Everything booked after the period is undone from today's balance to
         * find where the account closed; then the period's own entries are
         * undone to find where it opened.
         */
        BigDecimal closing = account.balance();

        for (LedgerEntry entry : all) {
            if (entry.getBookingDate().isAfter(to)) {
                closing = reverse(closing, entry);
            }
        }

        List<LedgerEntry> inPeriod = all.stream()
                .filter(e -> !e.getBookingDate().isBefore(from))
                .filter(e -> !e.getBookingDate().isAfter(to))
                .toList();

        BigDecimal opening = closing;

        for (LedgerEntry entry : inPeriod) {
            opening = reverse(opening, entry);
        }

        // --- walk forwards to give every entry its running balance ---

        List<Statement.StatementEntry> lines = new ArrayList<>();

        BigDecimal running = opening;

        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;

        int creditCount = 0;
        int debitCount = 0;

        for (LedgerEntry entry : inPeriod) {

            boolean credit = entry.getEntryType() == LedgerEntryType.CREDIT;

            running = credit
                    ? running.add(entry.getAmount())
                    : running.subtract(entry.getAmount());

            if (credit) {
                credits = credits.add(entry.getAmount());
                creditCount++;
            } else {
                debits = debits.add(entry.getAmount());
                debitCount++;
            }

            lines.add(new Statement.StatementEntry(
                    entry.getEntryReference(),
                    entry.getTransaction() == null
                            ? null
                            : entry.getTransaction().getTransactionReference(),
                    credit ? "CRDT" : "DBIT",
                    entry.getAmount(),
                    entry.getCurrency(),
                    entry.getBookingDate(),
                    entry.getValueDate(),
                    running,
                    describe(entry, credit)
            ));
        }

        return new Statement(
                statementId(accountId, from, to),
                accountId,
                account.iban(),
                account.clientName(),
                currency,
                from,
                to,
                opening,
                closing,
                credits,
                debits,
                creditCount,
                debitCount,
                lines
        );
    }

    /** Undoes one entry's effect on a balance. */
    private BigDecimal reverse(BigDecimal balance, LedgerEntry entry) {

        return entry.getEntryType() == LedgerEntryType.CREDIT
                ? balance.subtract(entry.getAmount())
                : balance.add(entry.getAmount());
    }

    /**
     * What the customer should read on the line.
     *
     * Named for the other party where there is one, and for the operation where
     * there is not — a charge or a cash movement has no counterparty, and
     * "Received" against a deposit somebody made themselves reads as a mistake.
     */
    private String describe(LedgerEntry entry, boolean credit) {

        Transaction transaction = entry.getTransaction();

        if (transaction == null) {
            return credit ? "Credit" : "Debit";
        }

        if (transaction.getTransactionType() == TransactionType.FEE) {
            return "Charge";
        }

        if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            return "Cash paid in";
        }

        if (transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
            return "Cash withdrawn";
        }

        if (transaction.getTransactionType() == TransactionType.RETURN) {
            return "Payment returned";
        }

        /*
         * A return books against the original payment, so the transaction type
         * still says TRANSFER and the counterparty is whoever it was first
         * time. Describing it that way would show the customer a second
         * ordinary payment where a reversal actually happened, so the leg's own
         * operation id is what distinguishes it.
         */
        String operation = entry.getOperationId();

        if (operation != null && operation.contains("-RTR-")) {
            return credit ? "Payment returned to you" : "Payment returned";
        }

        String counterparty = credit
                ? firstNonBlank(transaction.getDebtorName(), transaction.getDebtorIban())
                : firstNonBlank(transaction.getCreditorName(), transaction.getCreditorIban());

        if (counterparty == null) {
            return credit ? "Credit" : "Debit";
        }

        String remittance = transaction.getRemittanceInformation();

        return remittance == null || remittance.isBlank()
                ? counterparty
                : counterparty + " — " + remittance;
    }

    private static String firstNonBlank(String... values) {

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private static String statementId(Long accountId, LocalDate from, LocalDate to) {

        return "STMT-%d-%s-%s".formatted(
                accountId,
                from.toString().replace("-", ""),
                to.toString().replace("-", "")
        );
    }
}
