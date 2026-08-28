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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
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

        /*
         * Debits that have not yet been cancelled, oldest first, per payment.
         * A reversal eats into its own payment's debits and nobody else's, so
         * two payments of the same amount cannot pay each other off.
         */
        Map<String, Deque<BigDecimal>> uncancelled = new LinkedHashMap<>();

        BigDecimal reversed = BigDecimal.ZERO;
        BigDecimal arrived = BigDecimal.ZERO;
        int arrivedCount = 0;

        for (LedgerEntry entry : inPeriod) {

            boolean credit = entry.getEntryType() == LedgerEntryType.CREDIT;

            running = credit
                    ? running.add(entry.getAmount())
                    : running.subtract(entry.getAmount());

            if (credit) {
                credits = credits.add(entry.getAmount());
                creditCount++;

                /*
                 * Whatever a reversal cannot cancel is money that genuinely
                 * arrived -- the payment it undoes was made before this
                 * statement began, so this period really is better off by it.
                 */
                BigDecimal cancelled = isReversal(entry)
                        ? cancel(uncancelled, entry)
                        : BigDecimal.ZERO;

                reversed = reversed.add(cancelled);

                BigDecimal net = entry.getAmount().subtract(cancelled);

                if (net.signum() > 0) {
                    arrived = arrived.add(net);
                    arrivedCount++;
                }

            } else {
                debits = debits.add(entry.getAmount());
                debitCount++;

                remember(uncancelled, entry);
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
                netted(uncancelled, arrived, arrivedCount, reversed),
                lines
        );
    }

    /** Files a debit as something a later reversal could cancel. */
    private void remember(
            Map<String, Deque<BigDecimal>> uncancelled, LedgerEntry entry) {

        String key = keyOf(entry);

        if (key == null) {
            return;
        }

        uncancelled
                .computeIfAbsent(key, ignored -> new ArrayDeque<>())
                .addLast(entry.getAmount());
    }

    /**
     * Cancels as much of this payment's outstanding debits as the credit covers.
     *
     * Oldest first, and only within the same payment. Returns what was actually
     * cancelled, which is less than the credit when the debit being undone
     * happened before this statement started.
     */
    private BigDecimal cancel(
            Map<String, Deque<BigDecimal>> uncancelled, LedgerEntry entry) {

        String key = keyOf(entry);

        if (key == null) {
            return BigDecimal.ZERO;
        }

        Deque<BigDecimal> debits = uncancelled.get(key);

        if (debits == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal remaining = entry.getAmount();
        BigDecimal cancelled = BigDecimal.ZERO;

        while (remaining.signum() > 0 && !debits.isEmpty()) {

            BigDecimal debit = debits.removeFirst();
            BigDecimal taken = debit.min(remaining);

            cancelled = cancelled.add(taken);
            remaining = remaining.subtract(taken);

            // A partial return leaves the rest of that debit standing.
            if (debit.compareTo(taken) > 0) {
                debits.addFirst(debit.subtract(taken));
            }
        }

        return cancelled;
    }

    /**
     * What is left once the round trips are taken off both sides.
     *
     * Paid out is whatever debit never got cancelled -- on a returned payment
     * that is the charge alone, which is exactly what the customer is out of
     * pocket and exactly what the closing balance reflects.
     */
    private Statement.Netted netted(
            Map<String, Deque<BigDecimal>> uncancelled,
            BigDecimal arrived,
            int arrivedCount,
            BigDecimal reversed) {

        BigDecimal paidOut = BigDecimal.ZERO;
        int paidOutCount = 0;

        for (Deque<BigDecimal> debits : uncancelled.values()) {
            for (BigDecimal debit : debits) {

                if (debit.signum() > 0) {
                    paidOut = paidOut.add(debit);
                    paidOutCount++;
                }
            }
        }

        return new Statement.Netted(
                arrived, paidOut, arrivedCount, paidOutCount, reversed
        );
    }

    /**
     * Which payment a leg belongs to.
     *
     * The transaction reference, because every leg of one payment shares it --
     * the original debit, its charge, and whatever comes back.
     */
    private String keyOf(LedgerEntry entry) {

        Transaction transaction = entry.getTransaction();

        return transaction == null ? null : transaction.getTransactionReference();
    }

    /**
     * Whether this leg is putting back money an earlier leg took out.
     *
     * Both spellings appear because the two paths are different events: a
     * rejection never left the country and unwinds under -RETURN-, while a
     * return travelled and came back as its own payment under -RTR-.
     */
    private boolean isReversal(LedgerEntry entry) {

        String operation = entry.getOperationId();

        return operation != null
                && (operation.contains("-RTR-") || operation.contains("-RETURN-"));
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

        /*
         * The charge rides on the same transaction as the payment that caused
         * it, so the transaction type cannot tell them apart. The leg's own
         * operation id can, and the customer needs it named: an unexplained
         * difference between what they sent and what left their account is
         * exactly the question a statement should answer.
         */
        if (operation != null && operation.contains("-FEE")) {
            return credit ? "Charge refunded" : "Charge";
        }

        /*
         * A rejection unwinds a payment that never left, so the credit carries
         * the original payment's parties -- and on an outbound payment the
         * debtor is the account holder. Left to the counterparty branch below,
         * this line would tell customers they had been paid by themselves.
         */
        if (operation != null && operation.contains("-RETURN-")) {
            return credit ? "Payment rejected, refunded to you" : "Payment returned";
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
