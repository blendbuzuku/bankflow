package com.bankflow.transactionservice.eod;

import com.bankflow.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Keeps closed days closed.
 *
 * Once a day has been signed off, its figures are a statement somebody made
 * and an auditor may rely on. Booking into it afterwards would silently make
 * that statement false — the trial balance printed on the day would no longer
 * match the ledger behind it, and nothing would say so.
 *
 * So a posting whose booking date falls in a closed period is refused, and the
 * correct handling is the one a bank actually uses: book it today, and if it
 * belongs to the closed day in substance, say so in the narrative. Value dates
 * may look backwards; booking dates may not.
 */
@Component
public class ClosedPeriodGuard {

    private final DayCloseRepository dayCloses;

    public ClosedPeriodGuard(DayCloseRepository dayCloses) {
        this.dayCloses = dayCloses;
    }

    /**
     * @throws BusinessException when the date has already been closed
     */
    @Transactional(readOnly = true)
    public void requireOpen(LocalDate bookingDate) {

        if (bookingDate == null) {
            return;
        }

        dayCloses.findByBookingDate(bookingDate).ifPresent(closed -> {
            throw new BusinessException(
                    ("%s was closed by %s and cannot take new entries. Book it "
                            + "today instead.").formatted(
                            bookingDate,
                            closed.getClosedByUsername()
                    )
            );
        });
    }

    @Transactional(readOnly = true)
    public boolean isClosed(LocalDate bookingDate) {
        return bookingDate != null && dayCloses.existsByBookingDate(bookingDate);
    }
}
