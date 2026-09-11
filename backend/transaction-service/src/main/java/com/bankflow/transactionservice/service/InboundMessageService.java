package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.entity.*;
import com.bankflow.transactionservice.pacs.*;
import com.bankflow.transactionservice.recall.RecallRequest;
import com.bankflow.transactionservice.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles messages arriving from KIPS.
 *
 * Two things can turn up: a payment somebody is sending us, and an answer about
 * a payment we sent. Both are stored verbatim before anything is acted on, so
 * the record of what was received survives whatever happens next.
 *
 * A payment we cannot apply is not an error condition. Naming an account we do
 * not hold is an ordinary, expected event, and the correct response is a
 * rejection carrying AC01 — not a stack trace.
 */
@Service
public class InboundMessageService {

    private static final Logger log =
            LoggerFactory.getLogger(InboundMessageService.class);

    private final BusinessCalendar businessCalendar;
    private final PacsMessageParser parser;
    private final PacsMessageRepository messageRepository;
    private final PacsMessageService pacsMessageService;
    private final Pacs002Builder pacs002Builder;
    private final PacsSchemaValidator validator;
    private final MessageIdGenerator messageIdGenerator;
    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final LedgerPoster ledgerPoster;
    private final SuspenseAccountResolver suspenseAccounts;
    private final AuditService auditService;
    private final PaymentReturnService paymentReturnService;
    private final RecallService recallService;

    public InboundMessageService(
            PacsMessageParser parser,
            PacsMessageRepository messageRepository,
            PacsMessageService pacsMessageService,
            Pacs002Builder pacs002Builder,
            PacsSchemaValidator validator,
            MessageIdGenerator messageIdGenerator,
            TransactionRepository transactionRepository,
            AccountClient accountClient,
            LedgerPoster ledgerPoster,
            SuspenseAccountResolver suspenseAccounts,
            AuditService auditService,
            PaymentReturnService paymentReturnService,
            RecallService recallService,
            BusinessCalendar businessCalendar) {

        this.parser = parser;
        this.messageRepository = messageRepository;
        this.pacsMessageService = pacsMessageService;
        this.pacs002Builder = pacs002Builder;
        this.validator = validator;
        this.messageIdGenerator = messageIdGenerator;
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
        this.ledgerPoster = ledgerPoster;
        this.suspenseAccounts = suspenseAccounts;
        this.auditService = auditService;
        this.paymentReturnService = paymentReturnService;
        this.recallService = recallService;
        this.businessCalendar = businessCalendar;
    }

    /**
     * The participant endpoint's entry point.
     *
     * @return the XML to return to the sender — a pacs.002 for a credit
     *         transfer, or empty for a status report, which is not itself
     *         answered.
     */
    @Transactional
    public String receive(String xml) {

        ParsedMessage parsed = parser.parse(xml);

        store(parsed, xml);

        /*
         * Check the message against the scheme's own schema before acting on
         * it. FF01 exists precisely for this, and refusing a malformed message
         * up front is far safer than discovering halfway through booking that a
         * field we relied on was never valid.
         */
        ReasonCode schemaFault = validateAgainstSchema(parsed, xml);

        if (schemaFault != null) {

            /*
             * A status report is an answer, and answers are not themselves
             * answered. A malformed one is recorded and dropped rather than
             * replied to, which would invite a loop.
             */
            if (parsed.isAnswer()) {

                auditService.record(
                        AuditEventType.PACS_MESSAGE_RECEIVED,
                        null,
                        "PacsMessage",
                        parsed.messageId(),
                        "Discarded malformed %s: fails schema validation"
                                .formatted(parsed.messageType().getIdentifier()),
                        Map.of("messageId", String.valueOf(parsed.messageId()))
                );

                return "";
            }

            return reject(parsed, schemaFault);
        }

        if (parsed.isCreditTransfer()) {
            return handleCreditTransfer(parsed);
        }

        /*
         * Each of these answers or asks about a payment we already hold, and
         * is filed under it once that payment has been found. Stored on
         * arrival with no reference, they otherwise belonged to nothing: the
         * payment's own screen listed what we sent and never what came back,
         * so the status report that settled it and the return that undid it
         * could not be seen from the payment at all.
         */
        if (parsed.isStatusReport()) {
            Transaction answered = handleStatusReport(parsed);
            linkToTransaction(parsed, answered.getTransactionReference());
            return "";
        }

        if (parsed.isReturn()) {
            Transaction returned = paymentReturnService.receiveReturn(parsed);
            linkToTransaction(parsed, returned.getTransactionReference());
            return "";
        }

        /*
         * A recall is a question, and the answer is a person's to give. It is
         * recorded for the queue rather than answered here.
         */
        if (parsed.isCancellationRequest()) {
            RecallRequest recall = recallService.receiveCancellationRequest(parsed);
            linkToTransaction(parsed, recall.getTransactionReference());
            return "";
        }

        throw new BusinessException(
                "Unsupported inbound message type: " + parsed.messageType()
        );
    }

    /**
     * Validates the parts of a transmission that have a published schema.
     *
     * @return {@link ReasonCode#FF01} when the message does not satisfy its
     *         schema, or null when it does
     */
    private ReasonCode validateAgainstSchema(ParsedMessage parsed, String xml) {

        PacsMessageParser.InboundEnvelope envelope;

        try {
            envelope = parser.split(xml);
        } catch (BusinessException cannotSplit) {

            log.info(
                    "Inbound message {} could not be split for validation: {}",
                    parsed.messageId(),
                    cannotSplit.getMessage()
            );

            return ReasonCode.FF01;
        }

        PacsSchemaValidator.ValidationResult document =
                validator.validate(
                        envelope.rail(),
                        parsed.messageType(),
                        envelope.documentXml()
                );

        if (!document.valid()) {

            log.info(
                    "Inbound {} {} fails the KIPS {} schema: {}",
                    parsed.messageType().getIdentifier(),
                    parsed.messageId(),
                    envelope.rail(),
                    document.describe()
            );

            auditService.record(
                    AuditEventType.PACS_MESSAGE_RECEIVED,
                    null,
                    "PacsMessage",
                    parsed.messageId(),
                    "Inbound %s fails KIPS %s schema validation".formatted(
                            parsed.messageType().getIdentifier(),
                            envelope.rail()
                    ),
                    Map.of(
                            "messageId", String.valueOf(parsed.messageId()),
                            "rail", envelope.rail(),
                            "errors", document.errors()
                    )
            );

            return ReasonCode.FF01;
        }

        if (!envelope.hasApplicationHeader()) {
            return null;
        }

        PacsSchemaValidator.ValidationResult header =
                validator.validateHeader(envelope.headerXml());

        if (!header.valid()) {

            log.info(
                    "Inbound business application header {} is invalid: {}",
                    parsed.messageId(),
                    header.describe()
            );

            auditService.record(
                    AuditEventType.PACS_MESSAGE_RECEIVED,
                    null,
                    "PacsMessage",
                    parsed.messageId(),
                    "Inbound business application header fails head.001 validation",
                    Map.of(
                            "messageId", String.valueOf(parsed.messageId()),
                            "errors", header.errors()
                    )
            );

            return ReasonCode.FF01;
        }

        return null;
    }

    // --- inbound payment ---------------------------------------------------

    private String handleCreditTransfer(ParsedMessage parsed) {

        AccountResponse beneficiary =
                parsed.creditorIban() == null
                        ? null
                        : accountClient.findByIban(parsed.creditorIban());

        ReasonCode refusal = screen(parsed, beneficiary);

        if (refusal != null) {
            return reject(parsed, refusal);
        }

        Currency currency = Currency.valueOf(parsed.currency());

        Transaction transaction = new Transaction();

        transaction.setTransactionReference(newReference());
        transaction.setEndToEndId(parsed.endToEndId());

        transaction.setInstructionId(
                parsed.transactionId() != null
                        ? parsed.transactionId()
                        : parsed.messageId()
        );

        transaction.setTransactionType(TransactionType.TRANSFER);

        /*
         * We are the creditor agent, so this arrived over a KIPS rail. Which
         * one is not stated in the message itself; ACH is the retail default.
         */
        transaction.setPaymentType(PaymentType.KIPS_ACH);
        transaction.setDirection(PaymentDirection.INBOUND);
        transaction.setServiceLevel(PaymentType.KIPS_ACH.getServiceLevel());
        transaction.setChargeBearer(ChargeBearer.SLEV);

        transaction.setSourceAccountId(null);
        transaction.setDestinationAccountId(beneficiary.id());

        transaction.setAmount(parsed.amount());
        transaction.setCurrency(currency);

        transaction.setDebtorName(parsed.debtorName());
        transaction.setDebtorIban(parsed.debtorIban());
        transaction.setDebtorAgentBic(parsed.debtorAgentBic());

        transaction.setCreditorName(parsed.creditorName());
        transaction.setCreditorIban(parsed.creditorIban());
        transaction.setCreditorAgentBic(parsed.creditorAgentBic());

        transaction.setRemittanceInformation(parsed.remittance());

        LocalDate today = businessCalendar.today();

        transaction.setBookingDate(today);
        transaction.setValueDate(today);
        transaction.setStatus(TransactionStatus.PROCESSING);

        transaction = transactionRepository.save(transaction);

        /*
         * Link the message we stored on the way in to the payment it just
         * created. It arrives before the transaction exists, so it has to be
         * attached afterwards — without this the incoming pacs.008 is filed
         * under no payment at all, and the customer who received the money
         * cannot see what was sent for it.
         */
        linkToTransaction(parsed, transaction.getTransactionReference());

        /*
         * Build the acknowledgement before booking anything.
         *
         * Balances live in another service and commit independently of this
         * transaction, so a failure after booking leaves money moved with no
         * ledger record behind it — the rollback here cannot reach across the
         * service boundary. Producing the answer first means a message we
         * cannot construct fails while nothing has moved.
         */
        String acknowledgement = statusReport(
                parsed,
                TransactionStatusCode.ACSC,
                null,
                transaction.getTransactionReference(),
                false
        );

        auditService.record(
                AuditEventType.PACS_MESSAGE_RECEIVED,
                transaction.getTransactionReference(),
                "PacsMessage",
                parsed.messageId(),
                "Inbound pacs.008 accepted for %s %s to %s".formatted(
                        parsed.amount(), currency, parsed.creditorIban()
                ),
                Map.of(
                        "messageId", parsed.messageId(),
                        "debtorIban", String.valueOf(parsed.debtorIban()),
                        "creditorIban", parsed.creditorIban(),
                        "beneficiaryAccountId", beneficiary.id()
                )
        );

        /*
         * Arriving money is taken in and then released, mirroring the outbound
         * direction: the scheme delivers it into suspense, and suspense pays
         * it out to the beneficiary.
         *
         * The first pair is the one that used to be missing. Crediting the
         * beneficiary straight out of suspense balanced the entry but left
         * suspense a little further below zero on every payment we received,
         * and never showed the position at the central bank rising to meet it
         * — so the account that says what is in flight answered nothing, and
         * the account that says what the scheme owes us was silent.
         */
        Long suspenseId = suspenseAccounts.suspenseAccountId(currency);
        String reference = transaction.getTransactionReference();

        ledgerPoster.debit(
                transaction,
                suspenseAccounts.settlementAccountId(currency),
                parsed.amount(),
                reference + "-IN-SETTLEMENT"
        );

        ledgerPoster.credit(
                transaction,
                suspenseId,
                parsed.amount(),
                reference + "-IN-SUSPENSE"
        );

        ledgerPoster.debit(
                transaction,
                suspenseId,
                parsed.amount(),
                reference + "-SUSPENSE"
        );

        ledgerPoster.credit(
                transaction,
                beneficiary.id(),
                parsed.amount(),
                reference + "-CREDIT"
        );

        transaction.setStatus(TransactionStatus.SETTLED);
        transaction = transactionRepository.save(transaction);

        Map<String, Object> posting = new LinkedHashMap<>();

        posting.put("settlementDebited", parsed.amount());
        posting.put("suspenseCredited", parsed.amount());
        posting.put("suspenseDebited", parsed.amount());
        posting.put("beneficiaryCredited", parsed.amount());
        posting.put("currency", currency.name());
        posting.put("net", BigDecimal.ZERO);

        auditService.record(
                AuditEventType.LEDGER_ENTRY_POSTED,
                reference,
                "LedgerEntry",
                reference,
                ("Inbound legs posted: DR settlement %s / CR suspense %s, "
                        + "DR suspense %s / CR beneficiary %s, net 0").formatted(
                        parsed.amount(), parsed.amount(),
                        parsed.amount(), parsed.amount()),
                posting
        );

        auditService.record(
                AuditEventType.PAYMENT_SETTLED,
                reference,
                "Transaction",
                reference,
                "Inbound payment of %s %s settled to %s".formatted(
                        parsed.amount(), currency, parsed.creditorIban()
                ),
                Map.of("status", TransactionStatus.SETTLED.name())
        );

        return acknowledgement;
    }

    /**
     * Decides whether an inbound payment can be applied.
     *
     * @return the reason to refuse it, or null when it may proceed
     */
    private ReasonCode screen(ParsedMessage parsed, AccountResponse beneficiary) {

        if (parsed.amount() == null
                || parsed.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return ReasonCode.AM02;
        }

        if (parsed.currency() == null) {
            return ReasonCode.AM03;
        }

        try {
            Currency.valueOf(parsed.currency());
        } catch (IllegalArgumentException unsupported) {
            return ReasonCode.AM03;
        }

        if (beneficiary == null) {
            return ReasonCode.AC01;
        }

        if (!"ACTIVE".equals(beneficiary.status())) {

            /*
             * A closed account and a blocked one are different refusals, and a
             * counterparty acts on them differently.
             */
            return "CLOSED".equals(beneficiary.status())
                    ? ReasonCode.AC04
                    : ReasonCode.AC06;
        }

        if (!beneficiary.currency().equals(parsed.currency())) {
            return ReasonCode.AM03;
        }

        if (parsed.endToEndId() != null
                && transactionRepository.existsByEndToEndId(parsed.endToEndId())) {
            return ReasonCode.AM05;
        }

        return null;
    }

    private String reject(ParsedMessage parsed, ReasonCode reason) {

        log.info(
                "Rejecting inbound payment {} with {}: {}",
                parsed.messageId(),
                reason.getCode(),
                reason.getDescription()
        );

        auditService.record(
                AuditEventType.PAYMENT_REJECTED,
                null,
                "PacsMessage",
                parsed.messageId(),
                "Inbound pacs.008 rejected with %s - %s".formatted(
                        reason.getCode(), reason.getDescription()
                ),
                Map.of(
                        "messageId", parsed.messageId(),
                        "reasonCode", reason.getCode(),
                        "creditorIban", String.valueOf(parsed.creditorIban()),
                        "amount", String.valueOf(parsed.amount())
                )
        );

        return statusReport(
                parsed,
                TransactionStatusCode.RJCT,
                reason,
                null
        );
    }

    private String statusReport(
            ParsedMessage parsed,
            TransactionStatusCode status,
            ReasonCode reason,
            String transactionReference) {

        return statusReport(parsed, status, reason, transactionReference, true);
    }

    private String statusReport(
            ParsedMessage parsed,
            TransactionStatusCode status,
            ReasonCode reason,
            String transactionReference,
            boolean markSent) {

        String messageId = messageIdGenerator.newMessageId();

        /*
         * A UETR is a 36-character UUID and OrgnlTxId is Max35Text, so the two
         * are not interchangeable. RTGS carries it in OrgnlUETR; on ACH there is
         * nowhere to put one, and none is sent.
         */
        boolean isUetr = isUuid(parsed.transactionId());
        String rail = isUetr ? "rtgs" : "ach";

        String originalTxId =
                isUetr
                        ? parsed.messageId()
                        : parsed.transactionId() != null
                                ? parsed.transactionId()
                                : parsed.messageId();

        String xml = pacs002Builder.build(
                messageId,
                parsed.messageId(),
                parsed.endToEndId(),
                originalTxId,
                isUetr ? parsed.transactionId() : null,
                status,
                reason,
                rail
        );

        PacsSchemaValidator.ValidationResult result =
                validator.validate(rail, PacsMessageType.PACS_002, xml);

        if (!result.valid()) {
            throw new BusinessException(
                    "Generated pacs.002 does not satisfy the KIPS schema: "
                            + result.describe()
            );
        }

        PacsMessage message = new PacsMessage();

        message.setMessageId(messageId);
        message.setMessageType(PacsMessageType.PACS_002);
        message.setDirection(MessageDirection.OUTBOUND);
        /*
         * An acknowledgement built ahead of booking is not dispatched until the
         * money has actually moved, so it stays GENERATED until then.
         */
        message.setStatus(
                markSent
                        ? PacsMessageStatus.SENT
                        : PacsMessageStatus.GENERATED
        );

        message.setTransactionReference(transactionReference);
        message.setEndToEndId(parsed.endToEndId());
        message.setRelatedMessageId(parsed.messageId());
        message.setStatusCode(status.getCode());
        message.setReasonCode(reason == null ? null : reason.getCode());
        message.setRawXml(xml);

        messageRepository.save(message);

        auditService.record(
                AuditEventType.PACS_MESSAGE_GENERATED,
                transactionReference,
                "PacsMessage",
                messageId,
                "pacs.002 %s returned answering %s".formatted(
                        status.getCode(), parsed.messageId()
                ),
                Map.of(
                        "messageId", messageId,
                        "status", status.getCode(),
                        "reasonCode",
                        reason == null ? "none" : reason.getCode(),
                        "answers", parsed.messageId()
                )
        );

        return xml;
    }

    // --- status report about a payment we sent -----------------------------

    /** @return the payment the report answers, so the report can be filed under it */
    private Transaction handleStatusReport(ParsedMessage parsed) {

        Transaction transaction =
                transactionRepository.findByEndToEndId(parsed.endToEndId())
                        .orElseThrow(() -> new BusinessException(
                                "Status report references an unknown payment: "
                                        + parsed.endToEndId()
                        ));

        if (transaction.getStatus() != TransactionStatus.SENT) {
            throw new BusinessException(
                    "Payment %s is %s and cannot accept a status report".formatted(
                            transaction.getTransactionReference(),
                            transaction.getStatus()
                    )
            );
        }

        auditService.record(
                AuditEventType.PACS_MESSAGE_RECEIVED,
                transaction.getTransactionReference(),
                "PacsMessage",
                parsed.messageId(),
                "pacs.002 %s received for %s".formatted(
                        parsed.status(),
                        transaction.getTransactionReference()
                ),
                Map.of(
                        "messageId", String.valueOf(parsed.messageId()),
                        "status", String.valueOf(parsed.status()),
                        "reasonCode",
                        parsed.reasonCode() == null
                                ? "none"
                                : parsed.reasonCode().getCode()
                )
        );

        if (parsed.status() == TransactionStatusCode.RJCT) {
            unwind(transaction, parsed.reasonCode());
            return transaction;
        }

        if (parsed.status() != null && parsed.status().isFinal()) {
            settle(transaction);
        }

        /*
         * A non-final acknowledgement (ACTC, ACCP) means the counterparty has
         * the message but has not settled it. The payment stays in flight.
         */
        return transaction;
    }

    private void settle(Transaction transaction) {

        /*
         * Settlement is a booking, not just a status.
         *
         * Sending the payment credited suspense: gone from the customer,
         * arrived nowhere. The scheme confirming it is the moment it arrives
         * -- so suspense is cleared and the bank's position at the central
         * bank falls by the same amount.
         *
         * This used to change the status and book nothing, on the reasoning
         * that the suspense credit already recorded the outflow. It did, and
         * it then recorded it for ever: suspense never emptied, and a balance
         * meant to say how much is in flight ended up holding every payment
         * the bank had ever completed.
         */
        Currency currency = transaction.getCurrency();
        String reference = transaction.getTransactionReference();

        ledgerPoster.debit(
                transaction,
                suspenseAccounts.suspenseAccountId(currency),
                transaction.getAmount(),
                reference + "-STL-SUSPENSE"
        );

        ledgerPoster.credit(
                transaction,
                suspenseAccounts.settlementAccountId(currency),
                transaction.getAmount(),
                reference + "-STL"
        );

        transaction.setStatus(TransactionStatus.SETTLED);
        transactionRepository.save(transaction);

        auditService.record(
                AuditEventType.PAYMENT_SETTLED,
                transaction.getTransactionReference(),
                "Transaction",
                transaction.getTransactionReference(),
                "Counterparty confirmed settlement of %s %s".formatted(
                        transaction.getAmount(),
                        transaction.getCurrency()
                ),
                Map.of("status", TransactionStatus.SETTLED.name())
        );
    }

    /**
     * Puts the customer back where they were.
     *
     * The fee goes back too: we do not charge for a payment that never
     * happened.
     */
    private void unwind(Transaction transaction, ReasonCode reason) {

        String reference = transaction.getTransactionReference();
        Currency currency = transaction.getCurrency();
        BigDecimal amount = transaction.getAmount();

        BigDecimal fee =
                transaction.getDebtorFeeAmount() != null
                        ? transaction.getDebtorFeeAmount()
                        : BigDecimal.ZERO;

        ledgerPoster.debit(
                transaction,
                suspenseAccounts.suspenseAccountId(currency),
                amount,
                reference + "-RETURN-SUSPENSE"
        );

        if (fee.compareTo(BigDecimal.ZERO) > 0) {

            ledgerPoster.debit(
                    transaction,
                    suspenseAccounts.incomeAccountId(currency),
                    fee,
                    reference + "-RETURN-FEE"
            );
        }

        /*
         * Given back the same way it was taken: the payment and the charge as
         * separate lines, so a customer reading their statement sees the
         * refund of each rather than one figure that matches neither.
         */
        ledgerPoster.credit(
                transaction,
                transaction.getSourceAccountId(),
                amount,
                reference + "-RETURN-CREDIT"
        );

        if (fee.compareTo(BigDecimal.ZERO) > 0) {

            ledgerPoster.credit(
                    transaction,
                    transaction.getSourceAccountId(),
                    fee,
                    reference + "-RETURN-FEE-CREDIT"
            );
        }

        transaction.setStatus(TransactionStatus.REJECTED);

        transaction.setReasonCode(
                reason == null ? ReasonCode.MS03.getCode() : reason.getCode()
        );

        transaction.setRejectionReason(
                reason == null
                        ? "Rejected by counterparty"
                        : reason.getDescription()
        );

        transactionRepository.save(transaction);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("reasonCode", transaction.getReasonCode());
        details.put("amountReturned", amount);
        details.put("feeRefunded", fee);
        details.put("creditedTo", transaction.getSourceAccountId());

        auditService.record(
                AuditEventType.PAYMENT_REJECTED,
                reference,
                "Transaction",
                reference,
                "Rejected by counterparty (%s); %s %s returned to the debtor"
                        .formatted(
                                transaction.getReasonCode(),
                                amount.add(fee),
                                currency
                        ),
                details
        );
    }

    // --- storage -----------------------------------------------------------

    /**
     * Files an inbound message under the payment it belongs to.
     *
     * Silent when the message is not there: it may have been a replay, already
     * stored and already linked, and failing the whole payment over a
     * bookkeeping detail would be the wrong trade.
     */
    private void linkToTransaction(ParsedMessage parsed, String reference) {

        if (parsed.messageId() == null) {
            return;
        }

        messageRepository.findByMessageId(parsed.messageId())
                .filter(message -> message.getTransactionReference() == null)
                .ifPresent(message -> {
                    message.setTransactionReference(reference);
                    messageRepository.save(message);
                });
    }

    private void store(ParsedMessage parsed, String xml) {

        String messageId =
                parsed.messageId() != null
                        ? parsed.messageId()
                        : "UNKNOWN-" + UUID.randomUUID();

        /*
         * A replayed message is stored once. Re-storing it would fail the
         * unique constraint and mask the real duplicate handling, which belongs
         * to the screening step.
         */
        if (messageRepository.existsByMessageId(messageId)) {
            return;
        }

        PacsMessage message = new PacsMessage();

        message.setMessageId(messageId);
        message.setMessageType(parsed.messageType());
        message.setDirection(MessageDirection.INBOUND);
        message.setStatus(PacsMessageStatus.GENERATED);
        message.setEndToEndId(parsed.endToEndId());
        message.setRelatedMessageId(parsed.originalMessageId());

        message.setStatusCode(
                parsed.status() == null ? null : parsed.status().getCode()
        );

        message.setReasonCode(
                parsed.reasonCode() == null ? null : parsed.reasonCode().getCode()
        );

        message.setRawXml(xml);

        messageRepository.save(message);
    }

    private String newReference() {

        return "TXN-" + LocalDate.now() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * A UETR is always a UUIDv4, which is how an RTGS transaction identifier is
     * told apart from an ACH TxId.
     */
    private boolean isUuid(String value) {

        return value != null
                && value.length() == 36
                && value.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                                + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
