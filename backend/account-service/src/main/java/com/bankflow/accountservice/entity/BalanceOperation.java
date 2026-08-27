package com.bankflow.accountservice.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "balance_operations",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "operation_id")
        }
)
public class BalanceOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "operation_id",
            nullable = false,
            unique = true,
            length = 100
    )
    private String operationId;

    @Column(
            name = "account_id",
            nullable = false
    )
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 10
    )
    private BalanceOperationType operation;

    @Column(
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 3
    )
    private Currency currency;

    @Column(
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    /**
     * The business day this movement belongs to, which after a close is not
     * the day it was recorded on.
     */
    @Column(name = "booking_date", nullable = false)
    private java.time.LocalDate bookingDate;

    public BalanceOperation() {
    }

    public Long getId() {
        return id;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BalanceOperationType getOperation() {
        return operation;
    }

    public void setOperation(BalanceOperationType operation) {
        this.operation = operation;
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

    public java.time.LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(java.time.LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}