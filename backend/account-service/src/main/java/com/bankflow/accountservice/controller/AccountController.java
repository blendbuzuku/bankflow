package com.bankflow.accountservice.controller;

import com.bankflow.accountservice.dto.AccountCreateRequest;
import com.bankflow.accountservice.dto.AccountResponse;
import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.Currency;
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

    @GetMapping("/me")
    public ResponseEntity<List<AccountResponse>> getCurrentUserAccounts() {

        return ResponseEntity.ok(
                accountService.getCurrentUserAccounts()
        );
    }

    /**
     * Accounts belonging to a given user, for ownership checks by other
     * services.
     */
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<List<AccountResponse>> getAccountsForUser(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                accountService.getAccountsForUser(userId)
        );
    }

    /**
     * The bank's own suspense and income accounts, resolved by type and
     * currency rather than by ID so callers need no seeded identifiers.
     */
    @GetMapping("/internal/{accountType}/{currency}")
    public ResponseEntity<AccountResponse> getInternalAccount(
            @PathVariable AccountType accountType,
            @PathVariable Currency currency) {

        return ResponseEntity.ok(
                accountService.getInternalAccount(accountType, currency)
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

    /**
     * Ends an account's life. Not a delete — the payments behind it stay.
     */
    @PostMapping("/{id}/close")
    public ResponseEntity<AccountResponse> closeAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.closeAccount(id));
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