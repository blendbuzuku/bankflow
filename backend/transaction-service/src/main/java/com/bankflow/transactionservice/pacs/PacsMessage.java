package com.bankflow.transactionservice.pacs;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A stored ISO 20022 message.
 *
 * The raw XML is kept verbatim rather than only as parsed fields. A bank has to
 * be able to show exactly what it sent or received, byte for byte — reserialising
 * from parsed columns would produce something similar but not identical, which is
 * no use in a dispute.
 *
 * Linked to a transaction by reference rather than by a JPA relationship so a
 * message can be stored even when the payment it concerns is unknown to us,
 * which is precisely the case for an inbound payment naming an IBAN we do not
 * hold.
 */
@Entity
@Table(
        name = "pacs_messages",
        indexes = {
                @Index(name = "idx_pacs_transaction_reference",
                        columnList = "transaction_reference"),
                @Index(name = "idx_pacs_message_type", columnList = "message_type"),
                @Index(name = "idx_pacs_created_at", columnList = "created_at"),
                @Index(name = "idx_pacs_related_message_id",
                        columnList = "related_message_id")
        }
)
public class PacsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The message's own MsgId, as it appears in the GrpHdr. Unique because a
     * counterparty uses it to reference this message in a status report.
     */
    @Column(name = "message_id", nullable = false, unique = true, length = 35)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private PacsMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PacsMessageStatus status;

    @Column(name = "transaction_reference", length = 40)
    private String transactionReference;

    @Column(name = "end_to_end_id", length = 50)
    private String endToEndId;

    /**
     * For a status report, the MsgId of the message being answered.
     */
    @Column(name = "related_message_id", length = 35)
    private String relatedMessageId;

    /**
     * ISO status code carried by the message, e.g. ACSC or RJCT for a pacs.002.
     */
    @Column(name = "status_code", length = 10)
    private String statusCode;

    /**
     * ISO reason code when the message reports a refusal, e.g. AC01.
     */
    @Column(name = "reason_code", length = 10)
    private String reasonCode;

    @Column(name = "raw_xml", nullable = false, columnDefinition = "text")
    private String rawXml;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PacsMessage() {
    }

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = PacsMessageStatus.GENERATED;
        }
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public PacsMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(PacsMessageType messageType) {
        this.messageType = messageType;
    }

    public MessageDirection getDirection() {
        return direction;
    }

    public void setDirection(MessageDirection direction) {
        this.direction = direction;
    }

    public PacsMessageStatus getStatus() {
        return status;
    }

    public void setStatus(PacsMessageStatus status) {
        this.status = status;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public String getRelatedMessageId() {
        return relatedMessageId;
    }

    public void setRelatedMessageId(String relatedMessageId) {
        this.relatedMessageId = relatedMessageId;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getRawXml() {
        return rawXml;
    }

    public void setRawXml(String rawXml) {
        this.rawXml = rawXml;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
