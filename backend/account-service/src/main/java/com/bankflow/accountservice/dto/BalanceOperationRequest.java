package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.BalanceOperationType;
import com.bankflow.accountservice.entity.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank
    private String operationId;

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

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }
}