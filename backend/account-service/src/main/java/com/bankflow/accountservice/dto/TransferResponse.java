package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long transactionId,
        String transactionReference,
        String endToEndId,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        LocalDateTime createdAt
) {
}