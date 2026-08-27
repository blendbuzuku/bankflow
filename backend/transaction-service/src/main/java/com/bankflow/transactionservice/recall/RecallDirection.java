package com.bankflow.transactionservice.recall;

/** Who is asking for the money back. */
public enum RecallDirection {

    /** We sent the payment and want it returned. */
    OUTBOUND,

    /** Another bank sent us a payment and wants it back. */
    INBOUND
}
