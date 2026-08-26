package com.bankflow.transactionservice.repository;

import com.bankflow.transactionservice.entity.AuditEvent;
import com.bankflow.transactionservice.entity.AuditEventType;
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
