package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.service.EndOfDayService;
import com.bankflow.transactionservice.service.Reconciliation;
import com.bankflow.transactionservice.service.ReconciliationService;
import com.bankflow.transactionservice.service.TrialBalance;
import org.springframework.format.annotation.DateTimeFormat;
import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.transactionservice.eod.DayClose;
import com.bankflow.transactionservice.eod.DayCloseService;
import com.bankflow.transactionservice.eod.DaySummaryService;
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

    private final BusinessCalendar businessCalendar;
    private final DayCloseService dayCloseService;
    private final DaySummaryService daySummaryService;

    public EndOfDayController(
            EndOfDayService endOfDayService,
            ReconciliationService reconciliationService,
            DayCloseService dayCloseService,
            DaySummaryService daySummaryService,
            BusinessCalendar businessCalendar) {

        this.businessCalendar = businessCalendar;

        this.endOfDayService = endOfDayService;
        this.reconciliationService = reconciliationService;
        this.dayCloseService = dayCloseService;
        this.daySummaryService = daySummaryService;
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
    /**
     * Signs the day off, if it proves.
     *
     * A day that does not prove is not closed and nothing is recorded in the
     * register — writing a failed close would suggest the books had been
     * accepted when they were refused. The attempt is on the audit trail
     * either way.
     */
    @PostMapping("/close")
    public ResponseEntity<DayCloseService.DayCloseResult> close(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return ResponseEntity.ok(
                dayCloseService.close(date != null ? date : LocalDate.now())
        );
    }

    /** What the day consisted of, and what is still open. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public ResponseEntity<DaySummaryService.DaySummary> summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return ResponseEntity.ok(
                daySummaryService.summarise(date != null ? date : LocalDate.now())
        );
    }

    /** Which day the bank is trading into. */
    @GetMapping("/business-date")
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public ResponseEntity<Map<String, Object>> businessDate() {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("tradingInto", businessCalendar.today());
        result.put("calendarDate", LocalDate.now());
        result.put("daysBehind", businessCalendar.daysBehindTheClock());

        return ResponseEntity.ok(result);
    }

    /** The recent run of days and whether each one closed. */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public ResponseEntity<List<DayClose>> history() {
        return ResponseEntity.ok(dayCloseService.history());
    }

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
