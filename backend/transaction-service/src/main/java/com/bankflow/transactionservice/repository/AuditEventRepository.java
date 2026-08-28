package com.bankflow.transactionservice.repository;

import com.bankflow.transactionservice.entity.AuditEvent;
import com.bankflow.common.audit.AuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** Full history of one payment, oldest first. */
    List<AuditEvent> findByTransactionReferenceOrderByOccurredAtAsc(
            String transactionReference
    );

    /**
     * Everything recorded against one thing that is not a payment.
     *
     * A client or an account has no transaction reference, so the per-payment
     * query above cannot reach these. Without it the approval events would be
     * written and never readable, which is the same as not recording them.
     */
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtAsc(
            String entityType,
            String entityId
    );

    Page<AuditEvent> findByEventType(
            AuditEventType eventType,
            Pageable pageable
    );

    Page<AuditEvent> findByActorUserId(Long actorUserId, Pageable pageable);

    Page<AuditEvent> findByOccurredAtBetween(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );
}
