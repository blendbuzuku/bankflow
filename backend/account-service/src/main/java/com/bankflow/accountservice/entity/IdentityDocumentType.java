package com.bankflow.accountservice.entity;

/**
 * What was put on the counter to prove somebody is who they say they are.
 *
 * Kept to documents a Kosovo branch would actually accept. A driving licence
 * is absent deliberately: it proves entitlement to drive, not identity, and no
 * AML regime accepts it as a primary document.
 */
public enum IdentityDocumentType {

    PASSPORT("Passport"),
    NATIONAL_ID("National identity card"),
    RESIDENCE_PERMIT("Residence permit");

    private final String label;

    IdentityDocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
