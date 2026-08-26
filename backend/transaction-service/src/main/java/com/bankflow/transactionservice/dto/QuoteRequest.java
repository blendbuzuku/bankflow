package com.bankflow.transactionservice.dto;

import com.bankflow.transactionservice.entity.ChargeBearer;
import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Asks what a payment would cost, without booking it.
 */
public class QuoteRequest {

    @NotNull
    private PaymentType paymentType;

    @NotNull
    private Currency currency;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private ChargeBearer chargeBearer;

    public QuoteRequest() {
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public ChargeBearer getChargeBearer() {
        return chargeBearer;
    }

    public void setChargeBearer(ChargeBearer chargeBearer) {
        this.chargeBearer = chargeBearer;
    }
}
