package com.bankflow.transactionservice.pacs;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.entity.AuditEventType;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Produces, validates and stores the ISO 20022 messages for a payment.
 *
 * Every payment gets a pacs.008, including on-us ones that never leave the
 * bank: the message records what the instruction actually was, in the scheme's
 * own vocabulary, and costs nothing to keep.
 */
@Service
public class PacsMessageService {

    private static final Logger log =
            LoggerFactory.getLogger(PacsMessageService.class);

    private final Pacs008Builder pacs008Builder;
    private final BusinessApplicationHeaderBuilder headerBuilder;
    private final PacsSchemaValidator validator;
    private final MessageIdGenerator messageIdGenerator;
    private final PacsMessageRepository repository;
    private final AuditService auditService;

    public PacsMessageService(
            Pacs008Builder pacs008Builder,
            BusinessApplicationHeaderBuilder headerBuilder,
            PacsSchemaValidator validator,
            MessageIdGenerator messageIdGenerator,
            PacsMessageRepository repository,
            AuditService auditService) {

        this.pacs008Builder = pacs008Builder;
        this.headerBuilder = headerBuilder;
        this.validator = validator;
        this.messageIdGenerator = messageIdGenerator;
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Builds the pacs.008 for a payment, validates it against the KIPS schema
     * for its rail, and stores it.
     *
     * Validation failure is fatal. A message the scheme would reject is not
     * something to send and then apologise for — better the payment fails here,
     * before any money has moved, than half-settle against a malformed
     * instruction.
     */
    @Transactional
    public PacsMessage generateCreditTransfer(Transaction transaction) {

        String messageId = uniqueMessageId();
        String transmission = buildAndValidate(transaction, messageId);

        PacsMessage message = new PacsMessage();

        message.setMessageId(messageId);
        message.setMessageType(PacsMessageType.PACS_008);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setTransactionReference(transaction.getTransactionReference());
        message.setEndToEndId(transaction.getEndToEndId());
        message.setRawXml(transmission);

        /*
         * The message is built but not yet handed to the scheme, so it stays
         * GENERATED rather than claiming to have been sent.
         */
        message.setStatus(PacsMessageStatus.GENERATED);

        PacsMessage saved = repository.save(message);

        auditService.record(
                AuditEventType.PACS_MESSAGE_GENERATED,
                transaction.getTransactionReference(),
                "PacsMessage",
                messageId,
                "pacs.008 generated for %s and validated against the KIPS %s schema"
                        .formatted(
                                transaction.getPaymentType().getDisplayName(),
                                transaction.getPaymentType().schemaRail()
                        ),
                Map.of(
                        "messageId", messageId,
                        "messageType", PacsMessageType.PACS_008.getIdentifier(),
                        "rail", transaction.getPaymentType().schemaRail(),
                        "businessApplicationHeader",
                        transaction.getPaymentType() == PaymentType.KIPS_RTGS
                )
        );

        return saved;
    }

    /**
     * Builds and validates the message a payment would produce, without
     * storing anything.
     *
     * This is what a teller sees before committing a payment: the actual XML
     * that would go to KIPS, already checked against the scheme's schema, so a
     * rejection surfaces while the instruction can still be corrected.
     */
    public String previewCreditTransfer(Transaction transaction) {
        return buildAndValidate(transaction, messageIdGenerator.newMessageId());
    }

    /**
     * Produces the full transmission for a payment, failing if it would not
     * satisfy the scheme.
     *
     * Validation failure is fatal. A message the scheme would reject is not
     * something to send and then apologise for — better the payment fails
     * before any money has moved than half-settle on a malformed instruction.
     */
    private String buildAndValidate(Transaction transaction, String messageId) {

        String rail = transaction.getPaymentType().schemaRail();

        String documentXml = pacs008Builder.build(transaction, messageId);

        PacsSchemaValidator.ValidationResult documentResult =
                validator.validate(rail, PacsMessageType.PACS_008, documentXml);

        if (!documentResult.valid()) {

            recordFailure(
                    transaction,
                    messageId,
                    "pacs.008 failed KIPS " + rail + " validation",
                    documentResult.errors()
            );

            throw new BusinessException(
                    "Generated pacs.008 does not satisfy the KIPS "
                            + rail + " schema: " + documentResult.describe()
            );
        }

        /*
         * RTGS transmissions are wrapped in a Business Application Header
         * inside the operator's envelope. ACH takes a bare document — Annex D
         * shows no header, so adding one would not match the scheme.
         */
        String transmission = documentXml;

        if (transaction.getPaymentType() == PaymentType.KIPS_RTGS) {

            String headerXml = headerBuilder.buildHeader(
                    messageId,
                    PacsMessageType.PACS_008
            );

            PacsSchemaValidator.ValidationResult headerResult =
                    validator.validateHeader(headerXml);

            if (!headerResult.valid()) {

                recordFailure(
                        transaction,
                        messageId,
                        "Business application header failed head.001 validation",
                        headerResult.errors()
                );

                throw new BusinessException(
                        "Generated business application header is invalid: "
                                + headerResult.describe()
                );
            }

            transmission = headerBuilder.wrap(
                    messageId,
                    PacsMessageType.PACS_008,
                    documentXml
            );
        }

        return transmission;
    }

    @Transactional(readOnly = true)
    public List<PacsMessage> findForTransaction(String transactionReference) {

        return repository.findByTransactionReferenceOrderByCreatedAtAsc(
                transactionReference
        );
    }

    @Transactional(readOnly = true)
    public PacsMessage findByMessageId(String messageId) {

        return repository.findByMessageId(messageId)
                .orElseThrow(() ->
                        new BusinessException(
                                "No message with id " + messageId
                        )
                );
    }

    /**
     * Message identifiers are random, so a collision is vanishingly unlikely
     * but not impossible; retrying is cheaper than relying on the unique
     * constraint to surface it as a failed payment.
     */
    private String uniqueMessageId() {

        for (int attempt = 0; attempt < 5; attempt++) {

            String candidate = messageIdGenerator.newMessageId();

            if (!repository.existsByMessageId(candidate)) {
                return candidate;
            }
        }

        throw new BusinessException(
                "Could not allocate a unique message identifier"
        );
    }

    private void recordFailure(
            Transaction transaction,
            String messageId,
            String summary,
            List<String> errors) {

        log.error("{} [{}]: {}", summary, messageId, errors);

        auditService.record(
                AuditEventType.PACS_MESSAGE_GENERATED,
                transaction.getTransactionReference(),
                "PacsMessage",
                messageId,
                summary,
                Map.of(
                        "messageId", messageId,
                        "errors", errors
                )
        );
    }
}
