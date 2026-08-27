package com.bankflow.transactionservice.dto;

import java.math.BigDecimal;

public record BalanceOperationRequest(
        BigDecimal amount,
        String operation,
        String currency,
        String operationId,

        /**
         * The bank's business date, which after a close is not today. The
         * movement has to be filed under the same day the ledger books it to,
         * or reconciliation pairs them across a boundary and reports a break.
         */
        java.time.LocalDate bookingDate
) {
}