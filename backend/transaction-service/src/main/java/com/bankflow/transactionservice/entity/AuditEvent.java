package com.bankflow.transactionservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One immutable entry in the business audit trail.
 *
 * Deliberately append-only: there are no setters for the recorded facts beyond
 * construction, no update timestamp, and nothing in the codebase deletes rows.
 * An audit record that can be edited is not evidence of anything.
 *
 * Rows are keyed by transaction reference rather than by database ID so a whole
 * payment's history can be pulled in one query, including events recorded
 * before the transaction row itself was persisted.
 */
@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(
                        name = "idx_audit_transaction_reference",
                        columnList = "transaction_reference"
                ),
                @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_audit_event_type", columnList = "event_type"),
                @Index(name = "idx_audit_actor", columnList = "actor_user_id")
        }
)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private AuditEventType eventType;

    /**
     * Payment this event belongs to. Null for events that are not about a
     * specific payment, such as a tariff change.
     */
    @Column(name = "transaction_reference", updatable = false, length = 40)
    private String transactionReference;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 50)
    private String entityId;

    /**
     * Who caused this. Null only for events raised by a scheduled process with
     * no human or service principal attached.
     */
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_username", updatable = false, length = 50)
    private String actorUsername;

    @Column(name = "actor_role", updatable = false, length = 30)
    private String actorRole;

    /** Short human-readable statement of what happened. */
    @Column(nullable = false, updatable = false, length = 500)
    private String summary;

    /**
     * Before/after state as JSON. Text rather than jsonb so the audit trail
     * never fails to write because a payload did not parse.
     */
    @Column(name = "details", updatable = false, columnDefinition = "text")
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public AuditEvent() {
    }

    @PrePersist
    protected void onCreate() {

        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
