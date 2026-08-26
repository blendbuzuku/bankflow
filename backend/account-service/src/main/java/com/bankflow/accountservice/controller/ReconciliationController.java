package com.bankflow.accountservice.controller;

import com.bankflow.accountservice.dto.BalanceOperationResponse;
import com.bankflow.accountservice.entity.Account;
import com.bankflow.accountservice.repository.AccountRepository;
import com.bankflow.accountservice.repository.BalanceOperationRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes what actually moved, so other services can check their records
 * against it.
 *
 * A service that books a payment believes it moved money; this is the account
 * owner's account of what really changed. Reconciliation is only meaningful
 * when the two are independent.
 */
@RestController
@RequestMapping("/api/accounts/reconciliation")
@PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN', 'TRANSACTION_SERVICE')")
public class ReconciliationController {

    private final BalanceOperationRepository balanceOperationRepository;
    private final AccountRepository accountRepository;

    public ReconciliationController(
            BalanceOperationRepository balanceOperationRepository,
            AccountRepository accountRepository) {

        this.balanceOperationRepository = balanceOperationRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Every balance movement on a given day.
     */
    @GetMapping("/operations")
    public ResponseEntity<List<BalanceOperationResponse>> operations(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        LocalDate day = date != null ? date : LocalDate.now();

        List<BalanceOperationResponse> operations =
                balanceOperationRepository
                        .findByCreatedAtBetween(
                                day.atStartOfDay(),
                                day.atTime(LocalTime.MAX)
                        )
                        .stream()
                        .map(operation -> new BalanceOperationResponse(
                                operation.getOperationId(),
                                operation.getAccountId(),
                                operation.getOperation().name(),
                                operation.getAmount(),
                                operation.getCurrency().name(),
                                operation.getCreatedAt()
                        ))
                        .toList();

        return ResponseEntity.ok(operations);
    }

    /**
     * Checks that every stored balance equals the sum of its own movements.
     *
     * Accounts open at zero and only balance operations change them, so any
     * difference means a balance was altered by something that left no movement
     * behind — direct database access, or a bug writing balances directly.
     */
    @GetMapping("/balance-integrity")
    public ResponseEntity<Map<String, Object>> balanceIntegrity() {

        List<Map<String, Object>> breaks = new ArrayList<>();

        for (Account account : accountRepository.findAll()) {

            BigDecimal expected =
                    balanceOperationRepository.sumMovementsFor(account.getId());

            if (expected.compareTo(account.getBalance()) != 0) {

                Map<String, Object> mismatch = new LinkedHashMap<>();

                mismatch.put("accountId", account.getId());
                mismatch.put("iban", account.getIban());
                mismatch.put("accountType", account.getAccountType().name());
                mismatch.put("storedBalance", account.getBalance());
                mismatch.put("impliedByMovements", expected);

                mismatch.put(
                        "difference",
                        account.getBalance().subtract(expected)
                );

                breaks.add(mismatch);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("consistent", breaks.isEmpty());
        result.put("accountsChecked", accountRepository.count());
        result.put("breaks", breaks);

        return ResponseEntity.ok(result);
    }
}
