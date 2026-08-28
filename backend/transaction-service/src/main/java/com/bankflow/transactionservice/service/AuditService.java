package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.entity.AuditEvent;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.repository.AuditEventRepository;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the business audit trail.
 *
 * Every method runs in its own transaction. That is the whole point: when a
 * payment fails, the business transaction rolls back, and an audit record
 * enlisted in it would roll back too — destroying the evidence of precisely the
 * events most worth keeping. REQUIRES_NEW means a recorded failure survives the
 * rollback of the thing that failed.
 *
 * For the same reason, a failure to write audit is logged rather than thrown:
 * losing an audit row is bad, but letting an audit problem roll back a
 * successfully settled payment is worse.
 */
@Service
public class AuditService {

    private static final Logger log =
            LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper) {

        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuditEventType eventType,
            String transactionReference,
            String entityType,
            String entityId,
            String summary,
            Map<String, Object> details) {

        try {

            AuditEvent event = new AuditEvent();

            event.setEventType(eventType);
            event.setTransactionReference(transactionReference);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setSummary(truncate(summary, 500));
            event.setDetails(serialize(details));

            applyActor(event);

            auditEventRepository.save(event);

        } catch (Exception exception) {

            log.error(
                    "Failed to write audit event {} for {}: {}",
                    eventType,
                    transactionReference,
                    exception.getMessage(),
                    exception
            );
        }
    }

    public void record(
            AuditEventType eventType,
            String transactionReference,
            String summary) {

        record(
                eventType,
                transactionReference,
                "Transaction",
                transactionReference,
                summary,
                null
        );
    }

    /**
     * Records a change as an explicit before/after pair, which is the form an
     * auditor actually wants to read.
     */
    public void recordChange(
            AuditEventType eventType,
            String transactionReference,
            String summary,
            Object before,
            Object after) {

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("before", before);
        details.put("after", after);

        record(
                eventType,
                transactionReference,
                "Transaction",
                transactionReference,
                summary,
                details
        );
    }

    /**
     * Stamps the acting principal onto the event.
     *
     * A service principal is recorded as such rather than left blank, so the
     * trail distinguishes "the system did this" from "we do not know who did
     * this".
     */
    private void applyActor(AuditEvent event) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return;
        }

        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            event.setActorUserId(user.userId());
            event.setActorUsername(user.username());
        }

        authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .ifPresent(authority ->
                        event.setActorRole(
                                authority.startsWith("ROLE_")
                                        ? authority.substring(5)
                                        : authority
                        )
                );
    }

    private String serialize(Map<String, Object> details) {

        if (details == null || details.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception exception) {
            return "{\"serializationError\":\""
                    + exception.getMessage()
                    + "\"}";
        }
    }

    private String truncate(String value, int max) {

        if (value == null) {
            return null;
        }

        return value.length() <= max
                ? value
                : value.substring(0, max - 3) + "...";
    }
}
