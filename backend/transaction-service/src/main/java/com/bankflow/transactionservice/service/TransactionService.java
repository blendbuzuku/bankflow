package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.dto.BalanceOperationRequest;
import com.bankflow.transactionservice.entity.*;
import com.bankflow.transactionservice.repository.LedgerEntryRepository;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;
import com.bankflow.transactionservice.repository.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountClient accountClient;

    public TransactionService(
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountClient accountClient) {

        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountClient = accountClient;
    }

    @Transactional
    public Transaction createTransfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            Currency currency,
            String endToEndId,
            String instructionId,
            String authorizationHeader) {

        validateTransferRequest(
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                endToEndId,
                instructionId
        );

        // Prevent duplicate transactions.
        if (transactionRepository.existsByEndToEndId(endToEndId)) {
            throw new BusinessException(
                    "Transaction with this end-to-end ID already exists"
            );
        }

        // Retrieve both accounts from account-service.
        AccountResponse sourceAccount =
                accountClient.getAccount(
                        sourceAccountId,
                        authorizationHeader
                );

        AccountResponse destinationAccount =
                accountClient.getAccount(
                        destinationAccountId,
                        authorizationHeader
                );

        validateAccounts(
                sourceAccount,
                destinationAccount,
                currency,
                amount
        );

        // Create transaction in PENDING state.
        Transaction transaction = createPendingTransaction(
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                endToEndId,
                instructionId
        );

        transaction = transactionRepository.save(transaction);

        // Move transaction to PROCESSING before interacting
        // with the account-service.
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction = transactionRepository.save(transaction);

        boolean sourceDebited = false;

        try {

            // Debit source account.
            debitAccount(
                    sourceAccountId,
                    amount,
                    currency,
                    authorizationHeader
            );

            sourceDebited = true;

            // Credit destination account.
            creditAccount(
                    destinationAccountId,
                    amount,
                    currency,
                    authorizationHeader
            );

            // Create double-entry ledger records.
            LedgerEntry debit = createLedgerEntry(
                    transaction,
                    sourceAccountId,
                    LedgerEntryType.DEBIT,
                    amount,
                    currency
            );

            LedgerEntry credit = createLedgerEntry(
                    transaction,
                    destinationAccountId,
                    LedgerEntryType.CREDIT,
                    amount,
                    currency
            );

            ledgerEntryRepository.save(debit);
            ledgerEntryRepository.save(credit);

            // Verify that the transaction is balanced.
            validateAccountingBalance(debit, credit);

            // Transaction completed successfully.
            transaction.setStatus(TransactionStatus.COMPLETED);

            return transactionRepository.save(transaction);

        } catch (BusinessException exception) {

            handleTransferFailure(
                    transaction,
                    sourceDebited,
                    sourceAccountId,
                    amount,
                    currency,
                    authorizationHeader
            );

            throw exception;

        } catch (Exception exception) {

            handleTransferFailure(
                    transaction,
                    sourceDebited,
                    sourceAccountId,
                    amount,
                    currency,
                    authorizationHeader
            );

            throw new BusinessException(
                    "Transfer failed",
                    exception
            );
        }
    }

    private void validateTransferRequest(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            Currency currency,
            String endToEndId,
            String instructionId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Transaction amount must be greater than zero"
            );
        }

        if (sourceAccountId == null || destinationAccountId == null) {
            throw new BusinessException(
                    "Source and destination accounts are required"
            );
        }

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new BusinessException(
                    "Source and destination accounts must be different"
            );
        }

        if (currency == null) {
            throw new BusinessException(
                    "Currency is required"
            );
        }

        if (endToEndId == null || endToEndId.isBlank()) {
            throw new BusinessException(
                    "End-to-end ID is required"
            );
        }

        if (instructionId == null || instructionId.isBlank()) {
            throw new BusinessException(
                    "Instruction ID is required"
            );
        }
    }

    private void validateAccounts(
            AccountResponse sourceAccount,
            AccountResponse destinationAccount,
            Currency currency,
            BigDecimal amount) {

        if (sourceAccount == null) {
            throw new BusinessException(
                    "Source account could not be found"
            );
        }

        if (destinationAccount == null) {
            throw new BusinessException(
                    "Destination account could not be found"
            );
        }

        if (!"ACTIVE".equals(sourceAccount.status())) {
            throw new BusinessException(
                    "Source account is not active"
            );
        }

        if (!"ACTIVE".equals(destinationAccount.status())) {
            throw new BusinessException(
                    "Destination account is not active"
            );
        }

        if (!currency.name().equals(sourceAccount.currency())) {
            throw new BusinessException(
                    "Source account currency does not match transaction currency"
            );
        }

        if (!currency.name().equals(destinationAccount.currency())) {
            throw new BusinessException(
                    "Destination account currency does not match transaction currency"
            );
        }

        if (sourceAccount.balance() == null) {
            throw new BusinessException(
                    "Source account balance is unavailable"
            );
        }

        if (sourceAccount.balance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "Insufficient funds"
            );
        }
    }

    private Transaction createPendingTransaction(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            Currency currency,
            String endToEndId,
            String instructionId) {

        LocalDate today = LocalDate.now();

        Transaction transaction = new Transaction();

        transaction.setTransactionReference(
                generateTransactionReference()
        );

        transaction.setEndToEndId(endToEndId);
        transaction.setInstructionId(instructionId);

        transaction.setTransactionType(
                TransactionType.TRANSFER
        );

        transaction.setStatus(
                TransactionStatus.PENDING
        );

        transaction.setSourceAccountId(sourceAccountId);
        transaction.setDestinationAccountId(destinationAccountId);

        transaction.setAmount(amount);
        transaction.setCurrency(currency);

        transaction.setBookingDate(today);
        transaction.setValueDate(today);

        return transaction;
    }

    private void debitAccount(
            Long accountId,
            BigDecimal amount,
            Currency currency,
            String authorizationHeader) {

        BalanceOperationRequest request =
                new BalanceOperationRequest(
                        amount,
                        "DEBIT",
                        currency.name()
                );

        accountClient.applyBalanceOperation(
                accountId,
                request,
                authorizationHeader
        );
    }

    private void creditAccount(
            Long accountId,
            BigDecimal amount,
            Currency currency,
            String authorizationHeader) {

        BalanceOperationRequest request =
                new BalanceOperationRequest(
                        amount,
                        "CREDIT",
                        currency.name()
                );

        accountClient.applyBalanceOperation(
                accountId,
                request,
                authorizationHeader
        );
    }

    private LedgerEntry createLedgerEntry(
            Transaction transaction,
            Long accountId,
            LedgerEntryType entryType,
            BigDecimal amount,
            Currency currency) {

        LedgerEntry entry = new LedgerEntry();

        entry.setTransaction(transaction);

        entry.setEntryReference(
                generateLedgerReference()
        );

        entry.setAccountId(accountId);
        entry.setEntryType(entryType);

        entry.setAmount(amount);
        entry.setCurrency(currency);

        entry.setBookingDate(
                transaction.getBookingDate()
        );

        entry.setValueDate(
                transaction.getValueDate()
        );

        return entry;
    }

    private void validateAccountingBalance(
            LedgerEntry debit,
            LedgerEntry credit) {

        if (debit.getAmount() == null ||
                credit.getAmount() == null ||
                debit.getAmount().compareTo(credit.getAmount()) != 0) {

            throw new BusinessException(
                    "Transaction is not balanced"
            );
        }
    }

    private void handleTransferFailure(
            Transaction transaction,
            boolean sourceDebited,
            Long sourceAccountId,
            BigDecimal amount,
            Currency currency,
            String authorizationHeader) {

        if (sourceDebited) {
            try {

                creditAccount(
                        sourceAccountId,
                        amount,
                        currency,
                        authorizationHeader
                );

            } catch (Exception rollbackException) {

                transaction.setStatus(
                        TransactionStatus.FAILED
                );

                transactionRepository.save(transaction);

                throw new BusinessException(
                        "Transfer failed and automatic rollback also failed",
                        rollbackException
                );
            }
        }

        transaction.setStatus(
                TransactionStatus.FAILED
        );

        transactionRepository.save(transaction);
    }

    private String generateTransactionReference() {

        return "TXN-" +
                LocalDate.now() +
                "-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }

    private String generateLedgerReference() {

        return "LED-" +
                LocalDate.now() +
                "-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(Long id) {

        return transactionRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                "Transaction not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public Transaction getTransactionByReference(
            String transactionReference) {

        return transactionRepository
                .findByTransactionReference(transactionReference)
                .orElseThrow(() ->
                        new BusinessException(
                                "Transaction not found: "
                                        + transactionReference
                        )
                );
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getAccountTransactions(
            Long accountId,
            int page,
            int size,
            TransactionStatus status,
            TransactionType type,
            LocalDate fromDate,
            LocalDate toDate) {

        if (page < 0) {
            throw new BusinessException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new BusinessException(
                    "Page size must be between 1 and 100"
            );
        }

        Specification<Transaction> specification =
                TransactionSpecifications.accountId(accountId);

        if (status != null) {
            specification = specification.and(
                    TransactionSpecifications.status(status)
            );
        }

        if (type != null) {
            specification = specification.and(
                    TransactionSpecifications.transactionType(type)
            );
        }

        if (fromDate != null) {
            specification = specification.and(
                    TransactionSpecifications.bookingDateFrom(fromDate)
            );
        }

        if (toDate != null) {
            specification = specification.and(
                    TransactionSpecifications.bookingDateTo(toDate)
            );
        }

        if (fromDate != null
                && toDate != null
                && fromDate.isAfter(toDate)) {

            throw new BusinessException(
                    "From date cannot be after to date"
            );
        }

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );

        return transactionRepository.findAll(
                specification,
                pageable
        );
    }
}