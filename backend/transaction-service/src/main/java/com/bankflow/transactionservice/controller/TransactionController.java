package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.dto.QuoteRequest;
import com.bankflow.transactionservice.dto.TransactionPageResponse;
import com.bankflow.transactionservice.dto.TransactionResponse;
import com.bankflow.transactionservice.dto.TransferRequest;
import com.bankflow.transactionservice.entity.AuditEvent;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.pacs.PacsMessage;
import com.bankflow.transactionservice.pacs.PacsMessageService;
import com.bankflow.transactionservice.service.FeeAssessment;
import com.bankflow.transactionservice.service.OutboundPaymentService;
import com.bankflow.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/*
 * Every endpoint here is staff-only for now. Customer-facing transaction
 * history needs ownership filtering that does not exist yet; without it, a
 * customer could read any transaction by guessing an ID.
 */
@RestController
@RequestMapping("/api/transactions")
@PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
public class TransactionController {

    private final TransactionService transactionService;
    private final OutboundPaymentService outboundPaymentService;
    private final PacsMessageService pacsMessageService;

    public TransactionController(
            TransactionService transactionService,
            OutboundPaymentService outboundPaymentService,
            PacsMessageService pacsMessageService) {

        this.transactionService = transactionService;
        this.outboundPaymentService = outboundPaymentService;
        this.pacsMessageService = pacsMessageService;
    }

    /*
     * Restricted to bank staff for now.
     *
     * Customer-initiated transfers arrive with pain.001 support, which needs
     * per-account ownership checks that do not exist yet. Until then, allowing
     * CUSTOMER here would let any authenticated customer move money out of any
     * account by ID.
     */
    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> createTransfer(
            @Valid @RequestBody TransferRequest request) {

        /*
         * Routing is decided by the rail: a payment that leaves the bank needs
         * a suspense leg and a scheme message, an on-us one settles directly on
         * our own books.
         */
        boolean outbound =
                request.getPaymentType() != null
                        && request.getPaymentType().isExternal();

        Transaction transaction = outbound
                ? outboundPaymentService.send(request)
                : transactionService.createTransfer(
                        request.getSourceAccountId(),
                        request.getDestinationAccountId(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getEndToEndId(),
                        request.getInstructionId(),
                        request.getPaymentType(),
                        request.getChargeBearer(),
                        request.getPurposeCode(),
                        request.getRemittanceInformation()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TransactionResponse.fromEntity(transaction));
    }

    /**
     * What a payment would cost. Books nothing.
     */
    @PostMapping("/quote")
    public ResponseEntity<FeeAssessment> quote(
            @Valid @RequestBody QuoteRequest request) {

        return ResponseEntity.ok(
                transactionService.quote(
                        request.getPaymentType(),
                        request.getCurrency(),
                        request.getAmount(),
                        request.getChargeBearer()
                )
        );
    }

    /**
     * The scheme message a payment would produce, schema-checked, with nothing
     * stored and no money moved.
     */
    @PostMapping(value = "/preview-message", produces = "application/xml")
    public ResponseEntity<String> previewMessage(
            @Valid @RequestBody TransferRequest request) {

        return ResponseEntity.ok(
                transactionService.previewMessage(request)
        );
    }

    /**
     * ISO 20022 messages generated for one payment, oldest first.
     */
    @GetMapping("/reference/{transactionReference}/messages")
    public ResponseEntity<List<PacsMessage>> getMessages(
            @PathVariable String transactionReference) {

        return ResponseEntity.ok(
                pacsMessageService.findForTransaction(transactionReference)
        );
    }

    /**
     * The raw XML of one message, served as XML so it can be read or piped
     * straight into a schema checker.
     */
    @GetMapping(value = "/messages/{messageId}/xml", produces = "application/xml")
    public ResponseEntity<String> getMessageXml(
            @PathVariable String messageId) {

        return ResponseEntity.ok(
                pacsMessageService.findByMessageId(messageId).getRawXml()
        );
    }

    /**
     * Full audit history for one payment, oldest event first.
     */
    @GetMapping("/reference/{transactionReference}/audit")
    public ResponseEntity<List<AuditEvent>> getAuditTrail(
            @PathVariable String transactionReference) {

        return ResponseEntity.ok(
                transactionService.getAuditTrail(transactionReference)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable Long id) {

        Transaction transaction =
                transactionService.getTransaction(id);

        return ResponseEntity.ok(
                TransactionResponse.fromEntity(transaction)
        );
    }

    @GetMapping("/reference/{transactionReference}")
    public ResponseEntity<TransactionResponse> getTransactionByReference(
            @PathVariable String transactionReference) {

        Transaction transaction =
                transactionService.getTransactionByReference(
                        transactionReference
                );

        return ResponseEntity.ok(
                TransactionResponse.fromEntity(transaction)
        );
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<TransactionPageResponse> getAccountTransactions(
            @PathVariable Long accountId,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            TransactionStatus status,

            @RequestParam(required = false)
            TransactionType type,

            @RequestParam(required = false)
            LocalDate fromDate,

            @RequestParam(required = false)
            LocalDate toDate) {

        Page<Transaction> transactionPage =
                transactionService.getAccountTransactions(
                        accountId,
                        page,
                        size,
                        status,
                        type,
                        fromDate,
                        toDate
                );

        return ResponseEntity.ok(
                new TransactionPageResponse(
                        transactionPage.getContent()
                                .stream()
                                .map(TransactionResponse::fromEntity)
                                .toList(),
                        transactionPage.getNumber(),
                        transactionPage.getSize(),
                        transactionPage.getTotalElements(),
                        transactionPage.getTotalPages()
                )
        );
    }
}