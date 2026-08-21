package com.bankflow.accountservice.service;

import com.bankflow.accountservice.dto.AccountCreateRequest;
import com.bankflow.accountservice.dto.AccountResponse;
import com.bankflow.accountservice.entity.Account;
import com.bankflow.accountservice.entity.AccountStatus;
import com.bankflow.accountservice.entity.Client;
import com.bankflow.accountservice.repository.AccountRepository;
import com.bankflow.accountservice.repository.ClientRepository;
import com.bankflow.common.exception.ResourceNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import com.bankflow.accountservice.dto.BalanceOperationRequest;
import com.bankflow.accountservice.entity.BalanceOperationType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final IbanService ibanService;

    public AccountService(
            AccountRepository accountRepository,
            ClientRepository clientRepository,
            IbanService ibanService) {

        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.ibanService = ibanService;
    }

    public AccountResponse createAccount(AccountCreateRequest request) {

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with id: " + request.getClientId()
                        )
                );

        String accountNumber;

        do {
            accountNumber = ibanService.generateAccountNumber();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        String iban = ibanService.generateIban(accountNumber);

        Account account = new Account();

        account.setClient(client);
        account.setAccountNumber(accountNumber);
        account.setIban(iban);
        account.setAccountType(request.getAccountType());
        account.setCurrency(request.getCurrency());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);

        return mapToResponse(savedAccount);
    }

    public List<AccountResponse> getAllAccounts() {

        return accountRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AccountResponse getAccountById(Long id) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found with id: " + id
                        )
                );

        return mapToResponse(account);
    }

    public AccountResponse getAccountByIban(String iban) {

        Account account = accountRepository.findByIban(iban)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found with IBAN: " + iban
                        )
                );

        return mapToResponse(account);
    }

    public List<AccountResponse> getAccountsByClientId(Long clientId) {

        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException(
                    "Client not found with id: " + clientId
            );
        }

        return accountRepository.findByClientId(clientId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private AccountResponse mapToResponse(Account account) {

        return new AccountResponse(
                account.getId(),
                account.getClient().getId(),
                account.getAccountNumber(),
                account.getIban(),
                account.getAccountType(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    @Transactional
    public AccountResponse applyBalanceOperation(
            Long accountId,
            BalanceOperationRequest request) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found: " + accountId
                        )
                );

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Account is not active"
            );
        }

        if (account.getCurrency() != request.getCurrency()) {
            throw new IllegalArgumentException(
                    "Account currency does not match transaction currency"
            );
        }

        BigDecimal amount = request.getAmount();

        if (request.getOperation() == BalanceOperationType.DEBIT) {

            if (account.getBalance().compareTo(amount) < 0) {
                throw new IllegalStateException(
                        "Insufficient funds"
                );
            }

            account.setBalance(
                    account.getBalance().subtract(amount)
            );

        } else if (request.getOperation() == BalanceOperationType.CREDIT) {

            account.setBalance(
                    account.getBalance().add(amount)
            );
        }

        Account savedAccount = accountRepository.save(account);

        return mapToResponse(savedAccount);
    }
}