package com.bankflow.transactionservice.controller;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.kips.SchemeAction;
import com.bankflow.transactionservice.kips.SchemeQueueService;
import com.bankflow.transactionservice.pacs.*;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.service.InboundMessageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The KIPS participant interface.
 *
 * Everything the scheme sends us arrives here as XML and is answered in kind.
 */
@RestController
@RequestMapping("/api/kips")
public class KipsController {

    private final InboundMessageService inboundMessageService;
    private final TransactionRepository transactionRepository;
    private final PacsMessageRepository messageRepository;
    private final Pacs002Builder pacs002Builder;
    private final Pacs004Builder pacs004Builder;
    private final MessageIdGenerator messageIdGenerator;
    private final SchemeQueueService schemeQueue;
    private final BusinessApplicationHeaderBuilder headerBuilder;

    public KipsController(
            InboundMessageService inboundMessageService,
            TransactionRepository transactionRepository,
            PacsMessageRepository messageRepository,
            Pacs002Builder pacs002Builder,
            Pacs004Builder pacs004Builder,
            MessageIdGenerator messageIdGenerator,
            SchemeQueueService schemeQueue,
            BusinessApplicationHeaderBuilder headerBuilder) {

        this.schemeQueue = schemeQueue;
        this.headerBuilder = headerBuilder;

        this.inboundMessageService = inboundMessageService;
        this.transactionRepository = transactionRepository;
        this.messageRepository = messageRepository;
        this.pacs002Builder = pacs002Builder;
        this.pacs004Builder = pacs004Builder;
        this.messageIdGenerator = messageIdGenerator;
    }

    /**
     * What the scheme still has to answer.
     *
     * Read-only, and offered to anyone who may act on it, so the console can
     * show the queue without also being the thing that changes it.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @GetMapping("/queue")
    public ResponseEntity<Map<String, List<SchemeAction>>> queue() {

        return ResponseEntity.ok(Map.of(
                "awaitingStatus", schemeQueue.awaitingStatus(),
                "returnable", schemeQueue.returnable()
        ));
    }

    /**
     * Receives a message from the scheme.
     *
     * Answers a credit transfer with a pacs.002; a status report gets an empty
     * body, since an answer is not itself answered.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN', 'TRANSACTION_SERVICE')")
    @PostMapping(
            value = "/inbound",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> inbound(@RequestBody String xml) {
        return ResponseEntity.ok(inboundMessageService.receive(xml));
    }

    /**
     * Stands in for the counterparty bank while there is no real KIPS link.
     *
     * Builds a genuine pacs.002 for a payment we sent and feeds it through the
     * same inbound path a real one would take, so the simulation exercises the
     * production code rather than a shortcut around it.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @PostMapping(
            value = "/simulate/status/{transactionReference}",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> simulateStatus(
            @PathVariable String transactionReference,
            @RequestParam TransactionStatusCode status,
            @RequestParam(required = false) ReasonCode reason) {

        Transaction transaction =
                transactionRepository
                        .findByTransactionReference(transactionReference)
                        .orElseThrow(() -> new BusinessException(
                                "Unknown payment: " + transactionReference
                        ));

        PacsMessage original =
                messageRepository
                        .findByTransactionReferenceOrderByCreatedAtAsc(
                                transactionReference)
                        .stream()
                        .filter(message ->
                                message.getMessageType()
                                        == PacsMessageType.PACS_008)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "No pacs.008 was sent for " + transactionReference
                        ));

        if (status == TransactionStatusCode.RJCT && reason == null) {
            throw new BusinessException(
                    "A simulated rejection needs a reason code"
            );
        }

        /*
         * Built for the payment's own rail and delivered the way that rail
         * delivers.
         *
         * This used to build every answer as ACH and put an RTGS payment's
         * UETR in OrgnlTxId. A UUID is 36 characters and that field is
         * Max35Text, so the answer failed the schema, was discarded as
         * malformed — answers are not answered back — and the payment stayed
         * SENT with its money in suspense. No RTGS payment could settle.
         */
        String rail = transaction.getPaymentType().schemaRail();
        boolean rtgs = "rtgs".equals(rail);
        String messageId = messageIdGenerator.newMessageId();

        String document = pacs002Builder.build(
                messageId,
                original.getMessageId(),
                transaction.getEndToEndId(),
                transaction.getTransactionReference(),
                rtgs ? transaction.getUetr() : null,
                status,
                reason,
                rail
        );

        String pacs002 = asDelivered(messageId, PacsMessageType.PACS_002, document, rail);

        inboundMessageService.receive(pacs002);

        return ResponseEntity.ok(pacs002);
    }

    /**
     * Simulates the beneficiary bank sending a settled payment back.
     *
     * Like the status simulation, this builds a genuine pacs.004 with the
     * production builder and feeds it through the real inbound path, so what
     * is exercised is the code that would run for an actual return rather than
     * a shortcut around it.
     *
     * @param returnedAmount what actually comes back, which may be less than
     *                       the original where the other bank kept charges.
     *                       Defaults to the full amount.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @PostMapping(
            value = "/simulate/return/{transactionReference}",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> simulateReturn(
            @PathVariable String transactionReference,
            @RequestParam(defaultValue = "AC04") ReasonCode reason,
            @RequestParam(required = false) BigDecimal returnedAmount) {

        Transaction transaction =
                transactionRepository
                        .findByTransactionReference(transactionReference)
                        .orElseThrow(() -> new BusinessException(
                                "Unknown payment: " + transactionReference
                        ));

        BigDecimal amount =
                returnedAmount != null ? returnedAmount : transaction.getAmount();

        String rail = transaction.getPaymentType().schemaRail();
        String messageId = messageIdGenerator.newMessageId();

        String document = pacs004Builder.build(
                messageId,
                messageIdGenerator.newMessageId(),
                transaction,
                amount,
                reason,
                rail
        );

        String pacs004 = asDelivered(messageId, PacsMessageType.PACS_004, document, rail);

        inboundMessageService.receive(pacs004);

        return ResponseEntity.ok(pacs004);
    }

    /**
     * What the scheme would actually put on the wire.
     *
     * RTGS messages arrive inside the operator's envelope under a business
     * application header, and that header is how the inbound side knows which
     * schema set to check against. A bare document is ACH by definition, so
     * an RTGS answer sent without one was validated against the wrong schema.
     */
    private String asDelivered(
            String messageId,
            PacsMessageType type,
            String document,
            String rail) {

        return "rtgs".equals(rail)
                ? headerBuilder.wrapInbound(messageId, type, document)
                : document;
    }
}
