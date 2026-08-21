package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.AccountStatus;
import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        Long clientId,
        String accountNumber,
        String iban,
        AccountType accountType,
        Currency currency,
        BigDecimal balance,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}