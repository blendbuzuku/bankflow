package com.bankflow.accountservice.entity;

public enum AccountType {

    // --- customer accounts ---

    CURRENT,
    SAVINGS,

    // --- the bank's own accounts ---

    /**
     * Holds the balancing leg of a payment that has left the debtor but not yet
     * reached the beneficiary. Without it an outbound payment would leave the
     * books unbalanced while funds are in flight.
     */
    SUSPENSE,

    /**
     * Where fee income is credited.
     */
    INCOME;

    /**
     * Bank-owned accounts. These belong to the bank as a legal entity rather
     * than to a customer, and must never appear in customer-facing listings.
     */
    public boolean isInternal() {
        return this == SUSPENSE || this == INCOME;
    }
}
