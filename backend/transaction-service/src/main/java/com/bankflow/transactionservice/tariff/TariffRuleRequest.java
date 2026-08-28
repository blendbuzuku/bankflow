package com.bankflow.transactionservice.tariff;

import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of the tariff, as somebody maintaining it states it.
 *
 * A charge is a fixed amount, a percentage of the payment, or both, optionally
 * floored and capped. That covers how banks actually price: a flat fee on
 * retail clearing, a percentage on high value with a cap so a large payment
 * does not attract an absurd charge.
 */
public record TariffRuleRequest(

        String ruleCode,

        /** What appears when a customer asks why they were charged. */
        String description,

        PaymentType paymentType,
        Currency currency,

        /** The band this line prices. An empty upper limit is open-ended. */
        BigDecimal minAmount,
        BigDecimal maxAmount,

        BigDecimal fixedFee,
        BigDecimal percentageRate,

        BigDecimal minFee,
        BigDecimal maxFee,

        LocalDate validFrom,
        LocalDate validTo
) {
}
