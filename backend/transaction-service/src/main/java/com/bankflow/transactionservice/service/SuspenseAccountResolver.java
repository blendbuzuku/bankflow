package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.entity.Currency;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the bank's own accounts.
 *
 * Suspense holds the balancing leg of a payment that has left the debtor but
 * not yet reached the beneficiary; income receives fees; the vault is the
 * physical cash the bank is holding. All three are seeded once per currency by
 * account-service and never change, so their identifiers are cached rather
 * than fetched on every booking.
 */
@Service
public class SuspenseAccountResolver {

    private static final String SUSPENSE = "SUSPENSE";
    private static final String INCOME = "INCOME";
    private static final String VAULT = "VAULT";
    private static final String SETTLEMENT = "SETTLEMENT";

    private final AccountClient accountClient;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    public SuspenseAccountResolver(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    public Long suspenseAccountId(Currency currency) {
        return resolve(SUSPENSE, currency);
    }

    public Long incomeAccountId(Currency currency) {
        return resolve(INCOME, currency);
    }

    /**
     * The cash the bank physically holds in this currency.
     *
     * Deliberately not suspense. Suspense is value waiting on the scheme to
     * say where it went, and it clears when the scheme answers; cash over the
     * counter is neither waiting nor clearing, and parking it there left a
     * balance end-of-day would keep reporting as unresolved for ever.
     */
    public Long vaultAccountId(Currency currency) {
        return resolve(VAULT, currency);
    }

    /**
     * The bank's position at the central bank, where a settled payment lands.
     *
     * This is what lets suspense empty. Without it, settlement had nowhere to
     * post and so posted nothing, leaving every completed payment sitting in
     * suspense and making its balance answer nothing.
     */
    public Long settlementAccountId(Currency currency) {
        return resolve(SETTLEMENT, currency);
    }

    private Long resolve(String accountType, Currency currency) {

        return cache.computeIfAbsent(
                accountType + ":" + currency.name(),
                key -> {

                    AccountResponse account =
                            accountClient.getInternalAccount(
                                    accountType,
                                    currency.name()
                            );

                    if (account == null) {
                        throw new BusinessException(
                                "No %s account exists for %s".formatted(
                                        accountType, currency
                                )
                        );
                    }

                    return account.id();
                }
        );
    }
}
