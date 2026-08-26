package com.bankflow.transactionservice.config;

import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.FeeRule;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.repository.FeeRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Installs a starter tariff.
 *
 * Seeded by rule code and never updated in place, so editing prices in the
 * database is safe — a restart will not silently overwrite them. Repricing
 * should add a new effective-dated rule rather than change these.
 */
@Configuration
public class TariffSeeder {

    private static final Logger log =
            LoggerFactory.getLogger(TariffSeeder.class);

    @Bean
    public ApplicationRunner seedTariff(FeeRuleRepository feeRuleRepository) {

        return (ApplicationArguments args) -> {

            int created = 0;

            /*
             * Internal transfers are free. Represented as an explicit zero rule
             * rather than an absent one so the audit trail can still name the
             * tariff line that produced a zero charge.
             */
            created += seed(
                    feeRuleRepository,
                    rule(
                            "TARIFF-INTL-EUR",
                            "Internal transfer - no charge",
                            PaymentType.INTERNAL,
                            Currency.EUR,
                            BigDecimal.ZERO, null,
                            BigDecimal.ZERO, BigDecimal.ZERO,
                            null, null
                    )
            );

            /*
             * KIPS ACH is the retail rail: cheap, flat, and banded so small
             * payments cost less than large ones.
             */
            created += seed(
                    feeRuleRepository,
                    rule(
                            "TARIFF-ACH-EUR-LOW",
                            "KIPS ACH up to EUR 1,000",
                            PaymentType.KIPS_ACH,
                            Currency.EUR,
                            BigDecimal.ZERO, new BigDecimal("1000.00"),
                            new BigDecimal("0.50"), BigDecimal.ZERO,
                            null, null
                    )
            );

            created += seed(
                    feeRuleRepository,
                    rule(
                            "TARIFF-ACH-EUR-HIGH",
                            "KIPS ACH above EUR 1,000",
                            PaymentType.KIPS_ACH,
                            Currency.EUR,
                            new BigDecimal("1000.00"), null,
                            new BigDecimal("1.00"), BigDecimal.ZERO,
                            null, null
                    )
            );

            /*
             * RTGS carries high-value payments and is priced accordingly:
             * a percentage with a floor, since each one settles individually.
             */
            created += seed(
                    feeRuleRepository,
                    rule(
                            "TARIFF-RTGS-EUR",
                            "KIPS RTGS - 0.05%, min 5.00, max 50.00",
                            PaymentType.KIPS_RTGS,
                            Currency.EUR,
                            BigDecimal.ZERO, null,
                            BigDecimal.ZERO, new BigDecimal("0.0500"),
                            new BigDecimal("5.00"), new BigDecimal("50.00")
                    )
            );

            // International: percentage priced with a floor and a cap.
            for (Currency currency : new Currency[]{
                    Currency.EUR, Currency.USD, Currency.GBP, Currency.CHF}) {

                created += seed(
                        feeRuleRepository,
                        rule(
                                "TARIFF-SWIFT-" + currency.name(),
                                "International payment - 0.30%, min 15.00, max 150.00",
                                PaymentType.INTERNATIONAL,
                                currency,
                                BigDecimal.ZERO, null,
                                BigDecimal.ZERO, new BigDecimal("0.3000"),
                                new BigDecimal("15.00"), new BigDecimal("150.00")
                        )
                );
            }

            if (created > 0) {
                log.info("Tariff seeded with {} new fee rule(s)", created);
            }
        };
    }

    private int seed(FeeRuleRepository repository, FeeRule rule) {

        if (repository.existsByRuleCode(rule.getRuleCode())) {
            return 0;
        }

        repository.save(rule);
        return 1;
    }

    private FeeRule rule(
            String code,
            String description,
            PaymentType paymentType,
            Currency currency,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal fixedFee,
            BigDecimal percentageRate,
            BigDecimal minFee,
            BigDecimal maxFee) {

        FeeRule rule = new FeeRule();

        rule.setRuleCode(code);
        rule.setDescription(description);
        rule.setPaymentType(paymentType);
        rule.setCurrency(currency);
        rule.setMinAmount(minAmount);
        rule.setMaxAmount(maxAmount);
        rule.setFixedFee(fixedFee);
        rule.setPercentageRate(percentageRate);
        rule.setMinFee(minFee);
        rule.setMaxFee(maxFee);
        rule.setActive(true);

        return rule;
    }
}
