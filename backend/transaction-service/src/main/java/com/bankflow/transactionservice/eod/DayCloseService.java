package com.bankflow.transactionservice.eod;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import com.bankflow.transactionservice.service.AuditService;
import com.bankflow.transactionservice.service.EndOfDayService;
import com.bankflow.transactionservice.service.Reconciliation;
import com.bankflow.transactionservice.service.ReconciliationService;
import com.bankflow.transactionservice.service.TrialBalance;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Signing a day off, and keeping it signed off.
 *
 * A day closes only when both proofs pass: the trial balance shows the ledger
 * is internally consistent, and reconciliation shows it matches what actually
 * happened to the balances. Either one alone can be satisfied by a broken
 * book, which is why neither is enough.
 *
 * A close is recorded rather than recomputed. The figures kept here are the
 * ones the person accepted at the time, and they stay true even if the ledger
 * moves afterwards — though the seal is there to make sure it does not.
 */
@Service
public class DayCloseService {

    private static final int HISTORY = 14;

    private final DayCloseRepository repository;
    private final EndOfDayService endOfDay;
    private final ReconciliationService reconciliationService;
    private final AuditService auditService;
    private final BusinessCalendar businessCalendar;

    public DayCloseService(
            DayCloseRepository repository,
            EndOfDayService endOfDay,
            ReconciliationService reconciliationService,
            AuditService auditService,
            BusinessCalendar businessCalendar) {

        this.businessCalendar = businessCalendar;
        this.repository = repository;
        this.endOfDay = endOfDay;
        this.reconciliationService = reconciliationService;
        this.auditService = auditService;
    }

    /**
     * Closes a day, if it proves.
     *
     * A day that does not prove is not closed and not recorded — there is
     * nothing to sign off, and writing a failed close would suggest the books
     * had been accepted when they were refused.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public DayCloseResult close(LocalDate bookingDate) {

        /*
         * Only the open day can be closed. Any other date is either already
         * signed off or has not been traded into yet, and closing it would
         * seal a day the bank never opened.
         */
        LocalDate open = businessCalendar.today();

        if (!open.equals(bookingDate)) {
            throw new BusinessException(
                    ("The bank is trading into %s. Only the open day can be "
                            + "closed.").formatted(open)
            );
        }

        repository.findByBookingDate(bookingDate).ifPresent(already -> {
            throw new BusinessException(
                    "%s was already closed by %s on %s".formatted(
                            bookingDate,
                            already.getClosedByUsername(),
                            already.getClosedAt().toLocalDate()
                    )
            );
        });

        TrialBalance balance = endOfDay.trialBalance(bookingDate);
        Reconciliation reconciliation = reconciliationService.reconcile(bookingDate);

        boolean proves = balance.balanced() && reconciliation.reconciled();

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();

        String summary = describe(bookingDate, balance, reconciliation, proves);

        if (!proves) {

            /*
             * Recorded on the audit trail but not in the register: an attempt
             * to close that failed is worth knowing about, and is not a close.
             */
            auditService.record(
                    AuditEventType.DAY_CLOSE_REFUSED,
                    null,
                    "DayClose",
                    bookingDate.toString(),
                    summary + " (attempted by " + actor.username() + ")",
                    details(bookingDate, balance, reconciliation, false)
            );

            return new DayCloseResult(
                    bookingDate, false, balance, reconciliation, summary,
                    null, open
            );
        }

        DayClose close = new DayClose();

        close.setBookingDate(bookingDate);
        close.setClosedByUsername(actor.username());
        close.setClosedAt(LocalDateTime.now());
        close.setBalanced(true);
        close.setReconciled(true);
        close.setEntryCount(totalEntries(balance));
        close.setTotalDebits(total(balance, true));
        close.setTotalCredits(total(balance, false));
        close.setMatchedMovements(reconciliation.matched());
        close.setSummary(summary);

        DayClose saved = repository.save(close);

        /*
         * The bank moves on. Closing does not stop business — work done after
         * the sign-off is the next day's, and books there.
         */
        LocalDate next = businessCalendar.rollForward(bookingDate, actor.username());

        auditService.record(
                AuditEventType.DAY_CLOSED,
                null,
                "DayClose",
                bookingDate.toString(),
                "%s closed by %s; now trading into %s. %s".formatted(
                        bookingDate, actor.username(), next, summary),
                details(bookingDate, balance, reconciliation, true)
        );

        return new DayCloseResult(
                bookingDate, true, balance, reconciliation, summary, saved, next
        );
    }

    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public Optional<DayClose> findClose(LocalDate bookingDate) {
        return repository.findByBookingDate(bookingDate);
    }

    /** The recent run of days, newest first. */
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public List<DayClose> history() {
        return repository.findAllByOrderByBookingDateDesc(
                PageRequest.of(0, HISTORY)
        );
    }

    private String describe(
            LocalDate bookingDate,
            TrialBalance balance,
            Reconciliation reconciliation,
            boolean proves) {

        if (proves) {
            return ("Trial balance nets to zero across %d %s and reconciliation "
                    + "matched %d movements with no breaks.").formatted(
                    balance.currencies().size(),
                    balance.currencies().size() == 1 ? "currency" : "currencies",
                    reconciliation.matched()
            );
        }

        if (!balance.balanced() && !reconciliation.reconciled()) {
            return "The trial balance does not net to zero and reconciliation "
                    + "found " + reconciliation.breakCount() + " breaks.";
        }

        if (!balance.balanced()) {
            return "The trial balance does not net to zero.";
        }

        return "Reconciliation found %d %s.".formatted(
                reconciliation.breakCount(),
                reconciliation.breakCount() == 1 ? "break" : "breaks"
        );
    }

    private Map<String, Object> details(
            LocalDate bookingDate,
            TrialBalance balance,
            Reconciliation reconciliation,
            boolean closed) {

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("bookingDate", bookingDate.toString());
        details.put("closed", closed);
        details.put("balanced", balance.balanced());
        details.put("reconciled", reconciliation.reconciled());
        details.put("breaks", reconciliation.breakCount());
        details.put("matched", reconciliation.matched());
        details.put("entries", totalEntries(balance));

        return details;
    }

    private long totalEntries(TrialBalance balance) {
        return balance.currencies().stream()
                .mapToLong(TrialBalance.CurrencyBalance::entryCount)
                .sum();
    }

    private BigDecimal total(TrialBalance balance, boolean debits) {

        return balance.currencies().stream()
                .map(line -> debits ? line.totalDebits() : line.totalCredits())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What the close did, whether or not it sealed the day. */
    public record DayCloseResult(
            LocalDate bookingDate,
            boolean closed,
            TrialBalance trialBalance,
            Reconciliation reconciliation,
            String summary,
            DayClose record,

            /** The day the bank is trading into now. */
            LocalDate tradingInto) {
    }
}
