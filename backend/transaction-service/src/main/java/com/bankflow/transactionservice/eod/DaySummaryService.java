package com.bankflow.transactionservice.eod;

import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.transactionservice.entity.*;
import com.bankflow.transactionservice.recall.RecallRequestRepository;
import com.bankflow.transactionservice.recall.RecallStatus;
import com.bankflow.transactionservice.repository.TransactionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * What the day actually consisted of, and what is still open.
 *
 * The two proofs say whether the books add up. They say nothing about what the
 * bank did, so a clean day is a blank screen and a broken one gives no hint
 * where to look. This is the other half: the traffic that produced those
 * figures, and the things somebody should deal with before signing the day
 * off.
 */
@Service
public class DaySummaryService {

    private final TransactionRepository transactions;
    private final RecallRequestRepository recalls;
    private final DayCloseRepository dayCloses;
    private final BusinessCalendar businessCalendar;

    public DaySummaryService(
            TransactionRepository transactions,
            RecallRequestRepository recalls,
            DayCloseRepository dayCloses,
            BusinessCalendar businessCalendar) {

        this.businessCalendar = businessCalendar;
        this.transactions = transactions;
        this.recalls = recalls;
        this.dayCloses = dayCloses;
    }

    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    @Transactional(readOnly = true)
    public DaySummary summarise(LocalDate bookingDate) {

        List<Transaction> booked = transactions.findByBookingDate(bookingDate);

        return new DaySummary(
                bookingDate,
                businessCalendar.today(),
                dayCloses.existsByBookingDate(bookingDate),
                activity(booked),
                inFlight(bookingDate),
                outstanding()
        );
    }

    /** What moved, counted and totalled by the kind of thing it was. */
    private List<Line> activity(List<Transaction> booked) {

        List<Line> lines = new ArrayList<>();

        lines.add(line("Payments out", booked, t ->
                t.getDirection() == PaymentDirection.OUTBOUND
                        && t.getTransactionType() == TransactionType.TRANSFER));

        lines.add(line("Payments in", booked, t ->
                t.getDirection() == PaymentDirection.INBOUND
                        && t.getTransactionType() == TransactionType.TRANSFER));

        lines.add(line("On-us transfers", booked, t ->
                t.getDirection() == PaymentDirection.INTERNAL));

        lines.add(line("Cash in", booked, t ->
                t.getTransactionType() == TransactionType.DEPOSIT));

        lines.add(line("Cash out", booked, t ->
                t.getTransactionType() == TransactionType.WITHDRAWAL));

        lines.add(line("Returned", booked, t ->
                t.getStatus() == TransactionStatus.RETURNED));

        lines.add(line("Rejected", booked, t ->
                t.getStatus() == TransactionStatus.REJECTED));

        return lines.stream().filter(line -> line.count() > 0).toList();
    }

    private Line line(
            String label,
            List<Transaction> booked,
            java.util.function.Predicate<Transaction> matches) {

        List<Transaction> hits = booked.stream().filter(matches).toList();

        BigDecimal total = hits.stream()
                .map(Transaction::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fees = hits.stream()
                .map(Transaction::getDebtorFeeAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Line(label, hits.size(), total, fees);
    }

    /**
     * Money gone from the debtor and not yet answered for.
     *
     * A payment at SENT has been debited and is sitting in suspense until the
     * scheme replies. It is not a break — it is the bank's open position — but
     * an old one usually means a status report that never arrived.
     */
    private List<InFlight> inFlight(LocalDate asAt) {

        return transactions
                .findByStatusOrderByCreatedAtDesc(TransactionStatus.SENT)
                .stream()
                .map(t -> new InFlight(
                        t.getTransactionReference(),
                        t.getAmount(),
                        t.getCurrency().name(),
                        t.getCreditorName(),
                        t.getBookingDate(),
                        t.getBookingDate() == null
                                ? 0
                                : ChronoUnit.DAYS.between(t.getBookingDate(), asAt)
                ))
                .toList();
    }

    /**
     * Things a person should deal with before closing.
     *
     * Not blockers — a day can close with a payment parked, because the
     * payment has not moved any money. They are listed so the decision to
     * close is made knowing what is still open rather than in ignorance of it.
     */
    private List<Outstanding> outstanding() {

        List<Outstanding> items = new ArrayList<>();

        int parked = transactions
                .findByStatusOrderByCreatedAtAsc(TransactionStatus.PENDING_APPROVAL)
                .size();

        if (parked > 0) {
            items.add(new Outstanding(
                    "approvals",
                    parked,
                    parked == 1
                            ? "1 payment is waiting for a second approver"
                            : parked + " payments are waiting for a second approver",
                    "/approvals"
            ));
        }

        int openRecalls = recalls
                .findByStatusOrderByCreatedAtAsc(RecallStatus.REQUESTED)
                .size();

        if (openRecalls > 0) {
            items.add(new Outstanding(
                    "recalls",
                    openRecalls,
                    openRecalls == 1
                            ? "1 recall has not been answered"
                            : openRecalls + " recalls have not been answered",
                    "/recalls"
            ));
        }

        return items;
    }

    // --- shapes ---

    public record DaySummary(
            LocalDate bookingDate,

            /** The day the bank is trading into, which may not be this one. */
            LocalDate tradingInto,

            boolean closed,
            List<Line> activity,
            List<InFlight> inFlight,
            List<Outstanding> outstanding) {

        public BigDecimal inFlightTotal() {
            return inFlight.stream()
                    .map(InFlight::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record Line(
            String label,
            int count,
            BigDecimal total,
            BigDecimal fees) {
    }

    public record InFlight(
            String transactionReference,
            BigDecimal amount,
            String currency,
            String beneficiary,
            LocalDate bookingDate,
            long ageInDays) {
    }

    public record Outstanding(
            String kind,
            int count,
            String description,
            String where) {
    }
}
