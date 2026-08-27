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

        Element paymentReturn = firstByLocalName(root, "PmtRtr");

        if (paymentReturn != null) {
            return parseReturn(paymentReturn);
        }

        Element cancellation = firstByLocalName(root, "FIToFIPmtCxlReq");

        if (cancellation != null) {
            return parseCancellationRequest(cancellation);
        }

        throw new BusinessException(
                "Unsupported message: expected a pacs.008, pacs.002, pacs.004 "
                        + "or camt.056 payload"
        );
    }

    /**
     * A payment coming back.
     *
     * The identifiers we match on are the original payment's, not the return's:
     * RtrId names this return, while OrgnlTxId and OrgnlEndToEndId name what is
     * being undone, which is the payment on our books.
     */
    private ParsedMessage parseReturn(Element body) {

        Element groupHeader = firstByLocalName(body, "GrpHdr");
        Element transaction = firstByLocalName(body, "TxInf");

        if (transaction == null) {
            throw new BusinessException(
                    "pacs.004 contains no returned transaction"
            );
        }

        Element originalGroup = firstByLocalName(transaction, "OrgnlGrpInf");
        Element returnReason = firstByLocalName(transaction, "RtrRsnInf");

        /*
         * The returned amount is what actually comes back, which need not be
         * the original amount — a bank may keep its charges. That figure, not
         * the original, is what we credit.
         */
        Element returnedAmount =
                firstByLocalName(transaction, "RtrdIntrBkSttlmAmt");

        String transactionId = textOf(transaction, "OrgnlUETR");

        if (transactionId == null) {
            transactionId = textOf(transaction, "OrgnlTxId");
        }

        Element originalRef = firstByLocalName(transaction, "OrgnlTxRef");
        Element debtor = firstByLocalName(originalRef, "Dbtr");
        Element debtorAccount = firstByLocalName(originalRef, "DbtrAcct");
        Element creditor = firstByLocalName(originalRef, "Cdtr");
        Element creditorAccount = firstByLocalName(originalRef, "CdtrAcct");

        return new ParsedMessage(
                PacsMessageType.PACS_004,
                textOf(groupHeader, "MsgId"),
                textOf(transaction, "OrgnlEndToEndId"),
                transactionId,
                textOf(originalGroup, "OrgnlMsgId"),
                null,
                reasonOf(returnReason),
                textOf(debtor, "Nm"),
                textOf(debtorAccount, "IBAN"),
                null,
                textOf(creditor, "Nm"),
                textOf(creditorAccount, "IBAN"),
                null,
                returnedAmount == null
                        ? null
                        : new BigDecimal(returnedAmount.getTextContent().trim()),
                returnedAmount == null
                        ? null
                        : returnedAmount.getAttribute("Ccy"),
                null
        );
    }

    /** Another bank asking for a payment it sent us to be sent back. */
    private ParsedMessage parseCancellationRequest(Element body) {

        Element assignment = firstByLocalName(body, "Assgnmt");
        Element transaction = firstByLocalName(body, "TxInf");

        if (transaction == null) {
            throw new BusinessException(
                    "camt.056 contains no underlying transaction"
            );
        }

        Element originalGroup = firstByLocalName(transaction, "OrgnlGrpInf");
        Element cancellationReason = firstByLocalName(transaction, "CxlRsnInf");
        Element amount =
                firstByLocalName(transaction, "OrgnlIntrBkSttlmAmt");

        String transactionId = textOf(transaction, "OrgnlUETR");

        if (transactionId == null) {
            transactionId = textOf(transaction, "OrgnlTxId");
        }

        Element originalRef = firstByLocalName(transaction, "OrgnlTxRef");
        Element debtor = firstByLocalName(originalRef, "Dbtr");
        Element debtorAccount = firstByLocalName(originalRef, "DbtrAcct");
        Element creditor = firstByLocalName(originalRef, "Cdtr");
        Element creditorAccount = firstByLocalName(originalRef, "CdtrAcct");

        return new ParsedMessage(
                PacsMessageType.CAMT_056,
                textOf(assignment, "Id"),
                textOf(transaction, "OrgnlEndToEndId"),
                transactionId,
                textOf(originalGroup, "OrgnlMsgId"),
                null,
                reasonOf(cancellationReason),
                textOf(debtor, "Nm"),
                textOf(debtorAccount, "IBAN"),
                null,
                textOf(creditor, "Nm"),
                textOf(creditorAccount, "IBAN"),
                null,
                amount == null
                        ? null
                        : new BigDecimal(amount.getTextContent().trim()),
                amount == null ? null : amount.getAttribute("Ccy"),
                textOf(cancellationReason, "AddtlInf")
        );
    }

    /**
     * Reads a reason code we recognise. An unknown code is not a parse failure
     * — the counterparty may use one outside our list, and the message is still
     * actionable without it.
     */
    private ReasonCode reasonOf(Element reasonInfo) {

        Element reason = firstByLocalName(reasonInfo, "Rsn");
        String code = textOf(reason, "Cd");

        if (code == null) {
            return null;
        }

        try {
            return ReasonCode.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
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
