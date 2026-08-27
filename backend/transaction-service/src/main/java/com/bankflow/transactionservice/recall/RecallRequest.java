package com.bankflow.transactionservice.recall;

import com.bankflow.transactionservice.pacs.ReasonCode;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A request to send a settled payment back.
 *
 * Kept apart from the transaction because a recall is a conversation about a
 * payment, not a state of it: it can be asked and refused, and the payment is
 * unchanged either way. Only an accepted recall moves money, and it does so
 * through a return of its own.
 */
@Entity
@Table(
        name = "recall_requests",
        indexes = {
                @Index(
                        name = "ix_recall_requests_reference",
                        columnList = "transaction_reference"
                ),
                @Index(
                        name = "ix_recall_requests_status",
                        columnList = "status"
                )
        }
)
public class RecallRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Our own identifier for the request, echoed as CxlId so the counterparty's
     * answer can be matched back.
     */
    @Column(name = "cancellation_id", nullable = false, unique = true, length = 35)
    private String cancellationId;

    @Column(name = "transaction_reference", nullable = false, length = 40)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecallDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RecallStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 4)
    private ReasonCode reasonCode;

    @Column(name = "additional_information", length = 105)
    private String additionalInformation;

    @Column(name = "requested_by_username", length = 100)
    private String requestedByUsername;

    /**
     * Who decided an inbound request. Left null on an outbound one, where the
     * decision is the counterparty's and reaches us as a message.
     */
    @Column(name = "decided_by_username", length = 100)
    private String decidedByUsername;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Why an inbound request was refused, for the audit trail. */
    @Column(name = "decision_note", length = 255)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isOpen() {
        return status == RecallStatus.REQUESTED;
    }

    // --- accessors ---

    public Long getId() {
        return id;
    }

    public String getCancellationId() {
        return cancellationId;
    }

    public void setCancellationId(String cancellationId) {
        this.cancellationId = cancellationId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public RecallDirection getDirection() {
        return direction;
    }

    public void setDirection(RecallDirection direction) {
        this.direction = direction;
    }

    public RecallStatus getStatus() {
        return status;
    }

    public void setStatus(RecallStatus status) {
        this.status = status;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(ReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public String getRequestedByUsername() {
        return requestedByUsername;
    }

    public void setRequestedByUsername(String requestedByUsername) {
        this.requestedByUsername = requestedByUsername;
    }

    public String getDecidedByUsername() {
        return decidedByUsername;
    }

    public void setDecidedByUsername(String decidedByUsername) {
        this.decidedByUsername = decidedByUsername;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
