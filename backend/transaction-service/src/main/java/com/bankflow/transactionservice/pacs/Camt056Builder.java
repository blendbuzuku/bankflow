package com.bankflow.transactionservice.pacs;

import com.bankflow.transactionservice.entity.Transaction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds camt.056 payment cancellation requests.
 *
 * This asks the beneficiary's bank to send a settled payment back. It is a
 * request and nothing more — no money moves when it is sent, and the other
 * bank is entitled to refuse. A yes arrives as a pacs.004 carrying the funds.
 *
 * The message is a case assignment rather than a payment: it names who is
 * asking and who is being asked, which is why its header looks unlike the
 * pacs family's.
 */
@Component
public class Camt056Builder {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int ADDITIONAL_INFO_MAX = 105;

    private final KipsProperties properties;

    public Camt056Builder(KipsProperties properties) {
        this.properties = properties;
    }

    /**
     * @param messageId      identifier for this request
     * @param cancellationId our handle on the request, echoed in the answer
     * @param original       the settled payment we want back
     * @param reason         why we are asking; CUST when the customer asked
     * @param additionalInfo free text from whoever raised it, may be null
     */
    public String build(
            String messageId,
            String cancellationId,
            Transaction original,
            ReasonCode reason,
            String additionalInfo) {

        if (reason == null) {
            throw new IllegalArgumentException(
                    "A cancellation request must carry a reason code"
            );
        }

        XmlBuilder xml = XmlBuilder.create();

        Element document = xml.root(
                PacsMessageType.CAMT_056.getNamespace("ach"), "Document"
        );

        Element body = xml.child(document, "FIToFIPmtCxlReq");

        // --- who is asking whom ---

        Element assignment = xml.child(body, "Assgnmt");

        xml.text(assignment, "Id", cancellationId);

        Element assigner = xml.child(assignment, "Assgnr");
        Element assignerParty = xml.child(assigner, "Pty");
        xml.text(assignerParty, "Nm", properties.getBankName());

        Element assignee = xml.child(assignment, "Assgne");
        Element assigneeParty = xml.child(assignee, "Pty");
        xml.text(
                assigneeParty,
                "Nm",
                creditorAgentName(original)
        );

        xml.text(
                assignment,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        // --- what we want back ---

        Element underlying = xml.child(body, "Undrlyg");
        Element tx = xml.child(underlying, "TxInf");

        xml.text(tx, "CxlId", cancellationId);

        Element originalGroup = xml.child(tx, "OrgnlGrpInf");
        xml.text(originalGroup, "OrgnlMsgId", original.getTransactionReference());

        xml.text(
                originalGroup,
                "OrgnlMsgNmId",
                PacsMessageType.PACS_008.getIdentifier("ach")
        );

        xml.optional(tx, "OrgnlInstrId", original.getInstructionId());
        xml.text(tx, "OrgnlEndToEndId", original.getEndToEndId());
        xml.text(tx, "OrgnlTxId", original.getTransactionReference());

        Element amount = xml.text(
                tx, "OrgnlIntrBkSttlmAmt", money(original.getAmount())
        );

        amount.setAttribute("Ccy", original.getCurrency().name());

        xml.text(
                tx,
                "OrgnlIntrBkSttlmDt",
                original.getValueDate().format(DATE)
        );

        Element cancellationReason = xml.child(tx, "CxlRsnInf");

        Element originator = xml.child(cancellationReason, "Orgtr");
        xml.text(originator, "Nm", properties.getBankName());

        Element reasonElement = xml.child(cancellationReason, "Rsn");
        xml.text(reasonElement, "Cd", reason.getCode());

        xml.text(
                cancellationReason,
                "AddtlInf",
                truncate(
                        additionalInfo == null || additionalInfo.isBlank()
                                ? reason.getDescription()
                                : additionalInfo
                )
        );

        Element originalRef = xml.child(tx, "OrgnlTxRef");

        party(xml, originalRef, "Dbtr", original.getDebtorName());
        account(xml, originalRef, "DbtrAcct", original.getDebtorIban());
        agent(xml, originalRef, "DbtrAgt", properties.getBic());

        agent(xml, originalRef, "CdtrAgt", original.getCreditorAgentBic());
        party(xml, originalRef, "Cdtr", original.getCreditorName());
        account(xml, originalRef, "CdtrAcct", original.getCreditorIban());

        return xml.toXml();
    }

    /**
     * The request is addressed to the bank holding the beneficiary. We know it
     * by BIC rather than by name, which is what goes in when there is nothing
     * better.
     */
    private String creditorAgentName(Transaction original) {

        String bic = original.getCreditorAgentBic();

        return bic == null || bic.isBlank() ? "Beneficiary bank" : bic;
    }

    private void party(
            XmlBuilder xml, Element parent, String name, String value) {

        if (value == null || value.isBlank()) {
            return;
        }

        Element wrapper = xml.child(parent, name);
        Element party = xml.child(wrapper, "Pty");
        xml.text(party, "Nm", value);
    }

    private void account(
            XmlBuilder xml, Element parent, String name, String iban) {

        if (iban == null || iban.isBlank()) {
            return;
        }

        Element account = xml.child(parent, name);
        Element id = xml.child(account, "Id");
        xml.text(id, "IBAN", iban);
    }

    private void agent(
            XmlBuilder xml, Element parent, String name, String bic) {

        if (bic == null || bic.isBlank()) {
            return;
        }

        Element agent = xml.child(parent, name);
        Element institution = xml.child(agent, "FinInstnId");
        xml.text(institution, "BICFI", bic);
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String truncate(String value) {

        return value.length() <= ADDITIONAL_INFO_MAX
                ? value
                : value.substring(0, ADDITIONAL_INFO_MAX);
    }
}
