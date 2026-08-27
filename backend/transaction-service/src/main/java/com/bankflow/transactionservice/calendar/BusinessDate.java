package com.bankflow.transactionservice.calendar;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The day the bank is currently trading into.
 *
 * Not the same thing as the calendar date, and the difference is the whole
 * point. Closing a day does not stop business — it rolls this forward, so work
 * done after the close books into the next day and reconciles on that day's
 * proofs. Without the distinction, closing would either seal the books and
 * halt the bank until midnight, or let entries land in a day somebody has
 * already signed off.
 *
 * A single row. Trading happens on one day at a time.
 */
@Entity
@Table(name = "business_date")
public class BusinessDate {

    /** Always 1: there is one current business date, not a collection. */
    public static final long SINGLETON = 1L;

    @Id
    private Long id = SINGLETON;

    @Column(name = "trading_date", nullable = false)
    private LocalDate currentDate;

    @Column(name = "rolled_at")
    private LocalDateTime rolledAt;

    @Column(name = "rolled_by_username", length = 100)
    private String rolledByUsername;

    public Long getId() {
        return id;
    }

    public LocalDate getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(LocalDate currentDate) {
        this.currentDate = currentDate;
    }

    public LocalDateTime getRolledAt() {
        return rolledAt;
    }

    public void setRolledAt(LocalDateTime rolledAt) {
        this.rolledAt = rolledAt;
    }

    public String getRolledByUsername() {
        return rolledByUsername;
    }

    public void setRolledByUsername(String rolledByUsername) {
        this.rolledByUsername = rolledByUsername;
    }
}
