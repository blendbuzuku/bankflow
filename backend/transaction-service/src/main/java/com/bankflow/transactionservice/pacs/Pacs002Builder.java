package com.bankflow.transactionservice.pacs;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.time.OffsetDateTime;

/**
 * Builds pacs.002 payment status reports.
 *
 * This is how we answer a payment somebody sent us: accepted and settled, or
 * rejected with a reason. The original message is echoed back by MsgId,
 * EndToEndId and TxId so the sender can match the answer to what they sent —
 * all three are mandatory in the schema, not conveniences.
 */
@Component
public class Pacs002Builder {

    private static final int ADDITIONAL_INFO_MAX = 105;

    private final KipsProperties properties;

    public Pacs002Builder(KipsProperties properties) {
        this.properties = properties;
    }

    /**
     * @param messageId          identifier for this status report
     * @param originalMessageId  MsgId of the pacs.008 being answered
     * @param originalEndToEndId end-to-end reference of the payment
     * @param originalTxId       transaction identifier from the original PmtId
     * @param status             ACSC when settled, RJCT when refused
     * @param reason             required for RJCT, omitted otherwise
     */
    public String build(
            String messageId,
            String originalMessageId,
            String originalEndToEndId,
            String originalTxId,
            TransactionStatusCode status,
            ReasonCode reason) {

        return build(
                messageId,
                originalMessageId,
                originalEndToEndId,
                originalTxId,
                null,
                status,
                reason,
                "ach"
        );
    }

    /**
     * @param originalUetr the payment's UETR, for RTGS only. It does not belong
     *                     in OrgnlTxId: a UUID is 36 characters and that field
     *                     is Max35Text, which is exactly why the RTGS schema
     *                     provides a separate OrgnlUETR element.
     * @param rail         "ach" or "rtgs"; the two shapes differ
     */
    public String build(
            String messageId,
            String originalMessageId,
            String originalEndToEndId,
            String originalTxId,
            String originalUetr,
            TransactionStatusCode status,
            ReasonCode reason,
            String rail) {

        if (status == TransactionStatusCode.RJCT && reason == null) {
            throw new IllegalArgumentException(
                    "A rejection must carry a reason code"
            );
        }

        if (!status.isPermittedOn(rail)) {
            throw new IllegalArgumentException(
                    "Status %s is not valid on the %s rail".formatted(
                            status.getCode(), rail
                    )
            );
        }

        return "rtgs".equals(rail)
                ? buildRtgs(messageId, originalMessageId, originalEndToEndId,
                        originalTxId, originalUetr, status, reason)
                : buildAch(messageId, originalMessageId, originalEndToEndId,
                        originalTxId, status, reason);
    }

    private String buildAch(
            String messageId,
            String originalMessageId,
            String originalEndToEndId,
            String originalTxId,
            TransactionStatusCode status,
            ReasonCode reason) {

        XmlBuilder xml = XmlBuilder.create();
        String ns = PacsMessageType.PACS_002.getNamespace();

        Element document = xml.root(ns, "Document");
        Element body = xml.child(document, "FIToFIPmtStsRpt");

        Element groupHeader = xml.child(body, "GrpHdr");

        xml.text(groupHeader, "MsgId", messageId);

        xml.text(
                groupHeader,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        agent(xml, groupHeader, "InstgAgt", properties.getBic());

        Element originalGroup = xml.child(body, "OrgnlGrpInfAndSts");

        xml.text(originalGroup, "OrgnlMsgId", originalMessageId);

        xml.text(
                originalGroup,
                "OrgnlMsgNmId",
                PacsMessageType.PACS_008.getIdentifier()
        );

        Element transaction = xml.child(body, "TxInfAndSts");

        /*
         * StsId identifies this status record. Reusing the message id keeps the
         * two traceable to each other without inventing a second identifier.
         */
        xml.text(transaction, "StsId", messageId);
        xml.text(transaction, "OrgnlEndToEndId", originalEndToEndId);
        xml.text(transaction, "OrgnlTxId", originalTxId);
        xml.text(transaction, "TxSts", status.getCode());

        if (reason != null) {

            Element statusReason = xml.child(transaction, "StsRsnInf");

            /*
             * Orgtr is mandatory: a rejection has to say who is rejecting.
             */
            Element originator = xml.child(statusReason, "Orgtr");
            xml.text(originator, "Nm", properties.getBankName());

            Element reasonElement = xml.child(statusReason, "Rsn");
            xml.text(reasonElement, "Cd", reason.getCode());

            /*
             * The meaning travels with the code, capped at the schema's 105
             * characters. A counterparty reading AC01 should not need a lookup
             * table to learn the account number was wrong.
             */
            xml.text(
                    statusReason,
                    "AddtlInf",
                    truncate(reason.getDescription())
            );
        }

        agent(xml, transaction, "InstgAgt", properties.getBic());

        return xml.toXml();
    }

    /**
     * The RTGS shape nests the original group information inside the
     * transaction rather than beside it, and carries the UETR in its own field.
     */
    private String buildRtgs(
            String messageId,
            String originalMessageId,
            String originalEndToEndId,
            String originalTxId,
            String originalUetr,
            TransactionStatusCode status,
            ReasonCode reason) {

        XmlBuilder xml = XmlBuilder.create();
        String ns = PacsMessageType.PACS_002.getNamespace();

        Element document = xml.root(ns, "Document");
        Element body = xml.child(document, "FIToFIPmtStsRpt");

        Element groupHeader = xml.child(body, "GrpHdr");

        xml.text(groupHeader, "MsgId", messageId);

        xml.text(
                groupHeader,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        Element transaction = xml.child(body, "TxInfAndSts");

        xml.text(transaction, "StsId", messageId);

        Element originalGroup = xml.child(transaction, "OrgnlGrpInf");
        xml.text(originalGroup, "OrgnlMsgId", originalMessageId);

        xml.text(
                originalGroup,
                "OrgnlMsgNmId",
                PacsMessageType.PACS_008.getIdentifier()
        );

        xml.optional(transaction, "OrgnlEndToEndId", originalEndToEndId);
        xml.optional(transaction, "OrgnlTxId", originalTxId);
        xml.optional(transaction, "OrgnlUETR", originalUetr);

        xml.text(transaction, "TxSts", status.getCode());

        if (reason != null) {

            Element statusReason = xml.child(transaction, "StsRsnInf");

            Element originator = xml.child(statusReason, "Orgtr");
            xml.text(originator, "Nm", properties.getBankName());

            Element reasonElement = xml.child(statusReason, "Rsn");
            xml.text(reasonElement, "Cd", reason.getCode());

            xml.text(
                    statusReason,
                    "AddtlInf",
                    truncate(reason.getDescription())
            );
        }

        /*
         * RTGS requires both agents on the transaction, where ACH leaves them
         * optional. We are answering, so we instruct and the operator is
         * instructed — it routes the answer onward to the original sender.
         */
        agent(xml, transaction, "InstgAgt", properties.getBic());
        agent(xml, transaction, "InstdAgt", properties.getOperatorBic());

        return xml.toXml();
    }

    private void agent(XmlBuilder xml, Element parent, String name, String bic) {

        Element agent = xml.child(parent, name);
        Element institution = xml.child(agent, "FinInstnId");
        xml.text(institution, "BICFI", bic);
    }

    private String truncate(String value) {

        return value.length() <= ADDITIONAL_INFO_MAX
                ? value
                : value.substring(0, ADDITIONAL_INFO_MAX);
    }
}
