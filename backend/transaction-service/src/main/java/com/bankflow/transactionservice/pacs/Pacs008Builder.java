package com.bankflow.transactionservice.pacs;

import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.entity.Transaction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds pacs.008 customer credit transfers for both KIPS rails.
 *
 * The two rails are not the same message with different values — they are
 * different shapes, so each gets its own method rather than one method threaded
 * with conditionals:
 *
 * <ul>
 *   <li>ACH carries the batch total and settlement date in the group header,
 *       identifies the transaction with TxId, and requires a service level.</li>
 *   <li>RTGS keeps the group header minimal, settles per transaction, and
 *       identifies the payment with a UETR that follows it end to end.</li>
 * </ul>
 */
@Component
public class Pacs008Builder {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final KipsProperties properties;

    public Pacs008Builder(KipsProperties properties) {
        this.properties = properties;
    }

    /**
     * @param transaction the payment to describe
     * @param messageId   value for GrpHdr/MsgId, also used as the BizMsgIdr
     */
    public String build(Transaction transaction, String messageId) {

        return transaction.getPaymentType() == PaymentType.KIPS_RTGS
                ? buildRtgs(transaction, messageId)
                : buildAch(transaction, messageId);
    }

    // --- ACH ---------------------------------------------------------------

    private String buildAch(Transaction transaction, String messageId) {

        XmlBuilder xml = XmlBuilder.create();
        String ns = PacsMessageType.PACS_008.getNamespace();

        Element document = xml.root(ns, "Document");
        Element body = xml.child(document, "FIToFICstmrCdtTrf");

        Element groupHeader = xml.child(body, "GrpHdr");

        xml.text(groupHeader, "MsgId", messageId);

        xml.text(
                groupHeader,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        xml.text(groupHeader, "NbOfTxs", "1");

        /*
         * ACH clears in batches, so the group header states the total being
         * settled and the date it settles on. With one transaction per message
         * the total equals the transaction amount.
         */
        Element total = xml.text(
                groupHeader,
                "TtlIntrBkSttlmAmt",
                money(transaction.getAmount())
        );

        total.setAttribute("Ccy", transaction.getCurrency().name());

        xml.text(
                groupHeader,
                "IntrBkSttlmDt",
                transaction.getValueDate().format(DATE)
        );

        Element settlement = xml.child(groupHeader, "SttlmInf");

        xml.text(
                settlement,
                "SttlmMtd",
                transaction.getPaymentType().getSettlementMethod().getCode()
        );

        agent(xml, groupHeader, "InstgAgt", properties.getBic());
        agent(xml, groupHeader, "InstdAgt", properties.getOperatorBic());

        Element tx = xml.child(body, "CdtTrfTxInf");

        Element paymentId = xml.child(tx, "PmtId");
        xml.optional(paymentId, "InstrId", transaction.getInstructionId());
        xml.text(paymentId, "EndToEndId", transaction.getEndToEndId());
        xml.text(paymentId, "TxId", transaction.getTransactionReference());

        Element paymentTypeInfo = xml.child(tx, "PmtTpInf");
        Element serviceLevel = xml.child(paymentTypeInfo, "SvcLvl");

        xml.text(
                serviceLevel,
                "Cd",
                transaction.getPaymentType().getServiceLevel().getCode()
        );

        Element amount = xml.text(
                tx,
                "IntrBkSttlmAmt",
                money(transaction.getAmount())
        );

        amount.setAttribute("Ccy", transaction.getCurrency().name());

        xml.text(tx, "ChrgBr", transaction.getChargeBearer().getCode());

        party(xml, tx, "Dbtr", transaction.getDebtorName());
        account(xml, tx, "DbtrAcct", transaction.getDebtorIban());

        agent(xml, tx, "DbtrAgt", agentOrOwn(transaction.getDebtorAgentBic()));
        agent(xml, tx, "CdtrAgt", agentOrOwn(transaction.getCreditorAgentBic()));

        party(xml, tx, "Cdtr", transaction.getCreditorName());
        account(xml, tx, "CdtrAcct", transaction.getCreditorIban());

        purpose(xml, tx, transaction);
        remittance(xml, tx, transaction);

        return xml.toXml();
    }

    // --- RTGS --------------------------------------------------------------

    private String buildRtgs(Transaction transaction, String messageId) {

        XmlBuilder xml = XmlBuilder.create();
        String ns = PacsMessageType.PACS_008.getNamespace();

        Element document = xml.root(ns, "Document");
        Element body = xml.child(document, "FIToFICstmrCdtTrf");

        Element groupHeader = xml.child(body, "GrpHdr");

        xml.text(groupHeader, "MsgId", messageId);

        xml.text(
                groupHeader,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        xml.text(groupHeader, "NbOfTxs", "1");

        /*
         * RTGS settles each payment individually, so there is no batch total
         * here — the amount and settlement date live on the transaction.
         */
        Element settlement = xml.child(groupHeader, "SttlmInf");

        xml.text(
                settlement,
                "SttlmMtd",
                transaction.getPaymentType().getSettlementMethod().getCode()
        );

        Element clearing = xml.child(settlement, "ClrSys");
        xml.text(clearing, "Cd", properties.getRtgsClearingSystem());

        Element tx = xml.child(body, "CdtTrfTxInf");

        Element paymentId = xml.child(tx, "PmtId");
        xml.optional(paymentId, "InstrId", transaction.getInstructionId());
        xml.text(paymentId, "EndToEndId", transaction.getEndToEndId());
        xml.text(paymentId, "UETR", transaction.getUetr());

        Element paymentTypeInfo = xml.child(tx, "PmtTpInf");
        xml.text(paymentTypeInfo, "InstrPrty", "HIGH");

        Element localInstrument = xml.child(paymentTypeInfo, "LclInstrm");

        xml.text(
                localInstrument,
                "Prtry",
                properties.getRtgsLocalInstrument()
        );

        if (transaction.getPurposeCode() != null) {
            Element categoryPurpose = xml.child(paymentTypeInfo, "CtgyPurp");
            xml.text(
                    categoryPurpose,
                    "Cd",
                    transaction.getPurposeCode().getCode()
            );
        }

        Element amount = xml.text(
                tx,
                "IntrBkSttlmAmt",
                money(transaction.getAmount())
        );

        amount.setAttribute("Ccy", transaction.getCurrency().name());

        xml.text(
                tx,
                "IntrBkSttlmDt",
                transaction.getValueDate().format(DATE)
        );

        xml.text(tx, "ChrgBr", transaction.getChargeBearer().getCode());

        agent(xml, tx, "InstgAgt", properties.getBic());

        agent(
                xml,
                tx,
                "InstdAgt",
                agentOrOwn(transaction.getCreditorAgentBic())
        );

        party(xml, tx, "Dbtr", transaction.getDebtorName());
        account(xml, tx, "DbtrAcct", transaction.getDebtorIban());

        agent(xml, tx, "DbtrAgt", agentOrOwn(transaction.getDebtorAgentBic()));
        agent(xml, tx, "CdtrAgt", agentOrOwn(transaction.getCreditorAgentBic()));

        party(xml, tx, "Cdtr", transaction.getCreditorName());
        account(xml, tx, "CdtrAcct", transaction.getCreditorIban());

        remittance(xml, tx, transaction);

        return xml.toXml();
    }

    // --- shared fragments --------------------------------------------------

    private void agent(XmlBuilder xml, Element parent, String name, String bic) {

        Element agent = xml.child(parent, name);
        Element institution = xml.child(agent, "FinInstnId");
        xml.text(institution, "BICFI", bic);
    }

    private void party(XmlBuilder xml, Element parent, String name, String partyName) {

        Element party = xml.child(parent, name);

        /*
         * Nm is mandatory and capped at 70 characters. Falling back to the bank
         * name would misattribute the payment, so an absent name is a defect
         * worth surfacing rather than papering over.
         */
        xml.text(party, "Nm", requireName(partyName, name));
    }

    private void account(XmlBuilder xml, Element parent, String name, String iban) {

        Element account = xml.child(parent, name);
        Element id = xml.child(account, "Id");
        xml.text(id, "IBAN", iban);
    }

    private void purpose(XmlBuilder xml, Element parent, Transaction transaction) {

        if (transaction.getPurposeCode() == null) {
            return;
        }

        Element purpose = xml.child(parent, "Purp");
        xml.text(purpose, "Cd", transaction.getPurposeCode().getCode());
    }

    private void remittance(XmlBuilder xml, Element parent, Transaction transaction) {

        if (transaction.getRemittanceInformation() == null
                || transaction.getRemittanceInformation().isBlank()) {
            return;
        }

        Element remittance = xml.child(parent, "RmtInf");

        xml.text(
                remittance,
                "Ustrd",
                transaction.getRemittanceInformation()
        );
    }

    /**
     * Renders a monetary amount with the two decimals a currency has.
     *
     * A BigDecimal built from the JSON number 2500 carries scale zero and would
     * serialise as "2500". The schema tolerates that, but an interbank message
     * stating an amount without its minor units reads as a rounding error to
     * anyone auditing it, so the scale is made explicit.
     */
    private String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String agentOrOwn(String bic) {
        return bic != null && !bic.isBlank() ? bic : properties.getBic();
    }

    private String requireName(String value, String element) {

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    element + "/Nm is required by the KIPS pacs.008 schema"
            );
        }

        return value.length() > 70 ? value.substring(0, 70) : value;
    }
}
