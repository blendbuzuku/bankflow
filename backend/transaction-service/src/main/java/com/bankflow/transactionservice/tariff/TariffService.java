package com.bankflow.transactionservice.tariff;

import com.bankflow.common.audit.AuditEventType;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.FeeRule;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.repository.FeeRuleRepository;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import com.bankflow.transactionservice.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains what the bank charges.
 *
 * The prices were already read from the table on every payment, so pricing was
 * never hard-coded -- but there was no way to change a line without a
 * migration, which meant in practice the tariff was as fixed as if it had
 * been.
 *
 * A tariff is versioned rather than edited. A payment made last Tuesday was
 * priced under last Tuesday's rule, and if amending a line rewrote it, that
 * payment's charge could no longer be explained from the tariff -- the
 * customer would be looking at a figure the bank could not derive. So an
 * amendment closes the current line and opens its successor, and both stay on
 * the record.
 */
@Service
public class TariffService {

    private final FeeRuleRepository feeRuleRepository;
    private final BusinessCalendar businessCalendar;
    private final AuditService auditService;

    public TariffService(
            FeeRuleRepository feeRuleRepository,
            BusinessCalendar businessCalendar,
            AuditService auditService) {

        this.feeRuleRepository = feeRuleRepository;
        this.businessCalendar = businessCalendar;
        this.auditService = auditService;
    }

    /**
     * Every line, current and retired, newest band first.
     *
     * Retired lines are shown rather than hidden: they are what explains the
     * charge on an older payment, and a tariff screen that only shows today's
     * prices cannot answer "why was I charged that".
     */
    @Transactional(readOnly = true)
    public List<FeeRule> all() {

        return feeRuleRepository.findAll()
                .stream()
                .sorted(Comparator
                        .comparing(FeeRule::getPaymentType)
                        .thenComparing(FeeRule::getCurrency)
                        .thenComparing(FeeRule::getMinAmount))
                .toList();
    }

    /** Adds a line to the tariff. */
    @Transactional
    public FeeRule create(TariffRuleRequest request) {

        validate(request);

        feeRuleRepository.findByRuleCode(request.ruleCode().trim())
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "A tariff line with the code %s already exists"
                                    .formatted(request.ruleCode().trim())
                    );
                });

        FeeRule rule = apply(new FeeRule(), request);

        FeeRule saved = feeRuleRepository.save(rule);

        record(
                AuditEventType.TARIFF_RULE_CREATED,
                saved,
                "Tariff line %s added: %s".formatted(
                        saved.getRuleCode(), saved.getDescription()),
                null
        );

        return saved;
    }

    /**
     * Changes a price by superseding the line rather than rewriting it.
     *
     * The existing line is closed the day before the new one takes effect, so
     * the two never both apply and every past payment can still be priced from
     * the tariff as it stood.
     */
    @Transactional
    public FeeRule amend(Long id, TariffRuleRequest request) {

        FeeRule current = find(id);

        validate(request);

        LocalDate effective = request.validFrom();

        if (effective == null) {
            throw new BusinessException(
                    "Say when the new price takes effect"
            );
        }

        if (!effective.isAfter(current.getValidFrom())) {
            throw new BusinessException(
                    ("The new price would start on %s, which is not after the "
                            + "line it replaces began on %s. Two prices cannot "
                            + "apply to the same day.")
                            .formatted(effective, current.getValidFrom())
            );
        }

        if (effective.isBefore(businessCalendar.today())) {
            throw new BusinessException(
                    ("A price cannot start on %s, which is before the current "
                            + "business date of %s. Payments already booked "
                            + "were priced under the old line and repricing "
                            + "them is not something a tariff change can do.")
                            .formatted(effective, businessCalendar.today())
            );
        }

        Map<String, Object> before = describe(current);

        /*
         * Closed by date, not deactivated.
         *
         * The validity window already stops it applying once the successor
         * takes over, and covers() checks that window. Clearing the active
         * flag as well would take the line out of pricing the moment the
         * change was made -- so a payment later the same day, still governed
         * by the old price, would find no rule at all and be charged nothing.
         */
        current.setValidTo(effective.minusDays(1));
        feeRuleRepository.save(current);

        FeeRule successor = apply(new FeeRule(), request);

        /*
         * The successor needs its own code, because the code is unique and the
         * old line keeps its own. Suffixed with the date it takes effect so
         * the lineage is readable rather than a number nobody can place.
         */
        successor.setRuleCode(
                "%s-%s".formatted(
                        stripSuffix(current.getRuleCode()),
                        effective.toString().replace("-", "")
                )
        );

        FeeRule saved = feeRuleRepository.save(successor);

        record(
                AuditEventType.TARIFF_RULE_CHANGED,
                saved,
                "Tariff line %s superseded by %s from %s".formatted(
                        current.getRuleCode(), saved.getRuleCode(), effective),
                before
        );

        return saved;
    }

    /** Stops a line applying, without removing what it explains. */
    @Transactional
    public FeeRule retire(Long id) {

        FeeRule rule = find(id);

        if (!rule.isActive()) {
            throw new BusinessException(
                    "%s is already retired".formatted(rule.getRuleCode())
            );
        }

        Map<String, Object> before = describe(rule);

        rule.setActive(false);
        rule.setValidTo(businessCalendar.today());

        FeeRule saved = feeRuleRepository.save(rule);

        record(
                AuditEventType.TARIFF_RULE_CHANGED,
                saved,
                "Tariff line %s retired; it no longer prices new payments"
                        .formatted(saved.getRuleCode()),
                before
        );

        return saved;
    }

    @Transactional
    public FeeRule reinstate(Long id) {

        FeeRule rule = find(id);

        if (rule.isActive()) {
            throw new BusinessException(
                    "%s is already in use".formatted(rule.getRuleCode())
            );
        }

        Map<String, Object> before = describe(rule);

        rule.setActive(true);
        rule.setValidTo(null);

        FeeRule saved = feeRuleRepository.save(rule);

        record(
                AuditEventType.TARIFF_RULE_CHANGED,
                saved,
                "Tariff line %s put back in use".formatted(saved.getRuleCode()),
                before
        );

        return saved;
    }

    // --- internals -----------------------------------------------------

    private FeeRule find(Long id) {

        return feeRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "No tariff line with id " + id
                ));
    }

    private FeeRule apply(FeeRule rule, TariffRuleRequest request) {

        rule.setRuleCode(request.ruleCode().trim().toUpperCase());
        rule.setDescription(request.description().trim());
        rule.setPaymentType(request.paymentType());
        rule.setCurrency(request.currency());
        rule.setMinAmount(request.minAmount());
        rule.setMaxAmount(request.maxAmount());
        rule.setFixedFee(orZero(request.fixedFee()));
        rule.setPercentageRate(orZero(request.percentageRate()));
        rule.setMinFee(request.minFee());
        rule.setMaxFee(request.maxFee());
        rule.setValidFrom(
                request.validFrom() != null
                        ? request.validFrom()
                        : businessCalendar.today()
        );
        rule.setValidTo(request.validTo());
        rule.setActive(true);

        return rule;
    }

    /**
     * A price that could not be charged is worse than no price at all.
     *
     * Every one of these produces a figure somebody would have to explain to a
     * customer: a band that ends before it starts prices nothing, a floor above
     * a ceiling makes the two contradict, and a negative charge pays people to
     * make payments.
     */
    private void validate(TariffRuleRequest request) {

        if (request.ruleCode() == null || request.ruleCode().isBlank()) {
            throw new BusinessException("A tariff line needs a code");
        }

        if (request.description() == null || request.description().isBlank()) {
            throw new BusinessException(
                    "Describe what this line charges for. It is what appears "
                            + "when somebody asks why they were charged"
            );
        }

        if (request.paymentType() == null || request.currency() == null) {
            throw new BusinessException(
                    "A tariff line applies to one rail and one currency"
            );
        }

        if (request.minAmount() == null || request.minAmount().signum() < 0) {
            throw new BusinessException(
                    "The band must start at zero or above"
            );
        }

        if (request.maxAmount() != null
                && request.maxAmount().compareTo(request.minAmount()) <= 0) {

            throw new BusinessException(
                    ("The band runs from %s to %s, which covers no amount at "
                            + "all. Leave the upper limit empty for an "
                            + "open-ended band.").formatted(
                            request.minAmount().toPlainString(),
                            request.maxAmount().toPlainString())
            );
        }

        checkNotNegative(request.fixedFee(), "A fixed charge");
        checkNotNegative(request.percentageRate(), "A percentage rate");
        checkNotNegative(request.minFee(), "A minimum charge");
        checkNotNegative(request.maxFee(), "A maximum charge");

        if (request.minFee() != null && request.maxFee() != null
                && request.minFee().compareTo(request.maxFee()) > 0) {

            throw new BusinessException(
                    ("The minimum charge of %s is above the maximum of %s, so "
                            + "the two contradict each other.").formatted(
                            request.minFee().toPlainString(),
                            request.maxFee().toPlainString())
            );
        }

        boolean charges = orZero(request.fixedFee()).signum() > 0
                || orZero(request.percentageRate()).signum() > 0;

        if (!charges && orZero(request.minFee()).signum() > 0) {
            throw new BusinessException(
                    "This line charges nothing but sets a minimum, so every "
                            + "payment would be charged the minimum. Set a "
                            + "fixed charge instead if that is the intent"
            );
        }

        if (request.validTo() != null && request.validFrom() != null
                && request.validTo().isBefore(request.validFrom())) {

            throw new BusinessException(
                    "The line would stop applying before it started"
            );
        }
    }

    private void checkNotNegative(BigDecimal value, String label) {

        if (value != null && value.signum() < 0) {
            throw new BusinessException(
                    label + " cannot be negative. A bank does not pay people "
                            + "to make payments"
            );
        }
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Drops a previously appended effective date, so codes do not accrete. */
    private String stripSuffix(String code) {
        return code.replaceFirst("-\\d{8}$", "");
    }

    private Map<String, Object> describe(FeeRule rule) {

        Map<String, Object> state = new LinkedHashMap<>();

        state.put("ruleCode", rule.getRuleCode());
        state.put("paymentType", rule.getPaymentType());
        state.put("currency", rule.getCurrency());
        state.put("minAmount", rule.getMinAmount());
        state.put("maxAmount", rule.getMaxAmount());
        state.put("fixedFee", rule.getFixedFee());
        state.put("percentageRate", rule.getPercentageRate());
        state.put("minFee", rule.getMinFee());
        state.put("maxFee", rule.getMaxFee());
        state.put("validFrom", rule.getValidFrom());
        state.put("validTo", rule.getValidTo());
        state.put("active", rule.isActive());

        return state;
    }

    /**
     * A price change is somebody's decision and gets their name against it.
     *
     * The state before the change is recorded alongside, because the question
     * afterwards is never "what does it charge now" -- that is on the screen --
     * but "what did it charge before, and who moved it".
     */
    private void record(
            AuditEventType type,
            FeeRule rule,
            String summary,
            Map<String, Object> before) {

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("changedBy", actor == null ? null : actor.username());
        details.put("after", describe(rule));

        if (before != null) {
            details.put("before", before);
        }

        auditService.record(
                type,
                null,
                "FeeRule",
                String.valueOf(rule.getId()),
                summary,
                details
        );
    }
}
