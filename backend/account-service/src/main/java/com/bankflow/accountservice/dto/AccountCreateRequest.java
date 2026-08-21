package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.Currency;
import jakarta.validation.constraints.NotNull;

public class AccountCreateRequest {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Currency is required")
    private Currency currency;

    public AccountCreateRequest() {
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }
}