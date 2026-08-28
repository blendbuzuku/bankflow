package com.bankflow.accountservice.entity;

/**
 * Where the money in this account is expected to come from.
 *
 * Asked once at onboarding and then compared against what actually happens.
 * Its value is entirely in the mismatch: a salaried customer whose account
 * suddenly turns over cash deposits is the pattern monitoring exists to spot,
 * and without a declared baseline there is nothing to notice it against.
 */
public enum SourceOfFunds {

    SALARY("Salary or wages"),
    BUSINESS_INCOME("Business income"),
    PENSION("Pension"),
    SAVINGS("Existing savings"),
    INVESTMENTS("Investment returns"),
    PROPERTY_SALE("Sale of property"),
    INHERITANCE("Inheritance or gift"),
    REMITTANCES("Remittances from family abroad"),
    OTHER("Other");

    private final String label;

    SourceOfFunds(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
