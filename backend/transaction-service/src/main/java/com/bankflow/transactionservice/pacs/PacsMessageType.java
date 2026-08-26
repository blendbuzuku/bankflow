package com.bankflow.transactionservice.pacs;

/**
 * ISO 20022 message types this bank handles.
 *
 * The version suffix is part of the identity: a counterparty needs to know it
 * received pacs.008.001.08 and not some other revision, and the value is echoed
 * back in a status report's OrgnlMsgNmId.
 */
public enum PacsMessageType {

    PACS_008("pacs.008.001.08", "FI to FI customer credit transfer"),
    PACS_002("pacs.002.001.10", "FI to FI payment status report"),
    PACS_004("pacs.004.001.09", "Payment return"),
    PAIN_001("pain.001.001.09", "Customer credit transfer initiation");

    private final String identifier;
    private final String description;

    PacsMessageType(String identifier, String description) {
        this.identifier = identifier;
        this.description = description;
    }

    /** e.g. {@code pacs.008.001.08} — used as the XML namespace suffix. */
    public String getIdentifier() {
        return identifier;
    }

    public String getDescription() {
        return description;
    }

    public String getNamespace() {
        return "urn:iso:std:iso:20022:tech:xsd:" + identifier;
    }
}
