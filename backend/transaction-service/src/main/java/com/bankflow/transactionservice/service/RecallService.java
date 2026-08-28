package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.entity.*;
import com.bankflow.transactionservice.pacs.*;
import com.bankflow.transactionservice.recall.*;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asking for a settled payment back, and answering when we are asked.
 *
 * A recall is a request, not an instruction. Sending one moves no money and
 * the other bank may refuse; receiving one obliges nobody, because the money
 * is already in a customer's account and taking it back without their
 * agreement is not ours to do unilaterally. Only an accepted recall moves
 * anything, and it does so as a return.
 *
 * The scheme has no negative answer on ACH — the operator publishes camt.056
 * but no camt.029 — so a refusal is recorded and communicated outside the
 * message flow rather than invented as XML the counterparty could not parse.
 */
@Service
public class RecallService {

    private final RecallRequestRepository recallRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentReturnService returnService;
    private final Camt056Builder camt056Builder;
    private final PacsSchemaValidator validator;
    private final PacsMessageRepository messageRepository;
    private final MessageIdGenerator messageIdGenerator;
    private final AuditService auditService;

    public RecallService(
            RecallRequestRepository recallRepository,
            TransactionRepository transactionRepository,
            PaymentReturnService returnService,
            Camt056Builder camt056Builder,
            PacsSchemaValidator validator,
            PacsMessageRepository messageRepository,
            MessageIdGenerator messageIdGenerator,
            AuditService auditService) {

        this.recallRepository = recallRepository;
        this.transactionRepository = transactionRepository;
        this.returnService = returnService;
        this.camt056Builder = camt056Builder;
        this.validator = validator;
        this.messageRepository = messageRepository;
        this.messageIdGenerator = messageIdGenerator;
        this.auditService = auditService;
    }

    // --- asking ------------------------------------------------------------

    /**
     * Asks the beneficiary's bank to send a payment back.
     *
     * The payment must have gone out and settled: there is nothing to recall
     * from a payment still in flight, which should be left to fail or settle
     * on its own rather than chased with a second message.
     */
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public RecallRequest requestRecall(
            String transactionReference,
            ReasonCode reason,
            String note) {

        Transaction original = transactionRepository
                .findByTransactionReference(transactionReference)
                .orElseThrow(() -> new BusinessException(
                        "No payment with reference " + transactionReference
                ));

        if (original.getDirection() != PaymentDirection.OUTBOUND) {
            throw new BusinessException(
                    ("%s was not sent to another bank. An internal payment is "
                            + "reversed at the counter, not recalled.")
                            .formatted(transactionReference)
            );
        }

        if (original.getStatus() != TransactionStatus.SETTLED) {
            throw new BusinessException(
                    ("Payment %s is %s. Only a settled payment can be recalled.")
                            .formatted(transactionReference, original.getStatus())
            );
        }

        if (!PacsMessageType.CAMT_056.isSupportedOn(
                original.getPaymentType().schemaRail())) {

            throw new BusinessException(
                    ("%s settles gross and final on RTGS, which publishes no "
                            + "cancellation request. Contact the beneficiary "
                            + "bank directly.")
                            .formatted(transactionReference)
            );
        }

        recallRepository
                .findByTransactionReferenceAndStatus(
                        transactionReference, RecallStatus.REQUESTED)
                .ifPresent(open -> {
                    throw new BusinessException(
                            ("A recall on %s is already open and unanswered "
                                    + "(%s).").formatted(
                                    transactionReference,
                                    open.getCancellationId()
                            )
                    );
                });

        ReasonCode chosen = reason == null ? ReasonCode.CUST : reason;

        String messageId = messageIdGenerator.newMessageId();
        String cancellationId = messageIdGenerator.newMessageId();

        String xml = camt056Builder.build(
                messageId, cancellationId, original, chosen, note
        );

        PacsSchemaValidator.ValidationResult validation =
                validator.validate("ach", PacsMessageType.CAMT_056, xml);

        if (!validation.valid()) {
            throw new BusinessException(
                    "The recall message fails the KIPS ACH schema: "
                            + String.join("; ", validation.errors())
            );
        }

        PacsMessage message = new PacsMessage();

        message.setMessageId(messageId);
        message.setMessageType(PacsMessageType.CAMT_056);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setTransactionReference(transactionReference);
        message.setEndToEndId(original.getEndToEndId());
        message.setRawXml(xml);
        message.setStatus(PacsMessageStatus.GENERATED);

        messageRepository.save(message);

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();

        RecallRequest request = new RecallRequest();

        request.setCancellationId(cancellationId);
        request.setTransactionReference(transactionReference);
        request.setDirection(RecallDirection.OUTBOUND);
        request.setStatus(RecallStatus.REQUESTED);
        request.setReasonCode(chosen);
        request.setAdditionalInformation(note);
        request.setRequestedByUsername(actor.username());

        RecallRequest saved = recallRepository.save(request);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("cancellationId", cancellationId);
        details.put("messageId", messageId);
        details.put("reasonCode", chosen.getCode());
        details.put("amount", original.getAmount());
        details.put("currency", original.getCurrency().name());
        details.put("requestedBy", actor.username());

        auditService.record(
                AuditEventType.RECALL_REQUESTED,
                transactionReference,
                "RecallRequest",
                cancellationId,
                ("Recall requested by %s (%s); camt.056 generated. No funds "
                        + "have moved.").formatted(actor.username(), chosen.getCode()),
                details
        );

        return saved;
    }

    // --- being asked -------------------------------------------------------

    /**
     * Another bank wants a payment back.
     *
     * Recorded for a person to decide. The money is sitting in a customer's
     * account and the customer has not agreed to anything, so nothing is taken
     * automatically.
     */
    @Transactional
    public RecallRequest receiveCancellationRequest(ParsedMessage parsed) {

        Transaction original = locate(parsed);

        if (original.getDirection() != PaymentDirection.INBOUND) {
            throw new BusinessException(
                    ("%s was not a payment we received, so it cannot be "
                            + "recalled by the sender.")
                            .formatted(original.getTransactionReference())
            );
        }

        String cancellationId =
                parsed.messageId() != null
                        ? parsed.messageId()
                        : messageIdGenerator.newMessageId();

        var existing = recallRepository.findByCancellationId(cancellationId);

        if (existing.isPresent()) {
            return existing.get();
        }

        RecallRequest request = new RecallRequest();

        request.setCancellationId(cancellationId);
        request.setTransactionReference(original.getTransactionReference());
        request.setDirection(RecallDirection.INBOUND);
        request.setStatus(RecallStatus.REQUESTED);
        request.setReasonCode(
                parsed.reasonCode() == null ? ReasonCode.CUST : parsed.reasonCode()
        );
        request.setAdditionalInformation(parsed.remittance());

        RecallRequest saved = recallRepository.save(request);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("cancellationId", cancellationId);
        details.put("amount", original.getAmount());
        details.put("currency", original.getCurrency().name());
        details.put("beneficiaryAccountId", original.getDestinationAccountId());

        auditService.record(
                AuditEventType.RECALL_RECEIVED,
                original.getTransactionReference(),
                "RecallRequest",
                cancellationId,
                ("The sending bank asked for %s %s back. Awaiting a decision; "
                        + "no funds have moved.").formatted(
                        original.getAmount(), original.getCurrency()
                ),
                details
        );

        return saved;
    }

    /**
     * Agrees to send the money back.
     *
     * The return is what actually moves it, and it will refuse if the
     * beneficiary has since spent the funds — which is the honest outcome, not
     * something to work around by forcing the account negative.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public RecallRequest accept(Long id, String note) {

        RecallRequest request = open(id);

        if (request.getDirection() != RecallDirection.INBOUND) {
            throw new BusinessException(
                    ("Only a request from another bank is ours to accept. %s is "
                            + "ours, and its answer is theirs to give.")
                            .formatted(request.getCancellationId())
            );
        }

        returnService.returnInboundPayment(
                request.getTransactionReference(),
                request.getReasonCode(),
                note
        );

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();

        request.setStatus(RecallStatus.ACCEPTED);
        request.setDecidedByUsername(actor.username());
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(note);

        RecallRequest saved = recallRepository.save(request);

        auditService.record(
                AuditEventType.RECALL_ACCEPTED,
                request.getTransactionReference(),
                "RecallRequest",
                request.getCancellationId(),
                "Recall accepted by %s; the payment was returned".formatted(
                        actor.username()
                ),
                Map.of(
                        "cancellationId", request.getCancellationId(),
                        "decidedBy", actor.username(),
                        "note", note == null ? "" : note
                )
        );

        return saved;
    }

    /**
     * Refuses to send the money back.
     *
     * Nothing is booked and no message goes out, because ACH has no schema for
     * a negative answer. The decision and its reason are recorded so the bank
     * can say why when the counterparty asks.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public RecallRequest reject(Long id, String note) {

        RecallRequest request = open(id);

        if (request.getDirection() != RecallDirection.INBOUND) {
            throw new BusinessException(
                    ("Only a request from another bank is ours to refuse. %s is "
                            + "ours, and its answer is theirs to give.")
                            .formatted(request.getCancellationId())
            );
        }

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();

        request.setStatus(RecallStatus.REJECTED);
        request.setDecidedByUsername(actor.username());
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(note);

        RecallRequest saved = recallRepository.save(request);

        auditService.record(
                AuditEventType.RECALL_REJECTED,
                request.getTransactionReference(),
                "RecallRequest",
                request.getCancellationId(),
                "Recall refused by %s; the payment stands and no funds moved"
                        .formatted(actor.username()),
                Map.of(
                        "cancellationId", request.getCancellationId(),
                        "decidedBy", actor.username(),
                        "note", note == null ? "" : note
                )
        );

        return saved;
    }

    // --- reading -----------------------------------------------------------

    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public List<RecallRequest> awaitingDecision() {
        return recallRepository.findByStatusOrderByCreatedAtAsc(
                RecallStatus.REQUESTED
        );
    }

    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public List<RecallRequest> forTransaction(String transactionReference) {
        return recallRepository
                .findByTransactionReferenceOrderByCreatedAtDesc(
                        transactionReference
                );
    }

    private RecallRequest open(Long id) {

        RecallRequest request = recallRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "No recall request with id " + id
                ));

        if (!request.isOpen()) {
            throw new BusinessException(
                    "That recall has already been %s".formatted(
                            request.getStatus().name().toLowerCase()
                    )
            );
        }

        return request;
    }

    private Transaction locate(ParsedMessage parsed) {

        if (parsed.endToEndId() != null) {

            var byEndToEnd =
                    transactionRepository.findByEndToEndId(parsed.endToEndId());

            if (byEndToEnd.isPresent()) {
                return byEndToEnd.get();
            }
        }

        if (parsed.transactionId() != null) {

            var byReference = transactionRepository
                    .findByTransactionReference(parsed.transactionId());

            if (byReference.isPresent()) {
                return byReference.get();
            }
        }

        throw new BusinessException(
                "The recall references a payment we do not hold: "
                        + parsed.endToEndId()
        );
    }
}
