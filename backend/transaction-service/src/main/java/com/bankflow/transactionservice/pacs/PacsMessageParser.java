package com.bankflow.transactionservice.pacs;

import com.bankflow.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Reads inbound KIPS messages.
 *
 * Traversal is by local name rather than XPath with namespace bindings: RTGS
 * transmissions arrive wrapped in the operator's envelope alongside a BAH, so a
 * document contains two ISO namespaces at once, and matching on local names
 * keeps one parser working for both wrapped and bare messages.
 */
@Component
public class PacsMessageParser {

    /**
     * The validatable parts of an inbound transmission.
     *
     * The Montran envelope that carries an RTGS message has no published
     * schema, so it cannot be validated as a whole. Splitting it lets each part
     * be checked against the schema that does exist: the document against its
     * pacs schema, the header against head.001.
     *
     * @param documentXml the ISO {@code Document} subtree, standalone
     * @param headerXml   the {@code AppHdr} subtree, or null when the message
     *                    arrived bare as ACH messages do
     */
    public record InboundEnvelope(String documentXml, String headerXml) {

        public boolean hasApplicationHeader() {
            return headerXml != null;
        }

        /**
         * Which KIPS schema set applies. Only RTGS wraps messages in a business
         * application header, so its presence identifies the rail.
         */
        public String rail() {
            return hasApplicationHeader() ? "rtgs" : "ach";
        }
    }

    /**
     * Splits an inbound transmission into the parts that can be schema-checked.
     */
    public InboundEnvelope split(String xml) {

        Element root = parseDocument(xml).getDocumentElement();

        Element document = firstByLocalName(root, "Document");

        if (document == null) {
            throw new BusinessException(
                    "Message contains no ISO 20022 Document element"
            );
        }

        Element header = firstByLocalName(root, "AppHdr");

        return new InboundEnvelope(
                serialise(document),
                header == null ? null : serialise(header)
        );
    }

    private String serialise(Element element) {

        try {

            Transformer transformer =
                    TransformerFactory.newInstance().newTransformer();

            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            StringWriter writer = new StringWriter();

            transformer.transform(
                    new DOMSource(element),
                    new StreamResult(writer)
            );

            return writer.toString();

        } catch (Exception exception) {
            throw new BusinessException(
                    "Could not isolate message part for validation",
                    exception
            );
        }
    }

    public ParsedMessage parse(String xml) {

        Document document = parseDocument(xml);
        Element root = document.getDocumentElement();

        /*
         * A wrapped RTGS message has the envelope as its root, so find the
         * payload by looking for the message body rather than assuming depth.
         */
        Element creditTransfer = firstByLocalName(root, "FIToFICstmrCdtTrf");

        if (creditTransfer != null) {
            return parseCreditTransfer(creditTransfer, xml);
        }

        Element statusReport = firstByLocalName(root, "FIToFIPmtStsRpt");

        if (statusReport != null) {
            return parseStatusReport(statusReport);
        }

        throw new BusinessException(
                "Unsupported message: expected a pacs.008 or pacs.002 payload"
        );
    }

    private ParsedMessage parseCreditTransfer(Element body, String xml) {

        Element groupHeader = firstByLocalName(body, "GrpHdr");
        Element transaction = firstByLocalName(body, "CdtTrfTxInf");

        if (transaction == null) {
            throw new BusinessException(
                    "pacs.008 contains no credit transfer transaction"
            );
        }

        Element paymentId = firstByLocalName(transaction, "PmtId");

        /*
         * ACH identifies the transaction with TxId and RTGS with UETR. Whichever
         * is present is the sender's handle on this payment.
         */
        String transactionId = textOf(paymentId, "TxId");

        if (transactionId == null) {
            transactionId = textOf(paymentId, "UETR");
        }

        Element amountElement = firstByLocalName(transaction, "IntrBkSttlmAmt");

        Element debtor = firstByLocalName(transaction, "Dbtr");
        Element debtorAccount = firstByLocalName(transaction, "DbtrAcct");
        Element debtorAgent = firstByLocalName(transaction, "DbtrAgt");

        Element creditor = firstByLocalName(transaction, "Cdtr");
        Element creditorAccount = firstByLocalName(transaction, "CdtrAcct");
        Element creditorAgent = firstByLocalName(transaction, "CdtrAgt");

        Element remittanceInfo = firstByLocalName(transaction, "RmtInf");

        return new ParsedMessage(
                PacsMessageType.PACS_008,
                textOf(groupHeader, "MsgId"),
                textOf(paymentId, "EndToEndId"),
                transactionId,
                null,
                null,
                null,
                textOf(debtor, "Nm"),
                textOf(debtorAccount, "IBAN"),
                textOf(debtorAgent, "BICFI"),
                textOf(creditor, "Nm"),
                textOf(creditorAccount, "IBAN"),
                textOf(creditorAgent, "BICFI"),
                amountElement == null
                        ? null
                        : new BigDecimal(amountElement.getTextContent().trim()),
                amountElement == null
                        ? null
                        : amountElement.getAttribute("Ccy"),
                textOf(remittanceInfo, "Ustrd")
        );
    }

    private ParsedMessage parseStatusReport(Element body) {

        Element groupHeader = firstByLocalName(body, "GrpHdr");
        Element originalGroup = firstByLocalName(body, "OrgnlGrpInfAndSts");
        Element transaction = firstByLocalName(body, "TxInfAndSts");

        String statusText = textOf(transaction, "TxSts");

        TransactionStatusCode status =
                statusText == null
                        ? null
                        : TransactionStatusCode.fromCode(statusText)
                                .orElseThrow(() -> new BusinessException(
                                        "Unrecognised transaction status: " + statusText
                                ));

        Element statusReason =
                transaction == null
                        ? null
                        : firstByLocalName(transaction, "StsRsnInf");

        String reasonText =
                statusReason == null
                        ? null
                        : textOf(firstByLocalName(statusReason, "Rsn"), "Cd");

        return new ParsedMessage(
                PacsMessageType.PACS_002,
                textOf(groupHeader, "MsgId"),
                textOf(transaction, "OrgnlEndToEndId"),
                textOf(transaction, "OrgnlTxId"),
                textOf(originalGroup, "OrgnlMsgId"),
                status,
                reasonText == null
                        ? null
                        : ReasonCode.fromCode(reasonText).orElse(ReasonCode.NARR),
                null, null, null,
                null, null, null,
                null, null, null
        );
    }

    /**
     * Depth-first search for the first descendant with a given local name.
     */
    private Element firstByLocalName(Element parent, String localName) {

        if (parent == null) {
            return null;
        }

        if (localName.equals(parent.getLocalName())) {
            return parent;
        }

        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {

            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element found = firstByLocalName((Element) child, localName);

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private String textOf(Element parent, String localName) {

        Element element = firstByLocalName(parent, localName);

        if (element == null) {
            return null;
        }

        String text = element.getTextContent();

        return text == null || text.isBlank() ? null : text.trim();
    }

    private Document parseDocument(String xml) {

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);

            /*
             * Inbound XML comes from outside the bank, so entity resolution and
             * DOCTYPE declarations stay firmly off.
             */
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
            throw new BusinessException(
                    "Message is not well-formed XML: " + exception.getMessage(),
                    exception
            );
        }
    }
}
