package com.bankflow.transactionservice.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        Long clientId,
        String clientName,
        String clientStatus,

        /** The holder's address, for the debtor's PstlAdr on a pacs.008. */
        String clientAddressLine1,
        String clientAddressLine2,
        String clientCity,
        String clientPostalCode,
        String clientCountry,
        String accountNumber,
        String iban,
        String accountType,

        /** What the account is for, when a client holds more than one. */
        String purpose,

        String currency,
        BigDecimal balance,
        String status,
        String createdAt,
        String updatedAt
) {
}