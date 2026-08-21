package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.BalanceOperationType;
import com.bankflow.accountservice.entity.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class BalanceOperationRequest {

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private BalanceOperationType operation;

    @NotNull
    private Currency currency;

    public BalanceOperationRequest() {
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BalanceOperationType getOperation() {
        return operation;
    }

    public void setOperation(BalanceOperationType operation) {
        this.operation = operation;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }
}