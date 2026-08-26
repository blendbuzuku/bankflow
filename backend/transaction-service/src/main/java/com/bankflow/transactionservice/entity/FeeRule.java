package com.bankflow.transactionservice.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One line of the bank's tariff.
 *
 * A rule applies to a payment type and currency within an amount band. Pricing
 * is fixed + percentage, bounded by an optional floor and cap — which is how
 * real published tariffs read ("0.3%, min EUR 15, max EUR 150").
 *
 * Rules are effective-dated rather than edited in place: repricing adds a new
 * row and closes the old one, so a historic payment can always be explained
 * against the tariff that was live when it was booked.
 */
@Entity
@Table(
        name = "fee_rules",
        indexes = {
                @Index(
                        name = "idx_fee_rule_lookup",
                        columnList = "payment_type, currency, active"
                )
        }
)
public class FeeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable identifier used in audit records and on statements,
     * e.g. TARIFF-SCT-EUR-STD.
     */
    @Column(name = "rule_code", nullable = false, unique = true, length = 50)
    private String ruleCode;

    @Column(name = "description", nullable = false, length = 140)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 30)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    /** Inclusive lower bound of the amount band. */
    @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minAmount;

    /**
     * Exclusive upper bound. Null means the band is open-ended.
     */
    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "fixed_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal fixedFee = BigDecimal.ZERO;

    /** Percentage of the payment amount, expressed as a percent (0.30 = 0.30%). */
    @Column(name = "percentage_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal percentageRate = BigDecimal.ZERO;

    @Column(name = "min_fee", precision = 19, scale = 2)
    private BigDecimal minFee;

    @Column(name = "max_fee", precision = 19, scale = 2)
    private BigDecimal maxFee;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FeeRule() {
    }

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();

        if (validFrom == null) {
            validFrom = LocalDate.now();
        }
    }

    /**
     * Whether this rule covers the given amount on the given date.
     */
    public boolean covers(BigDecimal amount, LocalDate on) {

        if (!active) {
            return false;
        }

        if (validFrom != null && on.isBefore(validFrom)) {
            return false;
        }

        if (validTo != null && on.isAfter(validTo)) {
            return false;
        }

        if (amount.compareTo(minAmount) < 0) {
            return false;
        }

        return maxAmount == null || amount.compareTo(maxAmount) < 0;
    }

    public Long getId() {
        return id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public BigDecimal getFixedFee() {
        return fixedFee;
    }

    public void setFixedFee(BigDecimal fixedFee) {
        this.fixedFee = fixedFee;
    }

    public BigDecimal getPercentageRate() {
        return percentageRate;
    }

    public void setPercentageRate(BigDecimal percentageRate) {
        this.percentageRate = percentageRate;
    }

    public BigDecimal getMinFee() {
        return minFee;
    }

    public void setMinFee(BigDecimal minFee) {
        this.minFee = minFee;
    }

    public BigDecimal getMaxFee() {
        return maxFee;
    }

    public void setMaxFee(BigDecimal maxFee) {
        this.maxFee = maxFee;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
