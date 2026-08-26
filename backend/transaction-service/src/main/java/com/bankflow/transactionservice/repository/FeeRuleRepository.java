package com.bankflow.transactionservice.repository;

import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.FeeRule;
import com.bankflow.transactionservice.entity.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeRuleRepository extends JpaRepository<FeeRule, Long> {

    Optional<FeeRule> findByRuleCode(String ruleCode);

    boolean existsByRuleCode(String ruleCode);

    /**
     * Candidate rules for a payment. Band and date filtering happens in the
     * service, where the amount and booking date are known.
     */
    List<FeeRule> findByPaymentTypeAndCurrencyAndActiveTrue(
            PaymentType paymentType,
            Currency currency
    );
}
