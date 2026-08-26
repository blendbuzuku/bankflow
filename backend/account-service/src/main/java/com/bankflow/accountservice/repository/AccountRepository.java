package com.bankflow.accountservice.repository;

import com.bankflow.accountservice.entity.Account;
import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByIban(String iban);

    List<Account> findByClientId(Long clientId);

    /**
     * Internal accounts are unique per type and currency, so there is exactly
     * one suspense and one income account per currency.
     */
    Optional<Account> findByAccountTypeAndCurrency(
            AccountType accountType,
            Currency currency
    );

    /**
     * A client's existing accounts of one product, used to refuse a duplicate
     * that nothing distinguishes. Closed accounts are excluded, so a purpose
     * becomes reusable once the account holding it is shut.
     */
    @Query("""
            SELECT a
            FROM Account a
            WHERE a.client.id = :clientId
              AND a.accountType = :accountType
              AND a.currency = :currency
              AND a.status <> com.bankflow.accountservice.entity.AccountStatus.CLOSED
            """)
    List<Account> findOpenByClientAndProduct(
            @Param("clientId") Long clientId,
            @Param("accountType") AccountType accountType,
            @Param("currency") Currency currency
    );

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByIban(String iban);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Account a
            WHERE a.id = :id
            """)
    Optional<Account> findByIdForUpdate(
            @Param("id") Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Account a
            WHERE a.accountNumber = :accountNumber
            """)
    Optional<Account> findByAccountNumberForUpdate(
            @Param("accountNumber") String accountNumber
    );
}