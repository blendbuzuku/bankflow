package com.bankflow.transactionservice.entity;

public enum PaymentDirection {

    /** Both legs are our accounts. Settles immediately, no interbank message. */
    INTERNAL,

    /** We are the debtor agent. Money leaves via a suspense account. */
    OUTBOUND,

    /** We are the creditor agent. Money arrives from another institution. */
    INBOUND
}
