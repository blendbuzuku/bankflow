package com.bankflow.transactionservice.entity;

public enum TransactionType {

    TRANSFER,
    DEPOSIT,
    WITHDRAWAL,

    /**
     * A charge booked against a customer for a parent transfer. Always linked
     * to the transaction that caused it, so a statement can explain the charge.
     */
    FEE,

    /** Undoing our own booking after an internal failure. */
    REVERSAL,

    /** Funds sent back to the debtor after a rejection or a pacs.004. */
    RETURN
}
