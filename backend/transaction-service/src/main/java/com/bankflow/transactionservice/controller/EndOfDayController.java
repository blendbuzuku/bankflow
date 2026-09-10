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
     * The day meant when none is named: the one the bank is trading into.
     *
     * Not the date on the wall. The two agree until a day is signed off, and
     * then part company for as long as the bank stays behind the clock — so
     * defaulting to the calendar meant that the moment somebody closed a day,
     * every screen here went on showing it: proving a sealed day, summarising
     * a sealed day, and refusing to close it again because it was already
     * closed. The open day was reachable only by typing its date.
     */
    private LocalDate openDayOr(LocalDate date) {
        return date != null ? date : businessCalendar.today();
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
                reconciliationService.reconcile(openDayOr(date))
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
                endOfDayService.trialBalance(openDayOr(date))
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
                dayCloseService.close(openDayOr(date))
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
                daySummaryService.summarise(openDayOr(date))
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

        /*
         * Named, not just counted. "Two days behind" says there is a problem;
         * the dates say which days somebody has to prove and sign off, and in
         * what order.
         */
        result.put("overdueDays", businessCalendar.overdueDays());

        /*
         * The most recent sign-off, so a screen can say who last closed and
         * when without fetching the whole register to find out.
         */
        List<DayClose> closes = dayCloseService.history();

        if (!closes.isEmpty()) {

            DayClose last = closes.get(0);
            Map<String, Object> lastClosed = new LinkedHashMap<>();

            lastClosed.put("bookingDate", last.getBookingDate());
            lastClosed.put("closedByUsername", last.getClosedByUsername());
            lastClosed.put("closedAt", last.getClosedAt());

            result.put("lastClosed", lastClosed);

        } else {
            result.put("lastClosed", null);
        }

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
                endOfDayService.accountBreakdown(openDayOr(date), currency)
        );
    }
}
