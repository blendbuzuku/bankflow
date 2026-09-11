package com.bankflow.transactionservice.pacs;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * Builds the head.001 Business Application Header and the Montran envelope
 * that carries it.
 *
 * KIPS applies this to RTGS only. Annex D shows ACH messages as a bare
 * {@code Document} with no header at all, so wrapping an ACH message here would
 * produce something the scheme does not expect.
 *
 * The envelope namespace {@code urn:montran:message.02} is the operator's own,
 * and no schema was published for it, so the wrapper itself cannot be
 * validated — only the {@code AppHdr} and {@code Document} inside it.
 */
@Component
public class BusinessApplicationHeaderBuilder {

    public static final String HEAD_NAMESPACE =
            "urn:iso:std:iso:20022:tech:xsd:head.001.001.02";

    public static final String ENVELOPE_NAMESPACE =
            "urn:montran:message.02";

    private final KipsProperties properties;

    public BusinessApplicationHeaderBuilder(KipsProperties properties) {
        this.properties = properties;
    }

    /**
     * The header on its own, which is what gets schema-validated against
     * head.001.
     *
     * @param messageId    BizMsgIdr; matches the MsgId of the document it heads
     * @param messageType  identifies the message definition being carried
     */
    public String buildHeader(String messageId, PacsMessageType messageType) {

        XmlBuilder xml = XmlBuilder.create();
        Element header = xml.root(HEAD_NAMESPACE, "AppHdr");

        appendHeaderContent(xml, header, messageId, messageType,
                properties.getBic(), properties.getOperatorBic());

        return xml.toXml();
    }

    /**
     * The full transmission: envelope, header, then the document.
     *
     * The document is re-parsed and imported rather than concatenated as text,
     * so the result is a single well-formed tree and the document's own
     * namespace is preserved.
     */
    public String wrap(
            String messageId,
            PacsMessageType messageType,
            String documentXml) {

        return wrap(messageId, messageType, documentXml,
                properties.getBic(), properties.getOperatorBic());
    }

    /**
     * A transmission as the scheme delivers it to us: from the operator,
     * addressed to this bank — the reverse of what we send.
     */
    public String wrapInbound(
            String messageId,
            PacsMessageType messageType,
            String documentXml) {

        return wrap(messageId, messageType, documentXml,
                properties.getOperatorBic(), properties.getBic());
    }

    private String wrap(
            String messageId,
            PacsMessageType messageType,
            String documentXml,
            String fromBic,
            String toBic) {

        XmlBuilder xml = XmlBuilder.create();
        Element envelope = xml.root(ENVELOPE_NAMESPACE, "Message");

        Element header = xml.element(HEAD_NAMESPACE, "AppHdr");
        envelope.appendChild(header);

        appendHeaderContent(xml, header, messageId, messageType, fromBic, toBic);

        Element documentElement = parse(documentXml).getDocumentElement();

        /*
         * The document arrives already indented. Left alone, its whitespace
         * text nodes survive the import and the serialiser then indents around
         * them, producing a ragged mess. Stripping them lets the output be
         * formatted once, consistently.
         */
        stripIgnorableWhitespace(documentElement);

        Node imported = xml.document().importNode(documentElement, true);

        envelope.appendChild(imported);

        return xml.toXml();
    }

    private void appendHeaderContent(
            XmlBuilder xml,
            Element header,
            String messageId,
            PacsMessageType messageType,
            String fromBic,
            String toBic) {

        if (!MessageIdGenerator.isValidBusinessMessageId(messageId)) {
            throw new IllegalArgumentException(
                    "BizMsgIdr must be 1-35 characters from the restricted FIN "
                            + "character set (no underscores): " + messageId
            );
        }

        party(xml, header, "Fr", fromBic);
        party(xml, header, "To", toBic);

        xml.text(header, "BizMsgIdr", messageId);

        /*
         * The RTGS identifier, because only RTGS carries a header. For most
         * messages the two agree; for pacs.004 they do not (.10 against ACH's
         * .09), and the header would have named a definition different from
         * the document it carries.
         */
        xml.text(header, "MsgDefIdr", messageType.getIdentifier("rtgs"));
        xml.text(header, "BizSvc", properties.getBusinessService());

        /*
         * CreDt must carry an explicit offset. The schema pattern requires
         * (+|-)HH:MM, so a UTC instant rendered as "Z" is rejected outright.
         */
        xml.text(
                header,
                "CreDt",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );
    }

    private void party(XmlBuilder xml, Element parent, String name, String bic) {

        Element party = xml.child(parent, name);
        Element financialInstitution = xml.child(party, "FIId");
        Element institutionId = xml.child(financialInstitution, "FinInstnId");
        xml.text(institutionId, "BICFI", bic);
    }

    /**
     * Removes text nodes that hold nothing but layout whitespace.
     *
     * Only applied to elements that contain other elements: a node whose text
     * is genuinely blank-but-meaningful would be a schema violation anyway, and
     * element content is never mixed in ISO 20022.
     */
    private void stripIgnorableWhitespace(Node node) {

        NodeList children = node.getChildNodes();

        for (int i = children.getLength() - 1; i >= 0; i--) {

            Node child = children.item(i);

            if (child.getNodeType() == Node.TEXT_NODE) {

                if (child.getTextContent() == null
                        || child.getTextContent().isBlank()) {

                    node.removeChild(child);
                }

            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                stripIgnorableWhitespace(child);
            }
        }
    }

    private Document parse(String xml) {

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);

            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            return factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(
                            xml.getBytes(StandardCharsets.UTF_8)
                    )
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not parse document for enveloping",
                    exception
            );
        }
    }
}
