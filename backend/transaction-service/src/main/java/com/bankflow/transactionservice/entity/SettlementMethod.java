package com.bankflow.transactionservice.entity;

/**
 * ISO 20022 settlement method (SttlmMtd), as carried in a pacs.008 group header.
 *
 * KIPS pins this to CLRG: both rails settle through the clearing system operated
 * by the Central Bank of Kosovo rather than across bilateral accounts.
 */
public enum SettlementMethod {

    /** Settled through a clearing system. */
    CLRG("CLRG", "Clearing system"),

    /** Settled on the instructed agent's books. */
    INDA("INDA", "Instructed agent"),

    /** Settled on the instructing agent's books. */
    INGA("INGA", "Instructing agent"),

    /** Settled with a cover payment. */
    COVE("COVE", "Cover method");

    private final String code;
    private final String description;

    SettlementMethod(String code, String description) {
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
