package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.dto.TransactionPageResponse;
import com.bankflow.transactionservice.dto.TransactionResponse;
import com.bankflow.transactionservice.dto.TransferRequest;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {

        this.transactionService = transactionService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> createTransfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {

        Transaction transaction =
                transactionService.createTransfer(
                        request.getSourceAccountId(),
                        request.getDestinationAccountId(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getEndToEndId(),
                        request.getInstructionId(),
                        authorizationHeader
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TransactionResponse.fromEntity(transaction));
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