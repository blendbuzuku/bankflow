package com.bankflow.transactionservice.entity;

/**
 * Lifecycle of a payment.
 *
 * The terminal states are COMPLETED, SETTLED, REJECTED, RETURNED and FAILED;
 * everything else is in flight and may still change.
 */
public enum TransactionStatus {

    /** Created, not yet acted on. */
    PENDING,

    /** Above the four-eyes threshold, waiting for a second approver. */
    PENDING_APPROVAL,

    /** An approver declined it. Nothing was booked. */
    DECLINED,

    /** Being booked. */
    PROCESSING,

    /** Debtor debited and pacs.008 dispatched; awaiting a status report. */
    SENT,

    /** Internal payment, fully booked on both legs. */
    COMPLETED,

    /** External payment confirmed by the counterparty (pacs.002 ACSC). */
    SETTLED,

    /** Counterparty refused it (pacs.002 RJCT). Funds returned to the debtor. */
    REJECTED,

    /** Settled, then sent back by the beneficiary bank (pacs.004). */
    RETURNED,

    /** Failed inside our own processing. */
    FAILED;

    public boolean isTerminal() {

        return this == COMPLETED
                || this == SETTLED
                || this == REJECTED
                || this == RETURNED
                || this == FAILED
                || this == DECLINED;
    }
}
