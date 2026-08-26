package com.bankflow.transactionservice.entity;

/**
 * ISO 20022 service level (SvcLvl/Cd).
 *
 * Describes the settlement commitment the bank makes for a payment.
 */
public enum ServiceLevel {

    SEPA("SEPA", "SEPA scheme rules"),
    INST("INST", "Instant, settled in seconds"),
    URGP("URGP", "Urgent"),
    SDVA("SDVA", "Same day value"),
    NURG("NURG", "Non-urgent");

    private final String code;
    private final String description;

    ServiceLevel(String code, String description) {
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
