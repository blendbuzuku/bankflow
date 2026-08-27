package com.bankflow.transactionservice.controller;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.pacs.*;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.service.InboundMessageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

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

    public KipsController(
            InboundMessageService inboundMessageService,
            TransactionRepository transactionRepository,
            PacsMessageRepository messageRepository,
            Pacs002Builder pacs002Builder,
            Pacs004Builder pacs004Builder,
            MessageIdGenerator messageIdGenerator) {

        this.inboundMessageService = inboundMessageService;
        this.transactionRepository = transactionRepository;
        this.messageRepository = messageRepository;
        this.pacs002Builder = pacs002Builder;
        this.pacs004Builder = pacs004Builder;
        this.messageIdGenerator = messageIdGenerator;
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

        String pacs002 = pacs002Builder.build(
                messageIdGenerator.newMessageId(),
                original.getMessageId(),
                transaction.getEndToEndId(),
                transaction.getUetr() != null
                        ? transaction.getUetr()
                        : transaction.getTransactionReference(),
                status,
                reason
        );

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

        String pacs004 = pacs004Builder.build(
                messageIdGenerator.newMessageId(),
                messageIdGenerator.newMessageId(),
                transaction,
                amount,
                reason,
                transaction.getPaymentType().schemaRail()
        );

        inboundMessageService.receive(pacs004);

        return ResponseEntity.ok(pacs004);
    }
}
