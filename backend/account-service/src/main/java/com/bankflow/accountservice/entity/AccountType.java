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
    INCOME,

    /**
     * The physical cash the bank is holding.
     *
     * Cash paid in at the counter has to land somewhere on the books, and that
     * somewhere is not suspense. Suspense means "in flight to the scheme, will
     * clear when it answers" -- a banknote handed across a counter is not in
     * flight and never clears, so it sat there permanently and showed up at
     * end of day as an outstanding position that could not be resolved.
     *
     * An asset account. Cash coming in debits it and cash going out credits
     * it, so its movements track the drawer -- though the stored balance runs
     * negative, because the balance column is kept credits-minus-debits for
     * customer accounts and an asset is the other way round.
     */
    VAULT,

    /**
     * The bank's own account at the central bank.
     *
     * Where a payment finally lands once the scheme settles it. Without it
     * settlement had nowhere to post, so it posted nothing -- and the suspense
     * credit raised when the payment was sent stayed there for ever. Suspense
     * is meant to answer one question, how much has left our customers and not
     * yet arrived anywhere, and a balance that also holds every payment ever
     * completed cannot answer it.
     *
     * Its balance is the bank's position with the central bank, which is a
     * figure a treasurer actually looks at.
     */
    SETTLEMENT;

    /**
     * Bank-owned accounts. These belong to the bank as a legal entity rather
     * than to a customer, and must never appear in customer-facing listings.
     */
    public boolean isInternal() {
        return this == SUSPENSE
                || this == INCOME
                || this == VAULT
                || this == SETTLEMENT;
    }
}
