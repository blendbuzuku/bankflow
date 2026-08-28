package com.bankflow.accountservice.entity;

/**
 * How a company is constituted, using the Kosovo business register's forms.
 *
 * It decides who can bind the company and how far the owners' liability runs,
 * which is why a bank records it rather than treating every company alike.
 */
public enum LegalForm {

    SOLE_PROPRIETORSHIP("Individual business (B.I.)"),
    GENERAL_PARTNERSHIP("General partnership (O.P.)"),
    LIMITED_PARTNERSHIP("Limited partnership (Sh.K.M.)"),
    LIMITED_LIABILITY("Limited liability company (Sh.P.K.)"),
    JOINT_STOCK("Joint stock company (Sh.A.)"),
    FOREIGN_BRANCH("Branch of a foreign company"),
    NGO("Non-governmental organisation"),
    OTHER("Other");

    private final String label;

    LegalForm(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
