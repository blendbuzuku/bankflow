package com.bankflow.transactionservice.statement;

import com.bankflow.transactionservice.pacs.KipsProperties;
import com.bankflow.transactionservice.pacs.PacsMessageType;
import com.bankflow.transactionservice.pacs.PacsSchemaValidator;
import com.bankflow.transactionservice.pacs.XmlBuilder;
import com.bankflow.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Renders a statement as camt.053.
 *
 * The ISO shape says the same thing the printed statement does — opening
 * balance, the movements, closing balance — in the form another system can
 * read. Balances are signed by a separate indicator rather than by the number,
 * so a negative balance is a positive amount marked DBIT; writing a minus sign
 * into the amount would fail the schema.
 */
@Component
public class Camt053Builder {

    private static final String RAIL = "rtgs";

    /** Opening and closing booked balances, per the ISO balance type list. */
    private static final String OPENING_BOOKED = "OPBD";
    private static final String CLOSING_BOOKED = "CLBD";

    private static final int DESCRIPTION_MAX = 70;
    private static final int REFERENCE_MAX = 35;

    /**
     * The character set the restricted FIN text types allow. Anything outside
     * it fails the schema, so text has to be folded into it rather than
     * trusted through.
     */
    private static final Pattern PERMITTED =
            Pattern.compile("[0-9a-zA-Z/\\-?:().,'+ ]");

    private final KipsProperties properties;
    private final PacsSchemaValidator validator;

    public Camt053Builder(
            KipsProperties properties,
            PacsSchemaValidator validator) {

        this.properties = properties;
        this.validator = validator;
    }

    public String build(String messageId, Statement statement) {

        XmlBuilder xml = XmlBuilder.create();

        Element document = xml.root(
                PacsMessageType.CAMT_053.getNamespace(RAIL), "Document");
        Element body = xml.child(document, "BkToCstmrStmt");

        // --- group header ---

        Element groupHeader = xml.child(body, "GrpHdr");

        xml.text(groupHeader, "MsgId", messageId);

        xml.text(
                groupHeader,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        // --- the statement ---

        Element stmt = xml.child(body, "Stmt");

        xml.text(stmt, "Id", statement.statementId());

        /*
         * One page, and it is the last. Pagination is mandatory even for a
         * statement that never splits.
         */
        Element pagination = xml.child(stmt, "StmtPgntn");
        xml.text(pagination, "PgNb", "1");
        xml.text(pagination, "LastPgInd", "true");

        xml.text(
                stmt,
                "CreDtTm",
                XmlBuilder.formatDateTime(OffsetDateTime.now())
        );

        /*
         * The schema's date-time pattern demands an explicit +HH:MM offset, so
         * a UTC "Z" suffix is rejected. The period runs from the start of the
         * first day to the last moment of the last.
         */
        Element period = xml.child(stmt, "FrToDt");

        xml.text(period, "FrDtTm", XmlBuilder.formatDateTime(
                statement.from().atStartOfDay().atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()));

        xml.text(period, "ToDtTm", XmlBuilder.formatDateTime(
                statement.to().atTime(23, 59, 59).atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()));

        // --- whose account ---

        Element account = xml.child(stmt, "Acct");

        Element accountId = xml.child(account, "Id");
        xml.text(accountId, "IBAN", statement.iban());

        xml.text(account, "Ccy", statement.currency().name());

        Element owner = xml.child(account, "Ownr");
        xml.optional(owner, "Nm", statement.accountName());

        Element servicer = xml.child(account, "Svcr");
        Element institution = xml.child(servicer, "FinInstnId");
        xml.text(institution, "BICFI", properties.getBic());

        // --- balances ---

        String currency = statement.currency().name();

        balance(xml, stmt, OPENING_BOOKED, statement.openingBalance(),
                currency, statement.from());

        balance(xml, stmt, CLOSING_BOOKED, statement.closingBalance(),
                currency, statement.to());

        // --- totals ---

        Element summary = xml.child(stmt, "TxsSummry");

        Element total = xml.child(summary, "TtlNtries");
        xml.text(total, "NbOfNtries", String.valueOf(statement.entryCount()));

        Element totalCredits = xml.child(summary, "TtlCdtNtries");
        xml.text(totalCredits, "Sum", plain(statement.totalCredits()));

        Element totalDebits = xml.child(summary, "TtlDbtNtries");
        xml.text(totalDebits, "Sum", plain(statement.totalDebits()));

        // --- the movements ---

        for (Statement.StatementEntry line : statement.entries()) {
            entry(xml, stmt, line);
        }

        String result = xml.toXml();

        /*
         * Checked against the operator's schema like every other message this
         * bank produces. A statement that would not satisfy it is worse than
         * useless: the customer's own system would reject it, and they would
         * find out later than we could have.
         */
        PacsSchemaValidator.ValidationResult validation =
                validator.validate(RAIL, PacsMessageType.CAMT_053, result);

        if (!validation.valid()) {
            throw new BusinessException(
                    "The statement fails the camt.053 schema: "
                            + String.join("; ", validation.errors())
            );
        }

        return result;
    }

    private void balance(
            XmlBuilder xml,
            Element stmt,
            String code,
            BigDecimal amount,
            String currency,
            java.time.LocalDate date) {

        Element balance = xml.child(stmt, "Bal");

        Element type = xml.child(balance, "Tp");
        Element codeOrProprietary = xml.child(type, "CdOrPrtry");
        xml.text(codeOrProprietary, "Cd", code);

        Element value = xml.text(balance, "Amt", plain(amount.abs()));
        value.setAttribute("Ccy", currency);

        /*
         * The sign lives in the indicator, not the number. A balance the
         * customer is owed is CRDT; one they owe is DBIT.
         */
        xml.text(
                balance,
                "CdtDbtInd",
                amount.signum() < 0 ? "DBIT" : "CRDT"
        );

        Element when = xml.child(balance, "Dt");
        xml.text(when, "Dt", date.toString());
    }

    private void entry(
            XmlBuilder xml, Element stmt, Statement.StatementEntry line) {

        Element entry = xml.child(stmt, "Ntry");

        xml.optional(entry, "NtryRef", restricted(line.entryReference(), REFERENCE_MAX));

        Element amount = xml.text(entry, "Amt", plain(line.amount()));
        amount.setAttribute("Ccy", line.currency().name());

        xml.text(entry, "CdtDbtInd", line.creditDebitIndicator());

        /*
         * Everything on a statement has been booked. Pending movements do not
         * appear here — that is what camt.052 is for.
         */
        Element status = xml.child(entry, "Sts");
        xml.text(status, "Cd", "BOOK");

        /*
         * The two dates are not interchangeable here: the restricted booking
         * choice admits only DtTm and the value choice only Dt, so a date in
         * the wrong one fails the schema even though both describe a day.
         */
        Element bookingDate = xml.child(entry, "BookgDt");

        xml.text(bookingDate, "DtTm", XmlBuilder.formatDateTime(
                line.bookingDate().atStartOfDay().atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()));

        if (line.valueDate() != null) {
            Element valueDate = xml.child(entry, "ValDt");
            xml.text(valueDate, "Dt", line.valueDate().toString());
        }

        /*
         * The scheme has no domain code for these movements, so the bank's own
         * reference is carried as a proprietary code rather than forcing an ISO
         * family that would misdescribe them.
         */
        Element transactionCode = xml.child(entry, "BkTxCd");
        Element proprietary = xml.child(transactionCode, "Prtry");

        xml.text(
                proprietary,
                "Cd",
                restricted(
                        line.transactionReference() == null
                                ? "LEDGER"
                                : line.transactionReference(),
                        REFERENCE_MAX)
        );

        xml.text(proprietary, "Issr", restricted(properties.getBic(), REFERENCE_MAX));

        xml.optional(
                entry, "AddtlNtryInf", restricted(line.description(), DESCRIPTION_MAX));
    }

    private String plain(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Folds text into what the scheme's restricted FIN types accept.
     *
     * Kosovo names carry ë and ç, and the permitted set has neither, so
     * diacritics are stripped to their base letter rather than dropped —
     * Gjocaj is recognisable where Gjoaj is not. Anything still outside the
     * set becomes a space, which is why the separator is a plain hyphen and
     * not the dash the screen uses.
     *
     * Returns null rather than an empty string: these fields have a minimum
     * length of one, so an empty value is a schema failure while an absent
     * optional element is fine.
     */
    private String restricted(String value, int max) {

        if (value == null) {
            return null;
        }

        String folded = Normalizer
                .normalize(value.replace('—', '-').replace('–', '-'),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        StringBuilder kept = new StringBuilder(folded.length());

        for (int i = 0; i < folded.length(); i++) {

            String character = String.valueOf(folded.charAt(i));

            kept.append(PERMITTED.matcher(character).matches() ? character : " ");
        }

        String cleaned = kept.toString().replaceAll("\\s+", " ").trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max).trim();
    }

}
