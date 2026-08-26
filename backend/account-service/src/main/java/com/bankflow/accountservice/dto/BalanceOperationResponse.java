package com.bankflow.accountservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A movement that actually happened on an account.
 *
 * This is the authoritative record of what changed a balance, independent of
 * whatever the calling service believes it booked.
 */
public record BalanceOperationResponse(
        String operationId,
        Long accountId,
        String operation,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt
) {
}
