package com.bankflow.transactionservice.entity;

import java.util.Arrays;

/**
 * ISO 20022 external purpose code (Purp/Cd) — why the payment was made.
 *
 * The published list runs to several hundred entries; this is the working
 * subset a retail and SME bank actually sees. Purpose drives regulatory
 * reporting and compliance screening rather than price — what a payment costs
 * is decided by {@link PaymentType}.
 */
public enum PurposeCode {

    SALA("SALA", "Salary payment"),
    PENS("PENS", "Pension payment"),
    SUPP("SUPP", "Supplier payment"),
    TAXS("TAXS", "Tax payment"),
    RENT("RENT", "Rent"),
    LOAN("LOAN", "Loan repayment"),
    INTC("INTC", "Intra-company transfer"),
    TRAD("TRAD", "Trade settlement"),
    CASH("CASH", "Cash management transfer"),
    OTHR("OTHR", "Other");

    private final String code;
    private final String description;

    PurposeCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static PurposeCode fromCode(String code) {

        return Arrays.stream(values())
                .filter(purpose -> purpose.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown purpose code: " + code
                        )
                );
    }
}
