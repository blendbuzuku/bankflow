package com.bankflow.transactionservice.pacs;

/**
 * The KIPS message definitions this bank speaks.
 *
 * A definition is versioned per rail. Most are the same on both, but a return
 * is pacs.004.001.09 on ACH and .10 on RTGS, and a cancellation request ships
 * only with the ACH schema set — so the identifier, and therefore the
 * namespace, has to be asked for by rail rather than assumed.
 */
public enum PacsMessageType {

    PACS_008(
            "pacs.008.001.08", "pacs.008.001.08",
            "FI to FI customer credit transfer"),

    PACS_002(
            "pacs.002.001.10", "pacs.002.001.10",
            "FI to FI payment status report"),

    PACS_004(
            "pacs.004.001.09", "pacs.004.001.10",
            "Payment return"),

    /**
     * A request to send a settled payment back. RTGS settles gross and final,
     * and the operator publishes no camt.056 for it, so recalls are an ACH
     * facility here.
     */
    CAMT_056(
            "camt.056.001.08", null,
            "FI to FI payment cancellation request"),

    /**
     * Account statement. Published only in the RTGS set, but it describes an
     * account rather than a rail, so it is used for every account we hold.
     */
    CAMT_053(
            null, "camt.053.001.08",
            "Bank to customer statement");

    private final String achIdentifier;
    private final String rtgsIdentifier;
    private final String description;

    PacsMessageType(
            String achIdentifier,
            String rtgsIdentifier,
            String description) {

        this.achIdentifier = achIdentifier;
        this.rtgsIdentifier = rtgsIdentifier;
        this.description = description;
    }

    /** The ACH identifier, which is the one most call sites want. */
    public String getIdentifier() {
        return achIdentifier;
    }

    public String getIdentifier(String rail) {

        String identifier = "rtgs".equals(rail) ? rtgsIdentifier : achIdentifier;

        if (identifier == null) {
            throw new IllegalArgumentException(
                    "%s is not published for the %s rail".formatted(name(), rail)
            );
        }

        return identifier;
    }

    public boolean isSupportedOn(String rail) {
        return ("rtgs".equals(rail) ? rtgsIdentifier : achIdentifier) != null;
    }

    public String getDescription() {
        return description;
    }

    public String getNamespace() {
        return namespaceOf(achIdentifier);
    }

    public String getNamespace(String rail) {
        return namespaceOf(getIdentifier(rail));
    }

    private static String namespaceOf(String identifier) {
        return "urn:iso:std:iso:20022:tech:xsd:" + identifier;
    }
}
