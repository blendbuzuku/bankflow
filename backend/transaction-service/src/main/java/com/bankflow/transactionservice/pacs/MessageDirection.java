package com.bankflow.transactionservice.pacs;

public enum MessageDirection {

    /** We produced it and sent it, or would send it. */
    OUTBOUND,

    /** A counterparty sent it to us. */
    INBOUND
}
