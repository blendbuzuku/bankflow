package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.PaymentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a customer may do without a member of staff.
 *
 * Self-service is deliberately narrower than what a teller can do. A customer
 * paying from their phone has had no identity check beyond a password, so the
 * limits here are a risk control rather than a technical one.
 */
@ConfigurationProperties(prefix = "bankflow.customer")
public class CustomerPaymentProperties {

    private boolean selfServiceEnabled = true;

    /**
     * Rails a customer may use. RTGS is excluded by default: it exists for
     * high-value payments, which is exactly what self-service should not be
     * doing unattended.
     */
    private List<PaymentType> permittedRails =
            List.of(PaymentType.INTERNAL, PaymentType.KIPS_ACH);

    private BigDecimal defaultLimit = new BigDecimal("5000.00");

    private Map<Currency, BigDecimal> limits = new EnumMap<>(Currency.class);

    public BigDecimal limitFor(Currency currency) {
        return limits.getOrDefault(currency, defaultLimit);
    }

    public boolean permits(PaymentType paymentType) {
        return permittedRails.contains(paymentType);
    }

    public boolean isSelfServiceEnabled() {
        return selfServiceEnabled;
    }

    public void setSelfServiceEnabled(boolean selfServiceEnabled) {
        this.selfServiceEnabled = selfServiceEnabled;
    }

    public List<PaymentType> getPermittedRails() {
        return permittedRails;
    }

    public void setPermittedRails(List<PaymentType> permittedRails) {
        this.permittedRails = permittedRails;
    }

    public BigDecimal getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(BigDecimal defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public Map<Currency, BigDecimal> getLimits() {
        return limits;
    }

    public void setLimits(Map<Currency, BigDecimal> limits) {
        this.limits = limits;
    }
}
