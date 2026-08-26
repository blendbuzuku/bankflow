package com.bankflow.transactionservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The result of matching the ledger against what actually moved.
 *
 * The trial balance proves the ledger adds up. This proves the ledger describes
 * reality — a distinction that matters because a ledger missing both sides of a
 * movement still nets to zero and still looks balanced.
 *
 * @param bookingDate     the day reconciled
 * @param matched         movements present and agreeing on both sides
 * @param unrecordedMoves money moved with no ledger entry behind it
 * @param unmatchedLedger ledger entries with no corresponding movement
 * @param amountMismatch  the same operation booked for different amounts
 * @param unverifiable    ledger entries with no operation id, predating the link
 */
public record Reconciliation(
        LocalDate bookingDate,
        boolean reconciled,
        int breakCount,
        int matched,
        List<Break> unrecordedMoves,
        List<Break> unmatchedLedger,
        List<Break> amountMismatch,
        int unverifiable
) {

    /**
     * Records serialise their components and nothing else, so the verdict is
     * computed here and carried as data rather than exposed as a method a JSON
     * consumer would never see.
     */
    public static Reconciliation of(
            LocalDate bookingDate,
            int matched,
            List<Break> unrecordedMoves,
            List<Break> unmatchedLedger,
            List<Break> amountMismatch,
            int unverifiable) {

        int breaks = unrecordedMoves.size()
                + unmatchedLedger.size()
                + amountMismatch.size();

        return new Reconciliation(
                bookingDate,
                breaks == 0,
                breaks,
                matched,
                unrecordedMoves,
                unmatchedLedger,
                amountMismatch,
                unverifiable
        );
    }

    /**
     * @param operationId the movement that could not be matched
     * @param detail      what is wrong with it, in plain terms
     */
    public record Break(
            String operationId,
            Long accountId,
            String direction,
            BigDecimal ledgerAmount,
            BigDecimal actualAmount,
            String detail
    ) {
    }

    public String describe() {

        if (reconciled) {
            return "Ledger reconciles to account movements for %s (%d matched)"
                    .formatted(bookingDate, matched);
        }

        return ("Ledger DOES NOT reconcile for %s: %d break(s) - "
                + "%d unrecorded move(s), %d unmatched ledger entr(ies), "
                + "%d amount mismatch(es)").formatted(
                bookingDate,
                breakCount,
                unrecordedMoves.size(),
                unmatchedLedger.size(),
                amountMismatch.size()
        );
    }
}
