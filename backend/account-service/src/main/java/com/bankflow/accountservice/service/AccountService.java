package com.bankflow.accountservice.service;

import com.bankflow.accountservice.dto.AccountCreateRequest;
import com.bankflow.accountservice.dto.AccountResponse;
import com.bankflow.accountservice.entity.Account;
import com.bankflow.accountservice.entity.AccountStatus;
import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.Currency;
import com.bankflow.accountservice.entity.BalanceOperation;
import com.bankflow.accountservice.entity.Client;
import com.bankflow.accountservice.repository.AccountRepository;
import com.bankflow.accountservice.repository.BalanceOperationRepository;
import com.bankflow.accountservice.repository.ClientRepository;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.exception.ResourceNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import com.bankflow.accountservice.dto.BalanceOperationRequest;
import com.bankflow.accountservice.security.SecurityUtils;
import com.bankflow.accountservice.security.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final IbanService ibanService;
    private final BalanceOperationRepository balanceOperationRepository;

    public AccountService(
            AccountRepository accountRepository,
            ClientRepository clientRepository,
            IbanService ibanService,
            BalanceOperationRepository balanceOperationRepository) {

        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.ibanService = ibanService;
        this.balanceOperationRepository = balanceOperationRepository;
    }

    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public AccountResponse createAccount(AccountCreateRequest request) {

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with id: " + request.getClientId()
                        )
                );

        /*
         * No account for a client nobody has checked. This is what makes the
         * client status a control rather than a label: a customer registers as
         * PENDING and stays unable to hold money until a member of staff has
         * seen who they are and approved them.
         */
        if (!client.isActive()) {
            throw new BusinessException(
                    ("%s is %s. A client must be approved before an account can "
                            + "be opened for them.").formatted(
                            client.getDisplayName(),
                            client.getStatus()
                    )
            );
        }

        /*
         * The bank is not a customer of itself. Its own entity exists to own
         * the suspense and income accounts, which are seeded — opening a
         * current account against it would put a retail product on the general
         * ledger.
         */
        if (client.getUserId() != null && client.getUserId() < 0) {
            throw new BusinessException(
                    "The bank's own accounts are not opened at the counter"
            );
        }

        String purpose = normalisePurpose(request.getPurpose());

        /*
         * A client may hold more than one account of the same product — bills
         * kept apart from spending — but not several that nothing tells apart.
         * The purpose is what makes the second one deliberate; without it, a
         * duplicate is almost always a button pressed twice.
         */
        List<Account> existing = accountRepository.findOpenByClientAndProduct(
                client.getId(), request.getAccountType(), request.getCurrency()
        );

        if (purpose == null && !existing.isEmpty()) {
            throw new BusinessException(
                    ("%s already holds a %s %s account (%s). To open another, "
                            + "give it a purpose so the two can be told apart.")
                            .formatted(
                                    client.getDisplayName(),
                                    request.getAccountType(),
                                    request.getCurrency(),
                                    existing.getFirst().getIban()
                            )
            );
        }

        if (purpose != null) {
            existing.stream()
                    .filter(a -> purpose.equalsIgnoreCase(a.getPurpose()))
                    .findFirst()
                    .ifPresent(clash -> {
                        throw new BusinessException(
                                ("%s already holds a %s %s account for \"%s\" (%s).")
                                        .formatted(
                                                client.getDisplayName(),
                                                request.getAccountType(),
                                                request.getCurrency(),
                                                purpose,
                                                clash.getIban()
                                        )
                        );
                    });
        }

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
        account.setPurpose(purpose);

        Account savedAccount = accountRepository.save(account);

        return mapToResponse(savedAccount);
    }

    /** Blank and whitespace are the same as "no purpose given". */
    private static String normalisePurpose(String purpose) {

        if (purpose == null || purpose.isBlank()) {
            return null;
        }

        return purpose.trim();
    }

    /**
     * Closes an account.
     *
     * A bank does not delete accounts — the payments and ledger entries behind
     * one have to stay, and reconciliation reads them. Closing is the end of
     * the lifecycle: the account stops taking payments and stops being offered,
     * while everything it did remains.
     *
     * The balance must be nil first. Closing an account with money in it would
     * either strand the money or move it without a payment behind it, and the
     * second is exactly the break end-of-day reconciliation exists to find.
     */
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public AccountResponse closeAccount(Long id) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found with id: " + id
                        )
                );

        if (account.getAccountType().isInternal()) {
            throw new BusinessException(
                    "The bank's own accounts are not closed at the counter"
            );
        }

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException("That account is already closed");
        }

        if (account.getBalance().signum() != 0) {
            throw new BusinessException(
                    ("%s still holds %s %s. Move the balance out before "
                            + "closing it.").formatted(
                            account.getIban(),
                            account.getBalance().toPlainString(),
                            account.getCurrency()
                    )
            );
        }

        account.setStatus(AccountStatus.CLOSED);

        return mapToResponse(accountRepository.save(account));
    }

    @PreAuthorize(
            "hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')"
    )
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {

        return accountRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found with id: " + id
                        )
                );

        validateAccountOwnership(account);

        return mapToResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByIban(String iban) {

        Account account = accountRepository.findByIban(iban)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found with IBAN: " + iban
                        )
                );

        validateAccountOwnership(account);

        return mapToResponse(account);
    }

    /**
     * Resolves one of the bank's own accounts.
     *
     * Restricted to staff and to transaction-service, which needs the suspense
     * and income accounts to book the non-customer legs of a payment.
     */
    @PreAuthorize(
            "hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN', 'TRANSACTION_SERVICE')"
    )
    @Transactional(readOnly = true)
    public AccountResponse getInternalAccount(
            AccountType accountType,
            Currency currency) {

        if (accountType == null || !accountType.isInternal()) {
            throw new IllegalArgumentException(
                    "Not an internal account type: " + accountType
            );
        }

        Account account = accountRepository
                .findByAccountTypeAndCurrency(accountType, currency)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No " + accountType + " account exists for " + currency
                        )
                );

        return mapToResponse(account);
    }

    /**
     * Accounts belonging to a given user.
     *
     * Exists so transaction-service can answer "does this customer own the
     * account they are paying from" using its own service identity, rather than
     * forwarding the customer's token around. Authorising the caller and acting
     * on their behalf stay separate concerns.
     */
    @PreAuthorize(
            "hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN', 'TRANSACTION_SERVICE')"
    )
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsForUser(Long userId) {

        return clientRepository.findByUserId(userId)
                .map(client -> accountRepository.findByClientId(client.getId())
                        .stream()
                        .map(this::mapToResponse)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getCurrentUserAccounts() {

        Long userId = SecurityUtils.getCurrentUserId();

        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No client profile exists for the current user"
                        )
                );

        return accountRepository.findByClientId(client.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<AccountResponse> getAccountsByClientId(Long clientId) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with id: " + clientId
                        )
                );

        AuthenticatedUser currentUser =
                SecurityUtils.getCurrentUser();

        boolean privileged =
                SecurityUtils.hasRole("BANK_ADMIN") ||
                        SecurityUtils.hasRole("OPERATIONS") ||
                        SecurityUtils.hasRole("TELLER");

        if (!privileged &&
                !client.getUserId().equals(currentUser.userId())) {

            throw new AccessDeniedException(
                    "You do not have access to this client's accounts"
            );
        }

        return accountRepository.findByClientId(clientId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private AccountResponse mapToResponse(Account account) {

        Client holder = account.getClient();

        return new AccountResponse(
                account.getId(),
                holder.getId(),
                holder.getDisplayName(),
                holder.getStatus(),
                account.getAccountNumber(),
                account.getIban(),
                account.getAccountType(),
                account.getPurpose(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN', 'TRANSACTION_SERVICE')")
    @Transactional
    public AccountResponse applyBalanceOperation(
            Long accountId,
            BalanceOperationRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Balance operation request is required"
            );
        }

        if (request.getAmount() == null ||
                request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        if (request.getOperation() == null) {
            throw new IllegalArgumentException(
                    "Balance operation type is required"
            );
        }

        if (request.getCurrency() == null) {
            throw new IllegalArgumentException(
                    "Currency is required"
            );
        }

        if (request.getOperationId() == null ||
                request.getOperationId().isBlank()) {

            throw new IllegalArgumentException(
                    "Operation ID is required"
            );
        }

        /*
         * Idempotency check.
         *
         * If this exact operation was already successfully
         * processed, do not modify the balance again.
         */
        var existingOperation =
                balanceOperationRepository.findByOperationId(
                        request.getOperationId()
                );

        if (existingOperation.isPresent()) {

            if (!existingOperation.get()
                    .getAccountId()
                    .equals(accountId)) {

                throw new IllegalStateException(
                        "Operation ID is already associated with another account"
                );
            }

            if (existingOperation.get().getOperation()
                    != request.getOperation()) {

                throw new IllegalStateException(
                        "Operation ID was already used for another operation type"
                );
            }

            if (existingOperation.get().getAmount()
                    .compareTo(request.getAmount()) != 0) {

                throw new IllegalStateException(
                        "Operation ID was already used with another amount"
                );
            }

            if (existingOperation.get().getCurrency()
                    != request.getCurrency()) {

                throw new IllegalStateException(
                        "Operation ID was already used with another currency"
                );
            }

            /*
             * The operation was already completed.
             * Return the current account state without
             * applying the balance change again.
             */
            Account existingAccount =
                    accountRepository.findById(accountId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Account not found: " + accountId
                                    )
                            );

            return mapToResponse(existingAccount);
        }

        /*
         * Lock the account row until this transaction commits.
         *
         * This prevents concurrent balance modifications.
         */
        Account account =
                accountRepository.findByIdForUpdate(accountId)
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

        switch (request.getOperation()) {

            case DEBIT -> {

                /*
                 * The bank's own accounts are general ledger positions, not
                 * balances anyone can overdraw. Suspense in particular goes
                 * negative whenever more value arrives from the scheme than we
                 * have sent out, which is an ordinary state of affairs rather
                 * than a failure. Only customer accounts are held to a floor.
                 */
                if (!account.getAccountType().isInternal()
                        && account.getBalance().compareTo(amount) < 0) {

                    throw new BusinessException(
                            "Insufficient funds"
                    );
                }

                account.setBalance(
                        account.getBalance().subtract(amount)
                );
            }

            case CREDIT -> {

                account.setBalance(
                        account.getBalance().add(amount)
                );
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported balance operation: "
                            + request.getOperation()
            );
        }

        Account savedAccount =
                accountRepository.save(account);

        /*
         * Record the operation only after the balance
         * modification has been successfully applied.
         *
         * Because both operations are inside the same
         * database transaction, if saving this record fails,
         * the balance modification is rolled back as well.
         */
        BalanceOperation operation =
                new BalanceOperation();

        operation.setOperationId(
                request.getOperationId()
        );

        operation.setAccountId(accountId);

        operation.setOperation(
                request.getOperation()
        );

        operation.setAmount(
                request.getAmount()
        );

        operation.setCurrency(
                request.getCurrency()
        );

        balanceOperationRepository.save(operation);

        return mapToResponse(savedAccount);
    }

    private void validateAccountOwnership(Account account) {

        AuthenticatedUser currentUser =
                SecurityUtils.getCurrentUser();

        /*
         * TRANSACTION_SERVICE is the identity transaction-service presents when
         * settling a transfer. It reads both legs of a payment, which by
         * definition belong to two different clients, so it cannot be subject to
         * the per-customer ownership rule. Authorising the human who requested
         * the transfer happens at the transaction-service boundary.
         */
        boolean privileged =
                SecurityUtils.hasRole("BANK_ADMIN") ||
                        SecurityUtils.hasRole("OPERATIONS") ||
                        SecurityUtils.hasRole("TELLER") ||
                        SecurityUtils.hasRole("TRANSACTION_SERVICE");

        if (privileged) {
            return;
        }

        if (account.getClient() == null ||
                account.getClient().getUserId() == null) {

            throw new AccessDeniedException(
                    "Account ownership could not be verified"
            );
        }

        if (!account.getClient()
                .getUserId()
                .equals(currentUser.userId())) {

            throw new AccessDeniedException(
                    "You do not have access to this account"
            );
        }
    }
}