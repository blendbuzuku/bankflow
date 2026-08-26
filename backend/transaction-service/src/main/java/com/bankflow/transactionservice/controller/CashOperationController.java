package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.dto.TransactionResponse;
import com.bankflow.transactionservice.service.CashOperationService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Cash over the counter.
 *
 * Staff only, and never the customer: paying cash in or taking it out requires
 * someone at a branch to have actually handled it.
 */
@RestController
@RequestMapping("/api/cash")
@PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
@Validated
public class CashOperationController {

    private final CashOperationService cashOperationService;

    public CashOperationController(CashOperationService cashOperationService) {
        this.cashOperationService = cashOperationService;
    }

    public record CashRequest(
            @NotNull Long accountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String narrative
    ) {
    }

    @PostMapping("/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @RequestBody CashRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TransactionResponse.fromEntity(
                        cashOperationService.deposit(
                                request.accountId(),
                                request.amount(),
                                request.narrative()
                        )
                ));
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @RequestBody CashRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TransactionResponse.fromEntity(
                        cashOperationService.withdraw(
                                request.accountId(),
                                request.amount(),
                                request.narrative()
                        )
                ));
    }
}
