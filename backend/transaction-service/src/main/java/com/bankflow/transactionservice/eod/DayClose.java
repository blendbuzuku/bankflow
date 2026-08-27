package com.bankflow.transactionservice.eod;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A day that has been closed.
 *
 * Closing is not merely a calculation, it is a decision recorded: these are
 * the figures as at the moment somebody signed the day off, and they stay
 * whatever happens to the ledger afterwards. Recomputing the proofs later
 * would answer a different question — what the books say now — where an audit
 * needs to know what they said then, and who accepted it.
 *
 * Once a day is closed it is sealed: nothing may be booked into it and it
 * cannot be closed a second time.
 */
@Entity
@Table(name = "day_closes")
public class DayClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_date", nullable = false, unique = true)
    private LocalDate bookingDate;

    @Column(name = "closed_by_username", nullable = false, length = 100)
    private String closedByUsername;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    /** Both proofs passed. A day only seals when they did. */
    @Column(nullable = false)
    private boolean balanced;

    @Column(nullable = false)
    private boolean reconciled;

    @Column(name = "entry_count", nullable = false)
    private long entryCount;

    @Column(name = "total_debits", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebits = BigDecimal.ZERO;

    @Column(name = "total_credits", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredits = BigDecimal.ZERO;

    @Column(name = "matched_movements", nullable = false)
    private int matchedMovements;

    /** What the person closing was told, kept verbatim. */
    @Column(nullable = false, length = 500)
    private String summary;

    public boolean isSealed() {
        return balanced && reconciled;
    }

    // --- accessors ---

    public Long getId() {
        return id;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public String getClosedByUsername() {
        return closedByUsername;
    }

    public void setClosedByUsername(String closedByUsername) {
        this.closedByUsername = closedByUsername;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public boolean isReconciled() {
        return reconciled;
    }

    public void setReconciled(boolean reconciled) {
        this.reconciled = reconciled;
    }

    public long getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(long entryCount) {
        this.entryCount = entryCount;
    }

    public BigDecimal getTotalDebits() {
        return totalDebits;
    }

    public void setTotalDebits(BigDecimal totalDebits) {
        this.totalDebits = totalDebits;
    }

    public BigDecimal getTotalCredits() {
        return totalCredits;
    }

    public void setTotalCredits(BigDecimal totalCredits) {
        this.totalCredits = totalCredits;
    }

    public int getMatchedMovements() {
        return matchedMovements;
    }

    public void setMatchedMovements(int matchedMovements) {
        this.matchedMovements = matchedMovements;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
