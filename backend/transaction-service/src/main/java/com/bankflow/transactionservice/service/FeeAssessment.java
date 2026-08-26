package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.entity.Currency;

import java.math.BigDecimal;

/**
 * The outcome of pricing one payment.
 *
 * Splitting the total into a debtor share and a creditor share here means the
 * booking logic never has to reason about charge bearers — it simply debits and
 * credits what it is told.
 *
 * @param totalFee     total charge assessed
 * @param debtorFee    charged to the debtor on top of the amount
 * @param creditorFee  deducted from what the creditor receives
 * @param currency     currency of the charge, always the payment currency
 * @param ruleCode     tariff line applied, recorded for audit
 * @param description  human-readable explanation for statements
 */
public record FeeAssessment(
        BigDecimal totalFee,
        BigDecimal debtorFee,
        BigDecimal creditorFee,
        Currency currency,
        String ruleCode,
        String description
) {

    public boolean isFree() {
        return totalFee.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * What the debtor's account is actually debited: amount plus their share.
     */
    public BigDecimal totalDebitFor(BigDecimal amount) {
        return amount.add(debtorFee);
    }

    /**
     * What lands in the creditor's account: amount less their share.
     */
    public BigDecimal netCreditFor(BigDecimal amount) {
        return amount.subtract(creditorFee);
    }

    public static FeeAssessment free(Currency currency, String reason) {

        return new FeeAssessment(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                currency,
                null,
                reason
        );
    }
}
