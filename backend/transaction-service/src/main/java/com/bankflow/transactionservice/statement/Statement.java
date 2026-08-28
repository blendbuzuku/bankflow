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

        /**
         * The same period with money that came straight back taken out.
         *
         * totalCredits and totalDebits above are gross turnover, which is what
         * camt.053 requires and what the entry list shows. They are not what a
         * customer means by "paid in": somebody who deposited thirty thousand
         * and had two payments bounce reads forty thousand, and no arithmetic
         * they can do on the card recovers the figure they know is true.
         */
        Netted netted,

        List<StatementEntry> entries) {

    /**
     * Turnover with round trips removed from both sides.
     *
     * A reversal cancels an equal debit, so taking it off both totals leaves
     * opening + paidIn - paidOut = closing exactly as before -- the customer
     * can still check the card by adding it up, and now the figures are ones
     * they recognise.
     *
     * Only reversals whose original debit falls inside the same period are
     * netted. A payment sent in August and returned in September genuinely is
     * money arriving in September, and September's statement should say so.
     */
    public record Netted(

            BigDecimal paidIn,
            BigDecimal paidOut,

            int paidInCount,
            int paidOutCount,

            /** How much went out and came back without leaving a trace. */
            BigDecimal reversed) {
    }

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
