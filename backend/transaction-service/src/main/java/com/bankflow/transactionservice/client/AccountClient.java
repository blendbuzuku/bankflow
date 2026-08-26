package com.bankflow.transactionservice.client;

import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.dto.BalanceOperationRequest;
import com.bankflow.transactionservice.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
public class AccountClient {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public AccountClient(
            @Value("${bankflow.account-service.base-url}") String baseUrl,
            ServiceTokenProvider serviceTokenProvider) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.serviceTokenProvider = serviceTokenProvider;
    }

    public AccountResponse getAccount(Long accountId) {

        return restClient.get()
                .uri("/api/accounts/{id}", accountId)
                .header("Authorization", serviceTokenProvider.issueAuthorizationHeader())
                .retrieve()
                .body(AccountResponse.class);
    }

    /**
     * Resolves an account by IBAN, used to decide whether an inbound payment
     * names a beneficiary we actually hold.
     *
     * @return null when no such account exists, which is a routing answer
     *         rather than an error — it means the payment must be rejected with
     *         AC01 rather than failing.
     */
    public AccountResponse findByIban(String iban) {

        try {

            return restClient.get()
                    .uri("/api/accounts/iban/{iban}", iban)
                    .header("Authorization",
                            serviceTokenProvider.issueAuthorizationHeader())
                    .retrieve()
                    .body(AccountResponse.class);

        } catch (HttpClientErrorException.NotFound notFound) {
            return null;
        }
    }

    /**
     * Accounts belonging to a given user.
     *
     * Used to check that a customer owns the account they are paying from. We
     * ask as ourselves rather than forwarding the customer's token: authorising
     * the caller and acting on their behalf are separate concerns.
     */
    public List<AccountResponse> accountsForUser(Long userId) {

        AccountResponse[] accounts = restClient.get()
                .uri("/api/accounts/by-user/{userId}", userId)
                .header("Authorization",
                        serviceTokenProvider.issueAuthorizationHeader())
                .retrieve()
                .body(AccountResponse[].class);

        return accounts == null ? List.of() : Arrays.asList(accounts);
    }

    /**
     * One of the bank's own accounts: SUSPENSE holds funds in flight, INCOME
     * receives fees.
     */
    public AccountResponse getInternalAccount(String accountType, String currency) {

        return restClient.get()
                .uri("/api/accounts/internal/{type}/{currency}",
                        accountType, currency)
                .header("Authorization",
                        serviceTokenProvider.issueAuthorizationHeader())
                .retrieve()
                .body(AccountResponse.class);
    }

    /**
     * Every balance movement account-service recorded on a day.
     *
     * This is the independent record the ledger is checked against; asking the
     * account owner what actually changed is the whole point of reconciling.
     */
    public List<BalanceMovement> movementsOn(LocalDate date) {

        BalanceMovement[] movements = restClient.get()
                .uri("/api/accounts/reconciliation/operations?date={date}", date)
                .header("Authorization",
                        serviceTokenProvider.issueAuthorizationHeader())
                .retrieve()
                .body(BalanceMovement[].class);

        return movements == null ? List.of() : Arrays.asList(movements);
    }

    /**
     * One movement as account-service recorded it.
     */
    public record BalanceMovement(
            String operationId,
            Long accountId,
            String operation,
            BigDecimal amount,
            String currency,
            String createdAt
    ) {
    }

    public AccountResponse applyBalanceOperation(
            Long accountId,
            BalanceOperationRequest request) {

        return restClient.patch()
                .uri("/api/accounts/{id}/balance", accountId)
                .header("Authorization", serviceTokenProvider.issueAuthorizationHeader())
                .body(request)
                .retrieve()
                .body(AccountResponse.class);
    }
}
