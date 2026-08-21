package com.bankflow.transactionservice.dto;

import java.math.BigDecimal;

public record BalanceOperationRequest(
        BigDecimal amount,
        String operation,
        String currency
) {
}