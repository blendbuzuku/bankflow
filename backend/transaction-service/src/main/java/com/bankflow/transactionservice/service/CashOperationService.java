package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.entity.*;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cash paid in and taken out at a branch.
 *
 * These are the only ways money enters or leaves the bank other than through
 * the scheme, and they are proper double-entry transactions like anything else.
 * Adjusting a balance directly would move money with no ledger entry behind it —
 * which the reconciliation control correctly reports as a break, because that is
 * exactly what it is.
 *
 * The contra leg is the vault: the cash the bank physically holds. Notes over
 * the counter debit it and notes back out credit it, so its movements track a
 * real drawer -- countable, which is the point. (The stored balance runs
 * negative, since that column is kept credits-minus-debits for customer
 * accounts and an asset is the other way round.)
 *
 * It used to be suspense. Suspense means value in flight to the scheme, and it
 * clears when the scheme answers; cash is neither in flight nor ever answered
 * for. Booking it there left a residue nothing could resolve, and made the
 * suspense balance useless for the one question it exists to answer -- how
 * much is currently in flight -- because it also held every note ever
 * deposited.
 */
@Service
public class CashOperationService {

    private final BusinessCalendar businessCalendar;
    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final LedgerPoster ledgerPoster;
    private final SuspenseAccountResolver suspenseAccounts;
    private final FundsCheck fundsCheck;
    private final AuditService auditService;

    public CashOperationService(
            TransactionRepository transactionRepository,
            AccountClient accountClient,
            LedgerPoster ledgerPoster,
            SuspenseAccountResolver suspenseAccounts,
            FundsCheck fundsCheck,
            AuditService auditService,
            BusinessCalendar businessCalendar) {

        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
        this.ledgerPoster = ledgerPoster;
        this.suspenseAccounts = suspenseAccounts;
        this.fundsCheck = fundsCheck;
        this.auditService = auditService;
        this.businessCalendar = businessCalendar;
    }

    /** Cash paid in: DR the vault, CR the customer. */
    @Transactional
    public Transaction deposit(
            Long accountId,
            BigDecimal amount,
            String narrative) {

        AccountResponse account = require(accountId, amount);

        Transaction transaction = newTransaction(
                TransactionType.DEPOSIT,
                account,
                amount,
                narrative == null || narrative.isBlank()
                        ? "Cash deposit"
                        : narrative
        );

        transaction.setDestinationAccountId(accountId);
        transaction.setSourceAccountId(null);
        transaction.setCreditorIban(account.iban());

        transaction = transactionRepository.save(transaction);

        String reference = transaction.getTransactionReference();
        Currency currency = transaction.getCurrency();

        ledgerPoster.debit(
                transaction,
                suspenseAccounts.vaultAccountId(currency),
                amount,
                reference + "-VAULT"
        );

        ledgerPoster.credit(
                transaction,
                accountId,
                amount,
                reference + "-CREDIT"
        );

        return complete(transaction, "deposited into", amount, currency);
    }

    /** Cash taken out: DR the customer, CR the vault. */
    @Transactional
    public Transaction withdraw(
            Long accountId,
            BigDecimal amount,
            String narrative) {

        AccountResponse account = require(accountId, amount);

        Transaction transaction = newTransaction(
                TransactionType.WITHDRAWAL,
                account,
                amount,
                narrative == null || narrative.isBlank()
                        ? "Cash withdrawal"
                        : narrative
        );

        transaction.setSourceAccountId(accountId);
        transaction.setDestinationAccountId(null);
        transaction.setDebtorIban(account.iban());

        /*
         * A withdrawal is the one cash operation that can fail for lack of
         * funds, so it is checked like any other debit.
         */
        fundsCheck.assertCanCover(transaction);

        transaction = transactionRepository.save(transaction);

        String reference = transaction.getTransactionReference();
        Currency currency = transaction.getCurrency();

        ledgerPoster.debit(
                transaction,
                accountId,
                amount,
                reference + "-DEBIT"
        );

        ledgerPoster.credit(
                transaction,
                suspenseAccounts.vaultAccountId(currency),
                amount,
                reference + "-VAULT"
        );

        return complete(transaction, "withdrawn from", amount, currency);
    }

    private AccountResponse require(Long accountId, BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be greater than zero");
        }

        AccountResponse account = accountClient.getAccount(accountId);

        if (account == null) {
            throw new BusinessException("Account " + accountId + " does not exist");
        }

        if (!"ACTIVE".equals(account.status())) {
            throw new BusinessException(
                    "Account is %s and cannot be used".formatted(account.status())
            );
        }

        return account;
    }

    private Transaction newTransaction(
            TransactionType type,
            AccountResponse account,
            BigDecimal amount,
            String narrative) {

        String stamp = UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();

        Transaction transaction = new Transaction();

        transaction.setTransactionReference(
                "TXN-" + LocalDate.now() + "-" + stamp);

        transaction.setEndToEndId("CASH-" + stamp);
        transaction.setInstructionId("CASH-" + stamp);
        transaction.setTransactionType(type);

        /*
         * Cash never touches a scheme, so it is classified as internal — the
         * money moves between the branch and the customer's account, both ours.
         */
        transaction.setPaymentType(PaymentType.INTERNAL);
        transaction.setDirection(PaymentDirection.INTERNAL);
        transaction.setChargeBearer(ChargeBearer.DEBT);

        transaction.setAmount(amount);
        transaction.setCurrency(Currency.valueOf(account.currency()));
        transaction.setRemittanceInformation(narrative);

        /*
         * The business date, not the clock: cash taken after the books were
         * closed is tomorrow's business.
         */
        LocalDate today = businessCalendar.today();

        transaction.setBookingDate(today);
        transaction.setValueDate(today);
        transaction.setStatus(TransactionStatus.PROCESSING);

        try {
            AuthenticatedUser user = SecurityUtils.getCurrentUser();
            transaction.setCreatedByUserId(user.userId());
            transaction.setCreatedByUsername(user.username());
        } catch (IllegalStateException ignored) {
            // No principal; leave the maker fields empty rather than guess.
        }

        return transaction;
    }

    private Transaction complete(
            Transaction transaction,
            String verb,
            BigDecimal amount,
            Currency currency) {

        transaction.setStatus(TransactionStatus.COMPLETED);

        Transaction saved = transactionRepository.save(transaction);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("type", saved.getTransactionType().name());
        details.put("amount", amount);
        details.put("currency", currency.name());
        details.put("narrative", saved.getRemittanceInformation());

        auditService.record(
                AuditEventType.PAYMENT_SETTLED,
                saved.getTransactionReference(),
                "Transaction",
                saved.getTransactionReference(),
                "%s %s %s the account".formatted(amount, currency, verb),
                details
        );

        return saved;
    }
}
