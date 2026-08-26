package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AccountCreateRequest {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Currency is required")
    private Currency currency;

    /**
     * Optional. Required only to open a second account of the same type and
     * currency for a client, where it is what tells the two apart.
     */
    @Size(max = 60, message = "Purpose must be 60 characters or fewer")
    private String purpose;

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

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}