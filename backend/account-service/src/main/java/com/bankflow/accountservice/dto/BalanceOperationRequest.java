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

    /**
     * The bank's business date, which after a close is not today. The movement
     * is filed under the same day the ledger books it to, or reconciliation
     * pairs them across a boundary and reports a break where nothing is wrong.
     *
     * Optional on the wire: a caller that does not send one gets today, which
     * is what every caller meant before the distinction existed.
     */
    private java.time.LocalDate bookingDate;

    public BalanceOperationRequest() {
    }

    public java.time.LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(java.time.LocalDate bookingDate) {
        this.bookingDate = bookingDate;
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