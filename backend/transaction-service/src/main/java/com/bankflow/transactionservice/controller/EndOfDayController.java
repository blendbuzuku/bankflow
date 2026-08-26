package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.service.EndOfDayService;
import com.bankflow.transactionservice.service.Reconciliation;
import com.bankflow.transactionservice.service.ReconciliationService;
import com.bankflow.transactionservice.service.TrialBalance;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * End-of-day balancing.
 *
 * Restricted to operations and administrators: this is a control function, not
 * something a teller performs.
 */
@RestController
@RequestMapping("/api/end-of-day")
@PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
public class EndOfDayController {

    private final EndOfDayService endOfDayService;
    private final ReconciliationService reconciliationService;

    public EndOfDayController(
            EndOfDayService endOfDayService,
            ReconciliationService reconciliationService) {

        this.endOfDayService = endOfDayService;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Checks the ledger against what account-service says actually moved.
     */
    @GetMapping("/reconciliation")
    public ResponseEntity<Reconciliation> reconciliation(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return ResponseEntity.ok(
                reconciliationService.reconcile(
                        date != null ? date : LocalDate.now()
                )
        );
    }

    /**
     * The trial balance for a day. Read-only, so it can be checked at any time
     * without recording a close.
     */
    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalance> trialBalance(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return ResponseEntity.ok(
                endOfDayService.trialBalance(
                        date != null ? date : LocalDate.now()
                )
        );
    }

    /**
     * Closes the day: proves the ledger balances and that it matches what
     * actually moved, recording both outcomes.
     *
     * A day is only closed when both pass. The trial balance alone is not
     * enough — it cannot see a movement that never reached the ledger.
     */
    @PostMapping("/close")
    public ResponseEntity<Map<String, Object>> close(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        LocalDate day = date != null ? date : LocalDate.now();

        TrialBalance balance = endOfDayService.closeDay(day);
        Reconciliation reconciliation = reconciliationService.runAndRecord(day);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("bookingDate", day.toString());
        result.put("closed", balance.balanced() && reconciliation.reconciled());
        result.put("trialBalance", balance);
        result.put("reconciliation", reconciliation);

        return ResponseEntity.ok(result);
    }

    /**
     * Per-account totals, for tracing a currency that does not balance.
     */
    @GetMapping("/breakdown")
    public ResponseEntity<List<Map<String, Object>>> breakdown(
            @RequestParam Currency currency,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return ResponseEntity.ok(
                endOfDayService.accountBreakdown(
                        date != null ? date : LocalDate.now(),
                        currency
                )
        );
    }
}
