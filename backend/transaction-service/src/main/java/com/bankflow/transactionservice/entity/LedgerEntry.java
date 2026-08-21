package com.bankflow.transactionservice.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
                @Index(name = "idx_ledger_account_id", columnList = "account_id"),
                @Index(name = "idx_ledger_entry_reference", columnList = "entry_reference"),
                @Index(name = "idx_ledger_booking_date", columnList = "booking_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "entry_reference")
        }
)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Transaction this ledger entry belongs to.
     *
     * This is a local database relationship because both
     * Transaction and LedgerEntry belong to transaction-service.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "transaction_id",
            nullable = false
    )
    private Transaction transaction;

    /**
     * Unique identifier for this accounting entry.
     *
     * Example:
     * LED-20260820-000001
     */
    @Column(
            name = "entry_reference",
            nullable = false,
            unique = true,
            length = 40
    )
    private String entryReference;

    /**
     * Account affected by this ledger entry.
     *
     * The actual Account entity belongs to account-service,
     * therefore this is stored as an ID rather than a JPA relationship.
     */
    @Column(
            name = "account_id",
            nullable = false
    )
    private Long accountId;

    /**
     * DEBIT or CREDIT.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "entry_type",
            nullable = false,
            length = 10
    )
    private LedgerEntryType entryType;

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
     * Date on which the accounting entry is booked.
     */
    @Column(
            name = "booking_date",
            nullable = false
    )
    private java.time.LocalDate bookingDate;

    /**
     * Date on which the accounting entry becomes effective.
     */
    @Column(
            name = "value_date",
            nullable = false
    )
    private java.time.LocalDate valueDate;

    @Column(
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public LedgerEntry() {
    }

    public Long getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public String getEntryReference() {
        return entryReference;
    }

    public void setEntryReference(String entryReference) {
        this.entryReference = entryReference;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(LedgerEntryType entryType) {
        this.entryType = entryType;
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

    public java.time.LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(java.time.LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();

        if (bookingDate == null) {
            bookingDate = java.time.LocalDate.now();
        }

        if (valueDate == null) {
            valueDate = bookingDate;
        }
    }
}