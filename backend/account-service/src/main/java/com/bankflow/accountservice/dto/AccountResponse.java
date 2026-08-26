package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.AccountStatus;
import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An account as other services and screens see it.
 *
 * Carries the holder's name as well as their id. A payment message must state
 * who the debtor is, and a teller choosing an account needs to know whose it is
 * — neither is answerable from a client id alone, and making every caller fetch
 * the client separately just to render a name is worse.
 */
public record AccountResponse(
        Long id,
        Long clientId,
        String clientName,
        String clientStatus,
        String accountNumber,
        String iban,
        AccountType accountType,

        /** What the account is for, when a client holds more than one. */
        String purpose,

        Currency currency,
        BigDecimal balance,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
