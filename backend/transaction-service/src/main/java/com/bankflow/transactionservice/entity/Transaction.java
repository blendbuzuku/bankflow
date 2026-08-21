package com.bankflow.transactionservice.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "transaction_reference"),
                @UniqueConstraint(columnNames = "end_to_end_id")
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Internal banking transaction reference.
     *
     * Example:
     * TXN-20260820-000001
     */
    @Column(
            name = "transaction_reference",
            nullable = false,
            unique = true,
            length = 40
    )
    private String transactionReference;

    /**
     * End-to-end payment identifier.
     *
     * Used to trace a payment through its complete lifecycle.
     */
    @Column(
            name = "end_to_end_id",
            nullable = false,
            unique = true,
            length = 50
    )
    private String endToEndId;

    /**
     * Identifier of the original payment instruction.
     */
    @Column(
            name = "instruction_id",
            nullable = false,
            length = 50
    )
    private String instructionId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "transaction_type",
            nullable = false,
            length = 20
    )
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private TransactionStatus status;

    /**
     * Source account.
     *
     * This is an ID belonging to account-service.
     * We deliberately do not create a JPA relationship
     * because Account belongs to another microservice.
     */
    @Column(
            name = "source_account_id",
            nullable = false
    )
    private Long sourceAccountId;

    /**
     * Destination account.
     *
     * Also owned by account-service.
     */
    @Column(
            name = "destination_account_id",
            nullable = false
    )
    private Long destinationAccountId;

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

    /**
     * Date on which the transaction is booked.
     */
    @Column(
            name = "booking_date",
            nullable = false
    )
    private LocalDate bookingDate;

    /**
     * Date on which the funds are considered effective.
     */
    @Column(
            name = "value_date",
            nullable = false
    )
    private LocalDate valueDate;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Transaction() {
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
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

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null) {
            status = TransactionStatus.PENDING;
        }

        if (bookingDate == null) {
            bookingDate = LocalDate.now();
        }

        if (valueDate == null) {
            valueDate = bookingDate;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}