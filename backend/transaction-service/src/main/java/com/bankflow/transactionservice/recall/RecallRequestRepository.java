package com.bankflow.transactionservice.recall;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecallRequestRepository
        extends JpaRepository<RecallRequest, Long> {

    Optional<RecallRequest> findByCancellationId(String cancellationId);

    List<RecallRequest> findByStatusOrderByCreatedAtAsc(RecallStatus status);

    List<RecallRequest> findByTransactionReferenceOrderByCreatedAtDesc(
            String transactionReference
    );

    /**
     * An open request on the same payment, in either direction. Asking twice
     * while the first is unanswered would put two claims on one payment.
     */
    Optional<RecallRequest> findByTransactionReferenceAndStatus(
            String transactionReference,
            RecallStatus status
    );
}
