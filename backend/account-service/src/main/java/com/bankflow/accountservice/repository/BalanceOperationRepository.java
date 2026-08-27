package com.bankflow.accountservice.repository;

import com.bankflow.accountservice.entity.BalanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BalanceOperationRepository
        extends JpaRepository<BalanceOperation, Long> {

    Optional<BalanceOperation> findByOperationId(
            String operationId
    );

    boolean existsByOperationId(
            String operationId
    );

    /**
     * Everything that moved in a window, for reconciliation against the callers'
     * own records.
     */
    /**
     * Every movement belonging to one business day.
     *
     * By booking date, not by the clock: after a close the two diverge, and
     * matching on the timestamp would pair a movement against a ledger entry
     * filed under a different day.
     */
    List<BalanceOperation> findByBookingDate(java.time.LocalDate bookingDate);

    List<BalanceOperation> findByCreatedAtBetween(
            LocalDateTime from,
            LocalDateTime to
    );

    /**
     * Balance implied by an account's movement history.
     *
     * Accounts open at zero and every subsequent change is recorded here, so
     * this must equal the stored balance. A difference means a balance was
     * changed by something other than a balance operation.
     */
    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN o.operation = com.bankflow.accountservice.entity.BalanceOperationType.CREDIT
                     THEN o.amount ELSE -o.amount END), 0)
            FROM BalanceOperation o
            WHERE o.accountId = :accountId
            """)
    BigDecimal sumMovementsFor(@Param("accountId") Long accountId);
}