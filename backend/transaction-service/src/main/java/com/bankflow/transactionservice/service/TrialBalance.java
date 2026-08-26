package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.entity.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The end-of-day proof: for a given booking date, every currency must have
 * total debits equal to total credits, so the net is exactly zero.
 *
 * A non-zero net means value was created or destroyed inside the bank, which is
 * never a rounding curiosity to be tolerated — it is a defect, and the offending
 * accounts are listed so it can be traced.
 *
 * @param bookingDate the day being proved
 * @param currencies  one line per currency, each of which must net to zero
 * @param balanced    true only when every currency nets to zero
 */
public record TrialBalance(
        LocalDate bookingDate,
        List<CurrencyBalance> currencies,
        boolean balanced
) {

    /**
     * @param net totalDebits minus totalCredits; must be zero
     */
    public record CurrencyBalance(
            Currency currency,
            BigDecimal totalDebits,
            BigDecimal totalCredits,
            BigDecimal net,
            long entryCount,
            boolean balanced
    ) {
    }

    /**
     * Currencies that failed to balance, for reporting and alerting.
     */
    public List<CurrencyBalance> discrepancies() {

        return currencies.stream()
                .filter(line -> !line.balanced())
                .toList();
    }

    public String describe() {

        if (balanced) {
            return "End of day %s balanced across %d currency line(s)".formatted(
                    bookingDate,
                    currencies.size()
            );
        }

        return "End of day %s DOES NOT BALANCE: %s".formatted(
                bookingDate,
                discrepancies().stream()
                        .map(line -> "%s net %s".formatted(
                                line.currency(),
                                line.net()
                        ))
                        .toList()
        );
    }
}
