package com.bankflow.transactionservice.pacs;

import java.util.Arrays;
import java.util.Optional;

/**
 * ISO 20022 transaction status codes carried in a pacs.002.
 *
 * The ACH schema restricts these to CLRD, ACCP, ACSC and RJCT — notably
 * excluding ACTC, which appears in the RTGS examples. RTGS leaves the code list
 * open, so anything valid on ACH is also valid there.
 */
public enum TransactionStatusCode {

    /** Accepted technical validation. RTGS only; rejected by the ACH schema. */
    ACTC("Accepted technical validation", false),

    /** Accepted customer profile — passed profile checks, not yet settled. */
    ACCP("Accepted customer profile", true),

    /** Cleared. */
    CLRD("Cleared", true),

    /** Accepted and settled. This is the code that releases funds. */
    ACSC("Accepted settlement completed", true),

    /** Rejected. Always accompanied by a reason code. */
    RJCT("Rejected", true);

    private final String description;
    private final boolean permittedOnAch;

    TransactionStatusCode(String description, boolean permittedOnAch) {
        this.description = description;
        this.permittedOnAch = permittedOnAch;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }

    public boolean isPermittedOn(String rail) {
        return !"ach".equals(rail) || permittedOnAch;
    }

    /**
     * Whether this status ends the payment's life. Anything else means the
     * counterparty has acknowledged the message but not yet settled it.
     */
    public boolean isFinal() {
        return this == ACSC || this == RJCT || this == CLRD;
    }

    public static Optional<TransactionStatusCode> fromCode(String code) {

        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(code))
                .findFirst();
    }
}
