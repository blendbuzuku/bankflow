package com.bankflow.accountservice.audit;

import com.bankflow.accountservice.security.AuthenticatedUser;
import com.bankflow.common.audit.AuditEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Writes the non-financial half of the business audit trail.
 *
 * Who was let in, who let them in, and what was opened for them. None of it
 * moves money, and all of it is what every later payment rests on: a status
 * column shows that a client is approved, never who approved them or when.
 *
 * Written with plain SQL rather than an entity on purpose. The table belongs
 * to transaction-service, which creates it and has altered it four times; a
 * second JPA mapping of the same table would be a second definition free to
 * drift from the first, and -- because Hibernate validates its mappings at
 * startup -- would also refuse to boot against a fresh database until the
 * owning service had run its migrations first. An insert has neither problem.
 *
 * Each write runs in its own transaction, for the same reason as its
 * counterpart in transaction-service: an audit row enlisted in the business
 * transaction would roll back with it, destroying the evidence of exactly the
 * events most worth keeping. A failed write is logged rather than thrown --
 * losing an audit row is bad, failing a client approval because of it is
 * worse.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final int SUMMARY_LIMIT = 500;

    private static final String INSERT = """
            INSERT INTO audit_events (
                event_type, transaction_reference, entity_type, entity_id,
                actor_user_id, actor_username, actor_role,
                summary, details, financial, occurred_at
            ) VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Records something that happened to a client or an account.
     *
     * There is no transaction reference: approving a client is not part of any
     * payment, and inventing one would file an administrative event inside a
     * payment's history where nobody would expect it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuditEventType eventType,
            String entityType,
            String entityId,
            String summary,
            Map<String, Object> details) {

        try {

            Actor actor = currentActor();

            jdbc.update(
                    INSERT,
                    eventType.name(),
                    entityType,
                    entityId,
                    actor.userId(),
                    actor.username(),
                    actor.role(),
                    truncate(summary),
                    serialize(details),

                    /*
                     * Taken from the type rather than from a caller, exactly as
                     * the entity does on the other side. Nothing this service
                     * records moves money, but that is the type's statement to
                     * make, not this method's.
                     */
                    eventType.isFinancial(),
                    LocalDateTime.now()
            );

        } catch (Exception exception) {

            log.error(
                    "Failed to write audit event {} for {} {}: {}",
                    eventType,
                    entityType,
                    entityId,
                    exception.getMessage(),
                    exception
            );
        }
    }

    /** Who did it, taken from the security context rather than a parameter. */
    private record Actor(Long userId, String username, String role) {
    }

    private Actor currentActor() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return new Actor(null, null, null);
        }

        Long userId = null;
        String username = null;

        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            userId = user.userId();
            username = user.username();
        }

        String role = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.startsWith("ROLE_")
                        ? authority.substring(5)
                        : authority)
                .orElse(null);

        return new Actor(userId, username, role);
    }

    private String serialize(Map<String, Object> details) {

        if (details == null || details.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception exception) {
            return "{\"serializationError\":\"" + exception.getMessage() + "\"}";
        }
    }

    private String truncate(String value) {

        if (value == null) {
            return null;
        }

        return value.length() <= SUMMARY_LIMIT
                ? value
                : value.substring(0, SUMMARY_LIMIT - 3) + "...";
    }
}
