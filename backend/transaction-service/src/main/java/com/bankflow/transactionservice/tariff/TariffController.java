package com.bankflow.transactionservice.tariff;

import com.bankflow.transactionservice.entity.FeeRule;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * What the bank charges, and who changed it.
 *
 * Guarded like the bank directory, and for the same reason: both decide
 * something about a customer's money without any payment being made, so
 * neither belongs to a teller.
 */
@RestController
@RequestMapping("/api/tariff")
@PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
public class TariffController {

    private final TariffService tariffService;

    public TariffController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    @GetMapping
    public ResponseEntity<List<TariffRuleResponse>> all() {

        return ResponseEntity.ok(
                tariffService.all().stream().map(TariffRuleResponse::from).toList()
        );
    }

    @PostMapping
    public ResponseEntity<TariffRuleResponse> create(
            @RequestBody TariffRuleRequest request) {

        return ResponseEntity.ok(
                TariffRuleResponse.from(tariffService.create(request))
        );
    }

    /** Supersedes a line from a given date rather than rewriting it. */
    @PutMapping("/{id}")
    public ResponseEntity<TariffRuleResponse> amend(
            @PathVariable Long id,
            @RequestBody TariffRuleRequest request) {

        return ResponseEntity.ok(
                TariffRuleResponse.from(tariffService.amend(id, request))
        );
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<TariffRuleResponse> retire(@PathVariable Long id) {
        return ResponseEntity.ok(
                TariffRuleResponse.from(tariffService.retire(id))
        );
    }

    @PostMapping("/{id}/reinstate")
    public ResponseEntity<TariffRuleResponse> reinstate(@PathVariable Long id) {
        return ResponseEntity.ok(
                TariffRuleResponse.from(tariffService.reinstate(id))
        );
    }

    /** One tariff line as it is shown back. */
    public record TariffRuleResponse(
            Long id,
            String ruleCode,
            String description,
            String paymentType,
            String paymentTypeName,
            String currency,
            java.math.BigDecimal minAmount,
            java.math.BigDecimal maxAmount,
            java.math.BigDecimal fixedFee,
            java.math.BigDecimal percentageRate,
            java.math.BigDecimal minFee,
            java.math.BigDecimal maxFee,
            boolean active,
            java.time.LocalDate validFrom,
            java.time.LocalDate validTo
    ) {

        public static TariffRuleResponse from(FeeRule rule) {

            return new TariffRuleResponse(
                    rule.getId(),
                    rule.getRuleCode(),
                    rule.getDescription(),
                    rule.getPaymentType().name(),
                    rule.getPaymentType().getDisplayName(),
                    rule.getCurrency().name(),
                    rule.getMinAmount(),
                    rule.getMaxAmount(),
                    rule.getFixedFee(),
                    rule.getPercentageRate(),
                    rule.getMinFee(),
                    rule.getMaxFee(),
                    rule.isActive(),
                    rule.getValidFrom(),
                    rule.getValidTo()
            );
        }
    }
}
