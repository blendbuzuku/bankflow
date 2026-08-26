package com.bankflow.transactionservice.pacs;

public enum PacsMessageStatus {

    /**
     * Built and stored, not dispatched. Internal payments stop here for good:
     * the message is kept as evidence of what would have been sent.
     */
    GENERATED,

    /** Handed to the counterparty. */
    SENT,

    /** Counterparty accepted it (pacs.002 ACSC). */
    ACKNOWLEDGED,

    /** Counterparty refused it (pacs.002 RJCT). */
    REJECTED,

    /** We could not build or dispatch it. */
    FAILED
}
