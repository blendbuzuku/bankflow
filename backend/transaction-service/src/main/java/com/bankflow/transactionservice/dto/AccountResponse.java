package com.bankflow.transactionservice.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        Long clientId,
        String accountNumber,
        String iban,
        String accountType,
        String currency,
        BigDecimal balance,
        String status,
        String createdAt,
        String updatedAt
) {
}