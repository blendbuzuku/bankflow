package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.entity.ChargeBearer;
import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.FeeRule;
import com.bankflow.transactionservice.entity.PaymentDirection;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.repository.FeeRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Prices a payment against the bank's tariff.
 *
 * Pricing is deliberately separate from booking: this decides what is owed and
 * by whom, and the payment engine moves the money. That split keeps charge
 * bearer rules in one place and makes the outcome testable without a ledger.
 */
@Service
public class FeeService {

    private static final int MONEY_SCALE = 2;

    private final FeeRuleRepository feeRuleRepository;

    public FeeService(FeeRuleRepository feeRuleRepository) {
        this.feeRuleRepository = feeRuleRepository;
    }

    @Transactional(readOnly = true)
    public FeeAssessment assess(
            PaymentType paymentType,
            Currency currency,
            BigDecimal amount,
            ChargeBearer chargeBearer,
            PaymentDirection direction,
            LocalDate bookingDate) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Cannot price a payment without a positive amount"
            );
        }

        FeeRule rule = resolveRule(
                paymentType,
                currency,
                amount,
                bookingDate
        );

        if (rule == null) {

            /*
             * No tariff line is a deliberate "free" outcome, not an error.
             * Internal transfers are normally priced at zero by simply having
             * no rule.
             */
            return FeeAssessment.free(
                    currency,
                    "No tariff applies to " + paymentType.getCode()
            );
        }

        BigDecimal total = computeFee(rule, amount);

        return allocate(
                total,
                currency,
                chargeBearer,
                direction,
                rule
        );
    }

    /**
     * The narrowest matching band wins, so a specific low-value rule beats an
     * open-ended catch-all.
     */
    private FeeRule resolveRule(
            PaymentType paymentType,
            Currency currency,
            BigDecimal amount,
            LocalDate bookingDate) {

        List<FeeRule> candidates =
                feeRuleRepository
                        .findByPaymentTypeAndCurrencyAndActiveTrue(
                                paymentType,
                                currency
                        );

        return candidates.stream()
                .filter(candidate -> candidate.covers(amount, bookingDate))
                .min(Comparator.comparing(this::bandWidth))
                .orElse(null);
    }

    private BigDecimal bandWidth(FeeRule rule) {

        if (rule.getMaxAmount() == null) {
            return new BigDecimal(Long.MAX_VALUE);
        }

        return rule.getMaxAmount().subtract(rule.getMinAmount());
    }

    private BigDecimal computeFee(FeeRule rule, BigDecimal amount) {

        BigDecimal percentageComponent = amount
                .multiply(rule.getPercentageRate())
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal fee = rule.getFixedFee().add(percentageComponent);

        if (rule.getMinFee() != null && fee.compareTo(rule.getMinFee()) < 0) {
            fee = rule.getMinFee();
        }

        if (rule.getMaxFee() != null && fee.compareTo(rule.getMaxFee()) > 0) {
            fee = rule.getMaxFee();
        }

        return fee.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Splits the charge according to the ISO charge bearer.
     *
     * SHAR and SLEV both mean "each party pays their own bank", so what we can
     * actually collect depends on which agent we are. Sending a payment out, we
     * charge our own customer (the debtor) and the beneficiary's bank charges
     * them separately — money that never touches our books. Only when both
     * parties are ours is there a genuine split to make.
     */
    private FeeAssessment allocate(
            BigDecimal total,
            Currency currency,
            ChargeBearer chargeBearer,
            PaymentDirection direction,
            FeeRule rule) {

        BigDecimal debtorFee;
        BigDecimal creditorFee;

        switch (chargeBearer) {

            case DEBT -> {
                debtorFee = total;
                creditorFee = BigDecimal.ZERO;
            }

            case CRED -> {
                debtorFee = BigDecimal.ZERO;
                creditorFee = total;
            }

            case SHAR, SLEV -> {

                if (direction == PaymentDirection.INTERNAL) {

                    /*
                     * We are both agents, so both halves are ours to collect.
                     * The debtor absorbs any odd cent.
                     */
                    creditorFee = total.divide(
                            BigDecimal.valueOf(2),
                            MONEY_SCALE,
                            RoundingMode.DOWN
                    );

                    debtorFee = total.subtract(creditorFee);

                } else if (direction == PaymentDirection.INBOUND) {

                    debtorFee = BigDecimal.ZERO;
                    creditorFee = total;

                } else {

                    debtorFee = total;
                    creditorFee = BigDecimal.ZERO;
                }
            }

            default -> throw new BusinessException(
                    "Unsupported charge bearer: " + chargeBearer
            );
        }

        return new FeeAssessment(
                total,
                debtorFee,
                creditorFee,
                currency,
                rule.getRuleCode(),
                rule.getDescription()
        );
    }
}
