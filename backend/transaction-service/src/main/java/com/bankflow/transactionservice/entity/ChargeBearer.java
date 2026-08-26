package com.bankflow.transactionservice.entity;

/**
 * ISO 20022 charge bearer (ChrgBr) — who carries the cost of the transfer.
 *
 * This materially changes what the beneficiary receives. Under CRED the fee is
 * taken out of the transferred amount, so less money arrives than was
 * instructed; under DEBT the debtor is charged on top and the full instructed
 * amount arrives.
 */
public enum ChargeBearer {

    /** Debtor pays all charges. Fee is debited on top of the amount. */
    DEBT("DEBT", "Borne by debtor"),

    /** Creditor pays all charges. Fee is deducted from the amount received. */
    CRED("CRED", "Borne by creditor"),

    /** Charges shared — each party pays their own side. */
    SHAR("SHAR", "Shared"),

    /** Charges follow the service level / scheme rules. */
    SLEV("SLEV", "Following service level");

    private final String code;
    private final String description;

    ChargeBearer(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
