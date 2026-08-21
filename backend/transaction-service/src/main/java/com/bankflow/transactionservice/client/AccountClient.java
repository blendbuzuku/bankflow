package com.bankflow.transactionservice.client;

import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.dto.BalanceOperationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
    }

    public AccountResponse getAccount(
            Long accountId,
            String authorizationHeader) {

        return restClient.get()
                .uri("/api/accounts/{id}", accountId)
                .header("Authorization", authorizationHeader)
                .retrieve()
                .body(AccountResponse.class);
    }

    public AccountResponse applyBalanceOperation(
            Long accountId,
            BalanceOperationRequest request,
            String authorizationHeader) {

        return restClient.patch()
                .uri("/api/accounts/{id}/balance", accountId)
                .header("Authorization", authorizationHeader)
                .body(request)
                .retrieve()
                .body(AccountResponse.class);
    }
}