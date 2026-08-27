package com.bankflow.transactionservice.repository;

import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    boolean existsByEndToEndId(String endToEndId);

    Optional<Transaction> findByTransactionReference(
            String transactionReference
    );

    /**
     * The end-to-end reference is how a counterparty identifies a payment in a
     * status report, so this is the lookup an inbound pacs.002 uses.
     */
    Optional<Transaction> findByEndToEndId(String endToEndId);

    Optional<Transaction> findByUetr(String uetr);

    /** The approval queue, oldest first so nothing waits indefinitely. */
    List<Transaction> findByStatusOrderByCreatedAtAsc(TransactionStatus status);

    /** Everything booked on one day, for the end-of-day summary. */
    List<Transaction> findByBookingDate(java.time.LocalDate bookingDate);

    /**
     * Money that has left the debtor but not yet been answered for.
     *
     * These are the payments sitting in suspense: sent to the scheme, awaiting
     * a status report. They are the difference between the bank's books and
     * its position with KIPS, so they are what somebody closing the day needs
     * to see.
     */
    List<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status);


    /**
     * Payments touching any of a set of accounts, either side.
     *
     * A customer's history is everything that moved their money, whether they
     * sent it, received it, or a teller acted for them.
     */
    List<Transaction>
    findBySourceAccountIdInOrDestinationAccountIdInOrderByCreatedAtDesc(
            Collection<Long> sourceAccountIds,
            Collection<Long> destinationAccountIds
    );
}