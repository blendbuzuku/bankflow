package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.entity.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * When a payment needs a second pair of eyes.
 *
 * Thresholds are per currency because an amount is only meaningful alongside
 * one: 10,000 CHF and 10,000 GBP are not the same risk, and a single number
 * applied to every currency would be arbitrary in all but one of them.
 */
@ConfigurationProperties(prefix = "bankflow.approval")
public class ApprovalProperties {

    /**
     * Whether the control is active at all. Off only makes sense in tests.
     */
    private boolean enabled = true;

    /**
     * Applies to any currency without its own threshold, so a newly supported
     * currency is protected by default rather than exempt by omission.
     */
    private BigDecimal defaultThreshold = new BigDecimal("10000.00");

    private Map<Currency, BigDecimal> thresholds = new EnumMap<>(Currency.class);

    /**
     * The amount at or above which a payment must be approved.
     */
    public BigDecimal thresholdFor(Currency currency) {

        return thresholds.getOrDefault(currency, defaultThreshold);
    }

    public boolean requiresApproval(Currency currency, BigDecimal amount) {

        if (!enabled || amount == null) {
            return false;
        }

        return amount.compareTo(thresholdFor(currency)) >= 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getDefaultThreshold() {
        return defaultThreshold;
    }

    public void setDefaultThreshold(BigDecimal defaultThreshold) {
        this.defaultThreshold = defaultThreshold;
    }

    public Map<Currency, BigDecimal> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<Currency, BigDecimal> thresholds) {
        this.thresholds = thresholds;
    }
}
