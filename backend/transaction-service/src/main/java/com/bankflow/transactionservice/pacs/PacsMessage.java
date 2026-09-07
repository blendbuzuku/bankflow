package com.bankflow.transactionservice.pacs;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A stored ISO 20022 message.
 *
 * The raw XML is kept verbatim rather than only as parsed fields. A bank has to
 * be able to show exactly what it sent or received, byte for byte — reserialising
 * from parsed columns would produce something similar but not identical, which is
 * no use in a dispute.
 *
 * Linked to a transaction by reference rather than by a relationship, so a
 * message can be stored even when the payment it concerns is unknown to us --
 * precisely the case for an inbound payment naming an IBAN we do not hold.
 *
 * Held in MongoDB rather than beside the ledger, because everything about it is
 * document-shaped: the payload is literally a document, it is written once and
 * never updated, a pacs.008 and a camt.056 have almost no fields in common, and
 * it is searched by attribute and body text rather than joined to anything.
 * Money stays in Postgres, where debits and credits have to commit or fail
 * together and reconciliation depends on being able to join them.
 *
 * One consequence worth stating plainly: a message write is no longer inside
 * the payment's database transaction, because the two stores cannot share one.
 * Messages are written before the money moves, so a failure between the two
 * leaves a message with no payment rather than a payment with no message --
 * the recoverable direction, since the message records what was meant to
 * happen and the ledger is what must never be wrong.
 */
@Document(collection = "scheme_messages")
public class PacsMessage {

    /**
     * The message's own MsgId, as it appears in the GrpHdr.
     *
     * Used as the document key rather than a generated one. It is unique by
     * the scheme's own rules, it travels with the message, and a counterparty
     * quotes it back when answering — so a second identifier would be one more
     * way to look a message up and one more thing to get wrong.
     */
    @Id
    private String messageId;

    @Indexed
    private PacsMessageType messageType;

    private MessageDirection direction;

    private PacsMessageStatus status;

    /** Indexed: one payment's whole history is fetched by this. */
    @Indexed
    private String transactionReference;

    @Indexed
    private String endToEndId;

    /**
     * For a status report, the MsgId of the message being answered.
     */
    @Indexed
    private String relatedMessageId;

    /**
     * ISO status code carried by the message, e.g. ACSC or RJCT for a pacs.002.
     */
    private String statusCode;

    /**
     * ISO reason code when the message reports a refusal, e.g. AC01.
     */
    private String reasonCode;

    private String rawXml;

    @Indexed
    private LocalDateTime createdAt;

    public PacsMessage() {
    }

    /**
     * Defaults applied on the way in.
     *
     * This was a JPA lifecycle callback, which nothing invokes for us any
     * more. The archive calls it before saving, so a message still cannot be
     * stored without a timestamp or a status.
     */
    void applyDefaults() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = PacsMessageStatus.GENERATED;
        }
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
