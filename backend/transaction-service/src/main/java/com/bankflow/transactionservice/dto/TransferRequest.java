package com.bankflow.transactionservice.dto;

import com.bankflow.transactionservice.entity.ChargeBearer;
import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.entity.PurposeCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class TransferRequest {

    @NotNull
    private Long sourceAccountId;

    /**
     * Required only for on-us payments. An external creditor is identified by
     * IBAN and agent BIC, and has no account with us.
     */
    private Long destinationAccountId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private Currency currency;

    @NotBlank
    private String endToEndId;

    @NotBlank
    private String instructionId;

    /**
     * The rail to send on. Defaults to INTERNAL when omitted, which is the only
     * rail the engine supports until the external routes land.
     */
    private PaymentType paymentType;

    /**
     * Who pays the charge. Defaults to DEBT.
     */
    private ChargeBearer chargeBearer;

    private PurposeCode purposeCode;

    @Size(max = 140, message = "Remittance information must not exceed 140 characters")
    private String remittanceInformation;

    @Size(max = 70, message = "Debtor name must not exceed 70 characters")
    private String debtorName;

    @Size(max = 70, message = "Creditor name must not exceed 70 characters")
    private String creditorName;

    @Size(max = 34)
    private String creditorIban;

    @Size(max = 11)
    private String creditorAgentBic;

    /** ISO 3166-1 alpha-2, which is what the scheme's Ctry element takes. */
    @Size(max = 2)
    private String creditorCountry;

    public TransferRequest() {
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public ChargeBearer getChargeBearer() {
        return chargeBearer;
    }

    public void setChargeBearer(ChargeBearer chargeBearer) {
        this.chargeBearer = chargeBearer;
    }

    public PurposeCode getPurposeCode() {
        return purposeCode;
    }

    public void setPurposeCode(PurposeCode purposeCode) {
        this.purposeCode = purposeCode;
    }

    public String getRemittanceInformation() {
        return remittanceInformation;
    }

    public void setRemittanceInformation(String remittanceInformation) {
        this.remittanceInformation = remittanceInformation;
    }

    public String getDebtorName() {
        return debtorName;
    }

    public void setDebtorName(String debtorName) {
        this.debtorName = debtorName;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public void setCreditorName(String creditorName) {
        this.creditorName = creditorName;
    }

    public String getCreditorIban() {
        return creditorIban;
    }

    public void setCreditorIban(String creditorIban) {
        this.creditorIban = creditorIban;
    }

    public String getCreditorCountry() {
        return creditorCountry;
    }

    public void setCreditorCountry(String creditorCountry) {
        this.creditorCountry = creditorCountry;
    }

    public String getCreditorAgentBic() {
        return creditorAgentBic;
    }

    public void setCreditorAgentBic(String creditorAgentBic) {
        this.creditorAgentBic = creditorAgentBic;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(Long destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }
}