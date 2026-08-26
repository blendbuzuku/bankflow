package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.entity.AuditEventType;
import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.LedgerEntryType;
import com.bankflow.transactionservice.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves the books balance.
 *
 * The rule is absolute: at end of day, total debits minus total credits must be
 * exactly zero in every currency. Double-entry guarantees it per transaction —
 * {@code validateAccountingBalance} enforces that when a payment is booked — but
 * the day-level proof is what catches a leg that was written without its
 * counterpart, which is precisely the failure a per-transaction check cannot see.
 *
 * Currencies are proved separately. Summing EUR and USD together would produce
 * a number that nets to zero only by coincidence and means nothing.
 */
@Service
public class EndOfDayService {

    private static final Logger log =
            LoggerFactory.getLogger(EndOfDayService.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    public EndOfDayService(
            LedgerEntryRepository ledgerEntryRepository,
            AuditService auditService) {

        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TrialBalance trialBalance(LocalDate bookingDate) {

        Map<Currency, BigDecimal[]> totals = new EnumMap<>(Currency.class);
        Map<Currency, Long> counts = new EnumMap<>(Currency.class);

        for (Object[] row : ledgerEntryRepository.sumByCurrencyAndType(bookingDate)) {

            Currency currency = (Currency) row[0];
            LedgerEntryType type = (LedgerEntryType) row[1];
            BigDecimal total = (BigDecimal) row[2];

            BigDecimal[] sides = totals.computeIfAbsent(
                    currency,
                    key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}
            );

            if (type == LedgerEntryType.DEBIT) {
                sides[0] = sides[0].add(total);
            } else {
                sides[1] = sides[1].add(total);
            }

            counts.merge(currency, 1L, Long::sum);
        }

        List<TrialBalance.CurrencyBalance> lines = new ArrayList<>();
        boolean allBalanced = true;

        for (Map.Entry<Currency, BigDecimal[]> entry : totals.entrySet()) {

            BigDecimal debits = entry.getValue()[0];
            BigDecimal credits = entry.getValue()[1];
            BigDecimal net = debits.subtract(credits);

            /*
             * compareTo rather than equals: BigDecimal.equals distinguishes
             * 0.00 from 0, and here they mean the same thing.
             */
            boolean balanced = net.compareTo(BigDecimal.ZERO) == 0;

            allBalanced &= balanced;

            lines.add(new TrialBalance.CurrencyBalance(
                    entry.getKey(),
                    debits,
                    credits,
                    net,
                    counts.getOrDefault(entry.getKey(), 0L),
                    balanced
            ));
        }

        return new TrialBalance(bookingDate, lines, allBalanced);
    }

    /**
     * Runs the proof and records the outcome in the audit trail.
     *
     * The result is recorded whether or not it balances — an end-of-day check
     * that only leaves evidence when it passes is not a control.
     */
    @Transactional
    public TrialBalance closeDay(LocalDate bookingDate) {

        TrialBalance balance = trialBalance(bookingDate);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("bookingDate", bookingDate.toString());
        details.put("balanced", balance.balanced());

        details.put(
                "currencies",
                balance.currencies().stream()
                        .map(line -> Map.of(
                                "currency", line.currency().name(),
                                "debits", line.totalDebits(),
                                "credits", line.totalCredits(),
                                "net", line.net()
                        ))
                        .toList()
        );

        if (!balance.balanced()) {

            log.error("{}", balance.describe());

            details.put(
                    "offendingAccounts",
                    balance.discrepancies().stream()
                            .flatMap(line ->
                                    accountBreakdown(bookingDate, line.currency())
                                            .stream())
                            .toList()
            );
        }

        auditService.record(
                AuditEventType.LEDGER_ENTRY_POSTED,
                null,
                "TrialBalance",
                bookingDate.toString(),
                balance.describe(),
                details
        );

        return balance;
    }

    /**
     * Per-account totals for a currency, so an out-of-balance day can be traced
     * to the accounts responsible.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> accountBreakdown(
            LocalDate bookingDate,
            Currency currency) {

        Map<Long, BigDecimal[]> byAccount = new LinkedHashMap<>();

        for (Object[] row : ledgerEntryRepository.sumByAccount(bookingDate, currency)) {

            Long accountId = (Long) row[0];
            LedgerEntryType type = (LedgerEntryType) row[1];
            BigDecimal total = (BigDecimal) row[2];

            BigDecimal[] sides = byAccount.computeIfAbsent(
                    accountId,
                    key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}
            );

            if (type == LedgerEntryType.DEBIT) {
                sides[0] = sides[0].add(total);
            } else {
                sides[1] = sides[1].add(total);
            }
        }

        List<Map<String, Object>> breakdown = new ArrayList<>();

        byAccount.forEach((accountId, sides) -> {

            Map<String, Object> line = new LinkedHashMap<>();

            line.put("accountId", accountId);
            line.put("currency", currency.name());
            line.put("debits", sides[0]);
            line.put("credits", sides[1]);
            line.put("net", sides[0].subtract(sides[1]));

            breakdown.add(line);
        });

        return breakdown;
    }
}
