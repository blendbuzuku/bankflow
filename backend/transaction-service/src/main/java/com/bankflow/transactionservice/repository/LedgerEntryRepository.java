package com.bankflow.transactionservice.repository;

import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.LedgerEntry;
import com.bankflow.transactionservice.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(
            Long transactionId
    );

    List<LedgerEntry> findByAccountId(
            Long accountId
    );

    List<LedgerEntry> findByAccountIdAndEntryType(
            Long accountId,
            LedgerEntryType entryType
    );

    boolean existsByEntryReference(
            String entryReference
    );

    List<LedgerEntry> findByBookingDate(LocalDate bookingDate);

    /**
     * Totals posted on a day, grouped by currency and side.
     *
     * Summed in the database rather than in Java: a day's entries can run to
     * millions, and the answer is three numbers per currency.
     *
     * @return rows of [currency, entryType, total]
     */
    @Query("""
            SELECT e.currency, e.entryType, SUM(e.amount), COUNT(e)
            FROM LedgerEntry e
            WHERE e.bookingDate = :bookingDate
            GROUP BY e.currency, e.entryType
            """)
    List<Object[]> sumByCurrencyAndType(
            @Param("bookingDate") LocalDate bookingDate
    );

    /**
     * Per-account movement for a day, used to explain a currency that does not
     * balance.
     *
     * @return rows of [accountId, entryType, total]
     */
    @Query("""
            SELECT e.accountId, e.entryType, SUM(e.amount)
            FROM LedgerEntry e
            WHERE e.bookingDate = :bookingDate
              AND e.currency = :currency
            GROUP BY e.accountId, e.entryType
            ORDER BY e.accountId
            """)
    List<Object[]> sumByAccount(
            @Param("bookingDate") LocalDate bookingDate,
            @Param("currency") Currency currency
    );
}