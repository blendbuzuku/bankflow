package com.bankflow.transactionservice.statement;

import com.bankflow.transactionservice.entity.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What an account did over a period.
 *
 * Built from the ledger rather than from transactions: the ledger is what
 * actually moved the balance, so a statement derived from it agrees with the
 * account by construction. A statement built from payments would quietly omit
 * anything booked another way — a fee, a cash operation, the returned leg of a
 * recall.
 *
 * The closing balance is opening plus credits minus debits. That is an
 * arithmetic identity here, not a reconciliation, and if it ever fails to
 * match the account's own balance the ledger has a break that end-of-day
 * should already have found.
 */
public record Statement(

        String statementId,
        Long accountId,
        String iban,
        String accountName,
        Currency currency,

        LocalDate from,
        LocalDate to,

        BigDecimal openingBalance,
        BigDecimal closingBalance,

        BigDecimal totalCredits,
        BigDecimal totalDebits,

        int creditCount,
        int debitCount,

        List<StatementEntry> entries) {

    /** One movement, as the customer sees it. */
    public record StatementEntry(

            String entryReference,
            String transactionReference,

            /** CRDT when money came in, DBIT when it went out. */
            String creditDebitIndicator,

            BigDecimal amount,
            Currency currency,

            LocalDate bookingDate,
            LocalDate valueDate,

            /** Running balance after this entry, which is what people read. */
            BigDecimal balanceAfter,

            /** The other party, or what the movement was. */
            String description) {

        public boolean isCredit() {
            return "CRDT".equals(creditDebitIndicator);
        }
    }

    public int entryCount() {
        return entries.size();
    }
}
