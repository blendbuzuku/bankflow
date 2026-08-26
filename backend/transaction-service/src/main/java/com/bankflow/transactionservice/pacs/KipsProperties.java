package com.bankflow.transactionservice.pacs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identity and scheme settings used when building KIPS messages.
 *
 * The defaults describe this bank as a KIPS participant. The operator BIC is
 * the Central Bank of Kosovo, which runs both rails and is the {@code To} party
 * on everything we send.
 */
@ConfigurationProperties(prefix = "bankflow.kips")
public class KipsProperties {

    /**
     * Our own BIC, used as the debtor agent and as the {@code Fr} party.
     */
    private String bic = "BANKXKPRXXX";

    /** Legal name, used where a message needs to name us. */
    private String bankName = "BankFlow";

    /**
     * Central Bank of Kosovo — operator of KIPS and the counterparty for every
     * message we send into the scheme.
     */
    private String operatorBic = "CBRKXKPA";

    /**
     * Business service identifier carried in the BAH. KIPS uses the SWIFT
     * Interact usage identifiers; pacs.008 travels on swift.iap.03.
     */
    private String businessService = "swift.iap.03";

    /**
     * Clearing system code for RTGS settlement instructions.
     */
    private String rtgsClearingSystem = "RTG";

    /**
     * Local instrument proprietary code for RTGS customer credit transfers.
     */
    private String rtgsLocalInstrument = "RTGSFIToFICustomerCredit";

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getOperatorBic() {
        return operatorBic;
    }

    public void setOperatorBic(String operatorBic) {
        this.operatorBic = operatorBic;
    }

    public String getBusinessService() {
        return businessService;
    }

    public void setBusinessService(String businessService) {
        this.businessService = businessService;
    }

    public String getRtgsClearingSystem() {
        return rtgsClearingSystem;
    }

    public void setRtgsClearingSystem(String rtgsClearingSystem) {
        this.rtgsClearingSystem = rtgsClearingSystem;
    }

    public String getRtgsLocalInstrument() {
        return rtgsLocalInstrument;
    }

    public void setRtgsLocalInstrument(String rtgsLocalInstrument) {
        this.rtgsLocalInstrument = rtgsLocalInstrument;
    }

    /**
     * The first four characters of our BIC, used to prefix message identifiers
     * the way KIPS participants do (e.g. {@code MBKO2025052290763745}).
     */
    public String institutionPrefix() {
        return bic.substring(0, 4);
    }
}
