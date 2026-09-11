package com.bankflow.transactionservice.pacs;

import com.bankflow.transactionservice.entity.Transaction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds pacs.004 payment returns.
 *
 * A return sends a settled payment back. It is not a rejection: a rejection
 * (pacs.002 RJCT) says the payment was never accepted, while a return says it
 * was accepted, booked, and is now being reversed by a second payment
 * travelling the other way. The two are different events and the ledger
 * records them differently.
 *
 * Every field beginning Orgnl belongs to the payment being returned, which is
 * how the receiving bank matches it back.
 */
@Component
public class Pacs004Builder {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int ADDITIONAL_INFO_MAX = 105;

    private final KipsProperties properties;

    public Pacs004Builder(KipsProperties properties) {
        this.properties = properties;
    }

    /**
     * @param messageId     identifier for this return message
     * @param returnId      our handle on the return itself, distinct from the
     *                      original payment's identifiers
     * @param original      the payment being sent back
     * @param returnedAmount what is actually going back, which is the settled
     *                      amount — charges already taken are not undone by a
     *                      return
     * @param reason        why it is coming back
     * @param rail          "ach" or "rtgs"
     */
    public String build(
            String messageId,
            String returnId,
            Transaction original,
            BigDecimal returnedAmount,
            ReasonCode reason,
            String rail) {

        if (reason == null) {
            throw new IllegalArgumentException(
                    "A return must carry a reason code"
            );
        }

        boolean rtgs = "rtgs".equals(rail);

        /*
         * RTGS is a different message, not the ACH one in another namespace.
         * HVPS+ drops the batch total and the header agents, requires a
         * clearing system and the original UETR, moves the agents onto the
         * transaction, and describes the parties in a return chain rather
         * than an original-transaction reference. Built as ACH, every RTGS
         * return failed the schema and was discarded, so none could land.
         */
        if (rtgs) {
            return buildRtgs(messageId, returnId, original, returnedAmount, reason);
        }

        XmlBuilder xml = XmlBuilder.create();

        Element document = xml.root(
                PacsMessageType.PACS_004.getNamespace(rail), "Document"
        );

        Element body = xml.child(document, "PmtRtr");

        // --- group header ---

        Element groupHeader = xml.child(body, "GrpHdr");

        xml.text(groupHeader, "MsgId", messageId);

        xml.text(
                groupHeader,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        xml.text(groupHeader, "NbOfTxs", "1");

        Element total = xml.text(
                groupHeader,
                "TtlRtrdIntrBkSttlmAmt",
                money(returnedAmount)
        );

        total.setAttribute("Ccy", original.getCurrency().name());

        xml.text(groupHeader, "IntrBkSttlmDt", LocalDate.now().format(DATE));

        Element settlement = xml.child(groupHeader, "SttlmInf");

        xml.text(
                settlement,
                "SttlmMtd",
                original.getPaymentType().getSettlementMethod().getCode()
        );

        agent(xml, groupHeader, "InstgAgt", properties.getBic());
        agent(xml, groupHeader, "InstdAgt", properties.getOperatorBic());

        // --- the returned transaction ---

        Element tx = xml.child(body, "TxInf");

        xml.text(tx, "RtrId", returnId);

        Element originalGroup = xml.child(tx, "OrgnlGrpInf");
        xml.text(originalGroup, "OrgnlMsgId", original.getTransactionReference());

        xml.text(
                originalGroup,
                "OrgnlMsgNmId",
                PacsMessageType.PACS_008.getIdentifier(rail)
        );

        xml.optional(tx, "OrgnlInstrId", original.getInstructionId());
        xml.text(tx, "OrgnlEndToEndId", original.getEndToEndId());

        /*
         * OrgnlTxId is Max35Text and a UETR is 36 characters, so RTGS carries
         * the UETR in its own element — the same split the status report makes.
         */
        if (rtgs && original.getUetr() != null) {
            xml.text(tx, "OrgnlTxId", original.getTransactionReference());
            xml.text(tx, "OrgnlUETR", original.getUetr());
        } else {
            xml.text(tx, "OrgnlTxId", original.getTransactionReference());
        }

        Element originalAmount = xml.text(
                tx, "OrgnlIntrBkSttlmAmt", money(original.getAmount())
        );

        originalAmount.setAttribute("Ccy", original.getCurrency().name());

        Element returned = xml.text(
                tx, "RtrdIntrBkSttlmAmt", money(returnedAmount)
        );

        returned.setAttribute("Ccy", original.getCurrency().name());

        // --- why ---

        Element returnReason = xml.child(tx, "RtrRsnInf");

        Element originator = xml.child(returnReason, "Orgtr");
        xml.text(originator, "Nm", properties.getBankName());

        Element reasonElement = xml.child(returnReason, "Rsn");
        xml.text(reasonElement, "Cd", reason.getCode());

        xml.text(
                returnReason,
                "AddtlInf",
                truncate(reason.getDescription())
        );

        /*
         * OrgnlTxRef restates the original payment's parties. The receiving
         * bank has them already, but the schema requires them and a return
         * that travels alone still has to describe what it undoes.
         */
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
     * pacs.004.001.10 in the HVPS+ usage KIPS publishes for RTGS.
     *
     * Element order follows PaymentTransaction118__1 exactly; the schema is a
     * sequence, so an element out of place fails validation even when every
     * value in it is right.
     */
    private String buildRtgs(
            String messageId,
            String returnId,
            Transaction original,
            BigDecimal returnedAmount,
            ReasonCode reason) {

        if (original.getUetr() == null) {
            throw new IllegalArgumentException(
                    "An RTGS return must name the original UETR, and %s has none"
                            .formatted(original.getTransactionReference())
            );
        }

        String currency = original.getCurrency().name();

        XmlBuilder xml = XmlBuilder.create();

        Element document = xml.root(
                PacsMessageType.PACS_004.getNamespace("rtgs"), "Document"
        );

        Element body = xml.child(document, "PmtRtr");

        // --- group header: identity and how it settles, nothing else ---

        Element groupHeader = xml.child(body, "GrpHdr");

        xml.text(groupHeader, "MsgId", messageId);
        xml.text(groupHeader, "CreDtTm", XmlBuilder.formatDateTime(OffsetDateTime.now()));
        xml.text(groupHeader, "NbOfTxs", "1");

        Element settlement = xml.child(groupHeader, "SttlmInf");

        xml.text(
                settlement,
                "SttlmMtd",
                original.getPaymentType().getSettlementMethod().getCode()
        );

        Element clearing = xml.child(settlement, "ClrSys");
        xml.text(clearing, "Cd", properties.getRtgsClearingSystem());

        // --- the returned transaction ---

        Element tx = xml.child(body, "TxInf");

        xml.text(tx, "RtrId", returnId);

        Element originalGroup = xml.child(tx, "OrgnlGrpInf");
        xml.text(originalGroup, "OrgnlMsgId", original.getTransactionReference());
        xml.text(originalGroup, "OrgnlMsgNmId", PacsMessageType.PACS_008.getIdentifier("rtgs"));

        xml.optional(tx, "OrgnlInstrId", original.getInstructionId());
        xml.text(tx, "OrgnlEndToEndId", original.getEndToEndId());
        xml.text(tx, "OrgnlTxId", original.getTransactionReference());
        xml.text(tx, "OrgnlUETR", original.getUetr());

        xml.text(tx, "OrgnlIntrBkSttlmAmt", money(original.getAmount()))
                .setAttribute("Ccy", currency);

        xml.text(tx, "RtrdIntrBkSttlmAmt", money(returnedAmount))
                .setAttribute("Ccy", currency);

        xml.text(tx, "IntrBkSttlmDt", LocalDate.now().format(DATE));

        agent(xml, tx, "InstgAgt", properties.getBic());
        agent(xml, tx, "InstdAgt", properties.getOperatorBic());

        /*
         * The return chain restates who paid whom in the original payment.
         * Debtor and creditor are both required, so a missing name falls back
         * to the account rather than leaving the element out.
         */
        Element chain = xml.child(tx, "RtrChain");

        requiredParty(xml, chain, "Dbtr", original.getDebtorName(), original.getDebtorIban());
        account(xml, chain, "DbtrAcct", original.getDebtorIban());
        agent(xml, chain, "DbtrAgt",
                original.getDebtorAgentBic() != null
                        ? original.getDebtorAgentBic()
                        : properties.getBic());

        agent(xml, chain, "CdtrAgt", original.getCreditorAgentBic());
        requiredParty(xml, chain, "Cdtr", original.getCreditorName(), original.getCreditorIban());
        account(xml, chain, "CdtrAcct", original.getCreditorIban());

        // --- why ---

        Element returnReason = xml.child(tx, "RtrRsnInf");

        Element originator = xml.child(returnReason, "Orgtr");
        xml.text(originator, "Nm", properties.getBankName());

        Element reasonElement = xml.child(returnReason, "Rsn");
        xml.text(reasonElement, "Cd", reason.getCode());

        xml.text(returnReason, "AddtlInf", truncate(reason.getDescription()));

        return xml.toXml();
    }

    private void requiredParty(
            XmlBuilder xml, Element parent, String name, String value, String fallback) {

        String shown = value != null && !value.isBlank()
                ? value
                : fallback != null && !fallback.isBlank() ? fallback : "Not stated";

        Element wrapper = xml.child(parent, name);
        Element party = xml.child(wrapper, "Pty");
        xml.text(party, "Nm", shown);
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
