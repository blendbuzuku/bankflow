package com.bankflow.transactionservice.calendar;

import com.bankflow.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What day the bank thinks it is.
 *
 * Every booking date comes from here rather than from the clock. The two agree
 * most of the time, and the times they do not are exactly the ones that
 * matter: after a day is closed, the clock still says the same date while the
 * bank has moved on to the next.
 *
 * A payment made at six in the evening, after the books were signed off, is
 * tomorrow's business. Booking it to the calendar date would put it in a day
 * an auditor has already been told is complete.
 */
@Service
public class BusinessCalendar {

    private final BusinessDateRepository repository;

    public BusinessCalendar(BusinessDateRepository repository) {
        this.repository = repository;
    }

    /**
     * The day being traded into.
     *
     * Seeded from the clock the first time it is asked for, so a fresh
     * database starts on the day it was first used rather than on a date
     * somebody had to remember to set.
     */
    @Transactional
    public LocalDate today() {

        return repository.findById(BusinessDate.SINGLETON)
                .map(BusinessDate::getCurrentDate)
                .orElseGet(this::seed);
    }

    /**
     * Moves the bank on to the next day.
     *
     * Called once a day has been closed, and only then: rolling without
     * closing would leave a day nobody proved, and closing without rolling
     * would leave the bank unable to trade.
     *
     * A calendar day at a time. A real operator skips weekends and holidays
     * against a published calendar; this bank has none, so it does not pretend
     * to know which days those are.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public LocalDate rollForward(LocalDate closed, String by) {

        BusinessDate current = repository.findById(BusinessDate.SINGLETON)
                .orElseGet(() -> {
                    seed();
                    return repository.findById(BusinessDate.SINGLETON).orElseThrow();
                });

        if (!current.getCurrentDate().equals(closed)) {
            throw new BusinessException(
                    ("The bank is trading into %s, so %s is not the day to "
                            + "roll from.").formatted(
                            current.getCurrentDate(), closed)
            );
        }

        current.setCurrentDate(closed.plusDays(1));
        current.setRolledAt(LocalDateTime.now());
        current.setRolledByUsername(by);

        return repository.save(current).getCurrentDate();
    }

    /**
     * Whether the bank has fallen behind the clock.
     *
     * True when days have been closed faster than they have passed, or when
     * nobody has closed for a while. Worth surfacing rather than hiding: a
     * business date days behind the calendar means end of day is not being
     * run.
     */
    @Transactional
    public long daysBehindTheClock() {
        return java.time.temporal.ChronoUnit.DAYS.between(today(), LocalDate.now());
    }

    /**
     * The days that should already have been signed off and have not, oldest
     * first.
     *
     * From the open day up to, but not including, today: today is still
     * trading and closes tonight, so it is not overdue. Every day before the
     * open one is closed already — the business date only moves when a close
     * succeeds — so this is the whole of what is outstanding, and it can only
     * be worked through in order.
     */
    @Transactional
    public java.util.List<LocalDate> overdueDays() {

        LocalDate open = today();
        LocalDate clock = LocalDate.now();

        return open.isBefore(clock)
                ? open.datesUntil(clock).toList()
                : java.util.List.of();
    }

    private LocalDate seed() {

        BusinessDate seeded = new BusinessDate();

        seeded.setCurrentDate(LocalDate.now());

        return repository.save(seeded).getCurrentDate();
    }
}
