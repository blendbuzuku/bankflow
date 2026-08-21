package com.bankflow.accountservice.controller;

import com.bankflow.accountservice.dto.AccountCreateRequest;
import com.bankflow.accountservice.dto.AccountResponse;
import com.bankflow.accountservice.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.bankflow.accountservice.dto.BalanceOperationRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(accountService.createAccount(request));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {

        return ResponseEntity.ok(
                accountService.getAllAccounts()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                accountService.getAccountById(id)
        );
    }

    @GetMapping("/iban/{iban}")
    public ResponseEntity<AccountResponse> getAccountByIban(
            @PathVariable String iban) {

        return ResponseEntity.ok(
                accountService.getAccountByIban(iban)
        );
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByClient(
            @PathVariable Long clientId) {

        return ResponseEntity.ok(
                accountService.getAccountsByClientId(clientId)
        );
    }

    @PatchMapping("/{id}/balance")
    public ResponseEntity<AccountResponse> applyBalanceOperation(
            @PathVariable Long id,
            @Valid @RequestBody BalanceOperationRequest request) {

        return ResponseEntity.ok(
                accountService.applyBalanceOperation(id, request)
        );
    }
}