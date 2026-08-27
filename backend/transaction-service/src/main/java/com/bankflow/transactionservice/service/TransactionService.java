package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.dto.BalanceOperationRequest;
import com.bankflow.transactionservice.dto.TransferRequest;
import com.bankflow.transactionservice.entity.AuditEventType;
import com.bankflow.transactionservice.entity.ChargeBearer;
import com.bankflow.transactionservice.entity.Currency;
import com.bankflow.transactionservice.entity.LedgerEntry;
import com.bankflow.transactionservice.entity.PaymentDirection;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.entity.PurposeCode;
import com.bankflow.transactionservice.pacs.MessageIdGenerator;
import com.bankflow.transactionservice.pacs.PacsMessageService;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import com.bankflow.transactionservice.entity.LedgerEntryType;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;
import com.bankflow.transactionservice.entity.AuditEvent;
import com.bankflow.transactionservice.repository.AuditEventRepository;
import com.bankflow.transactionservice.repository.LedgerEntryRepository;
import com.bankflow.transactionservice.repository.TransactionRepository;
import com.bankflow.transactionservice.repository.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {

    private final BusinessCalendar businessCalendar;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountClient accountClient;
    private final FeeService feeService;
    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;
    private final PacsMessageService pacsMessageService;
    private final MessageIdGenerator messageIdGenerator;
    private final ApprovalService approvalService;
    private final LedgerPoster ledgerPoster;
    private final FundsCheck fundsCheck;

    public TransactionService(
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountClient accountClient,
            FeeService feeService,
            AuditService auditService,
            AuditEventRepository auditEventRepository,
            PacsMessageService pacsMessageService,
            MessageIdGenerator messageIdGenerator,
            ApprovalService approvalService,
            LedgerPoster ledgerPoster,
            FundsCheck fundsCheck,
            BusinessCalendar businessCalendar) {

        this.fundsCheck = fundsCheck;
        this.pacsMessageService = pacsMessageService;
        this.messageIdGenerator = messageIdGenerator;
        this.approvalService = approvalService;
        this.ledgerPoster = ledgerPoster;

        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountClient = accountClient;
        this.feeService = feeService;
        this.auditService = auditService;
        this.auditEventRepository = auditEventRepository;
        this.businessCalendar = businessCalendar;
    }

    /**
     * Prices a payment without booking anything.
     *
     * The teller screen calls this to show the charge before the payment is
     * confirmed, and it makes the tariff independently verifiable.
     */
    public FeeAssessment quote(
            PaymentType paymentType,
            Currency currency,
            BigDecimal amount,
            ChargeBearer chargeBearer) {

        PaymentType type =
                paymentType != null ? paymentType : PaymentType.INTERNAL;

        ChargeBearer bearer =
                chargeBearer != null ? chargeBearer : type.defaultChargeBearer();

        return feeService.assess(
                type,
                currency,
                amount,
                bearer,
                directionFor(type),
                businessCalendar.today()
        );
    }

    /**
     * The scheme message a payment would produce, built and schema-checked but
     * not stored and with no money moved.
     *
     * Lets a teller see the exact XML destined for KIPS before committing, and
     * surfaces a schema rejection while the instruction can still be fixed.
     */
    public String previewMessage(TransferRequest request) {

        PaymentType paymentType =
                request.getPaymentType() != null
                        ? request.getPaymentType()
                        : PaymentType.INTERNAL;

        if (!paymentType.isExternal()) {
            throw new BusinessException(
                    "Internal transfers do not produce a scheme message; "
                            + "they are booked on our own ledger"
            );
        }

        ChargeBearer chargeBearer =
                request.getChargeBearer() != null
                        ? request.getChargeBearer()
                        : paymentType.defaultChargeBearer();

        if (!paymentType.permits(chargeBearer)) {
            throw new BusinessException(
                    "%s does not permit charge bearer %s".formatted(
                            paymentType.getDisplayName(),
                            chargeBearer.getCode()
                    )
            );
        }

        /*
         * An external payment names its creditor by IBAN and agent, since we
         * hold no account for them. Both are mandatory in the message.
         */
        if (request.getCreditorIban() == null
                || request.getCreditorIban().isBlank()) {

            throw new BusinessException(
                    "Creditor IBAN is required for an outbound payment"
            );
        }

        if (request.getCreditorName() == null
                || request.getCreditorName().isBlank()) {

            throw new BusinessException(
                    "Creditor name is required: the scheme mandates Cdtr/Nm"
            );
        }

        Transaction draft = new Transaction();

        draft.setTransactionReference(generateTransactionReference());
        draft.setEndToEndId(request.getEndToEndId());
        draft.setInstructionId(request.getInstructionId());
        draft.setTransactionType(TransactionType.TRANSFER);
        draft.setPaymentType(paymentType);
        draft.setDirection(PaymentDirection.OUTBOUND);
        draft.setServiceLevel(paymentType.getServiceLevel());
        draft.setChargeBearer(chargeBearer);
        draft.setPurposeCode(request.getPurposeCode());
        draft.setRemittanceInformation(request.getRemittanceInformation());
        draft.setAmount(request.getAmount());
        draft.setCurrency(request.getCurrency());
        LocalDate businessDay = businessCalendar.today();

        draft.setBookingDate(businessDay);
        draft.setValueDate(businessDay);
        draft.setStatus(TransactionStatus.PENDING);

        draft.setCreditorName(request.getCreditorName());
        draft.setCreditorIban(request.getCreditorIban());
        draft.setCreditorAgentBic(request.getCreditorAgentBic());

        if (request.getSourceAccountId() != null) {

            AccountResponse source =
                    accountClient.getAccount(request.getSourceAccountId());

            draft.setDebtorIban(source.iban());
            draft.setDebtorName(
                    firstNonBlank(request.getDebtorName(), source.clientName(), source.iban())
            );
        }

        if (paymentType == PaymentType.KIPS_RTGS) {
            draft.setUetr(messageIdGenerator.newUetr());
        }

        return pacsMessageService.previewCreditTransfer(draft);
    }

    /**
     * Until the external rails are built, every payment we can actually settle
     * is on-us. External types are priced but rejected at execution.
     */
    private PaymentDirection directionFor(PaymentType paymentType) {

        return paymentType.isExternal()
                ? PaymentDirection.OUTBOUND
                : PaymentDirection.INTERNAL;
    }

    @Transactional
    public Transaction createTransfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            Currency currency,
            String endToEndId,
            String instructionId,
            PaymentType requestedType,
            ChargeBearer requestedChargeBearer,
            PurposeCode purposeCode,
            String remittanceInformation) {

        validateTransferRequest(
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                endToEndId,
                instructionId
        );

        PaymentType paymentType =
                requestedType != null ? requestedType : PaymentType.INTERNAL;

        ChargeBearer chargeBearer =
                requestedChargeBearer != null
                        ? requestedChargeBearer
                        : paymentType.defaultChargeBearer();

        /*
         * Schemes restrict which charge bearers they accept — KIPS ACH permits
         * only SLEV. Refusing here gives a usable error, rather than building a
         * message the scheme would bounce at validation.
         */
        if (!paymentType.permits(chargeBearer)) {
            throw new BusinessException(
                    "%s does not permit charge bearer %s (allowed: %s)".formatted(
                            paymentType.getDisplayName(),
                            chargeBearer.getCode(),
                            paymentType.getPermittedChargeBearers()
                                    .stream()
                                    .map(ChargeBearer::getCode)
                                    .sorted()
                                    .toList()
                    )
            );
        }

        /*
         * External rails are priced but cannot yet be settled: there is no
         * suspense leg, no pacs.008 and nothing to answer a status report.
         * Failing here is far better than silently booking an external payment
         * as if it were on-us.
         */
        if (paymentType.isExternal()) {
            throw new BusinessException(
                    "Payment type " + paymentType.getCode()
                            + " is not yet supported for execution"
            );
        }

        /*
         * Prevent duplicate transactions at the transaction level.
         */
        if (transactionRepository.existsByEndToEndId(endToEndId)) {
            throw new BusinessException(
                    "Transaction with this end-to-end ID already exists"
            );
        }

        /*
         * Retrieve both accounts from account-service.
         */
        AccountResponse sourceAccount =
                accountClient.getAccount(sourceAccountId);

        AccountResponse destinationAccount =
                accountClient.getAccount(destinationAccountId);

        validateAccounts(
                sourceAccount,
                destinationAccount,
                currency,
                amount
        );

        FeeAssessment fee = feeService.assess(
                paymentType,
                currency,
                amount,
                chargeBearer,
                PaymentDirection.INTERNAL,
                businessCalendar.today()
        );

        /*
         * Create transaction in PENDING state.
         */
        Transaction transaction = createPendingTransaction(
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                endToEndId,
                instructionId
        );

        transaction.setPaymentType(paymentType);
        transaction.setDirection(PaymentDirection.INTERNAL);
        transaction.setServiceLevel(paymentType.getServiceLevel());
        transaction.setChargeBearer(chargeBearer);
        transaction.setPurposeCode(purposeCode);
        transaction.setRemittanceInformation(remittanceInformation);

        transaction.setDebtorIban(sourceAccount.iban());
        transaction.setCreditorIban(destinationAccount.iban());

        /*
         * The scheme requires a name for both parties. Account holder names are
         * not yet carried on AccountResponse, so fall back to the IBAN, which is
         * at least a true identifier of the party rather than an invented name.
         * Phase 6 replaces this with the real client name from the teller form.
         */
        /*
         * Names come from the account holders themselves. An IBAN is only a
         * last resort, for the case where a client record somehow has no name.
         */
        transaction.setDebtorName(
                firstNonBlank(
                        transaction.getDebtorName(),
                        sourceAccount.clientName(),
                        sourceAccount.iban()
                )
        );

        transaction.setCreditorName(
                firstNonBlank(
                        transaction.getCreditorName(),
                        destinationAccount.clientName(),
                        destinationAccount.iban()
                )
        );

        /*
         * RTGS follows a payment by its UETR rather than by our own reference,
         * so it is allocated at creation and never changes.
         */
        if (paymentType == PaymentType.KIPS_RTGS) {
            transaction.setUetr(messageIdGenerator.newUetr());
        }

        transaction.setFeeAmount(fee.totalFee());
        transaction.setDebtorFeeAmount(fee.debtorFee());
        transaction.setCreditorFeeAmount(fee.creditorFee());

        applyInitiator(transaction);

        transaction = transactionRepository.save(transaction);

        /*
         * Only payments that actually leave the bank get a scheme message.
         * KIPS is an interbank system: an on-us transfer never reaches it, and
         * inventing a pacs.008 for one would misrepresent an internal booking
         * as a clearing instruction. The ledger entries and audit trail are
         * what document an internal payment.
         *
         * When a message is built, it is built before any money moves — if the
         * instruction cannot be expressed in a form KIPS would accept, the
         * payment is wrong and should fail now rather than after a debit.
         */
        if (paymentType.isExternal()) {
            pacsMessageService.generateCreditTransfer(transaction);
        }

        auditService.record(
                AuditEventType.PAYMENT_INITIATED,
                transaction.getTransactionReference(),
                "Transaction",
                transaction.getTransactionReference(),
                "%s payment of %s %s initiated".formatted(
                        paymentType.getDisplayName(),
                        amount,
                        currency
                ),
                Map.of(
                        "paymentType", paymentType.getCode(),
                        "amount", amount,
                        "currency", currency.name(),
                        "sourceAccountId", sourceAccountId,
                        "destinationAccountId", destinationAccountId,
                        "endToEndId", endToEndId
                )
        );

        auditService.record(
                AuditEventType.FEE_ASSESSED,
                transaction.getTransactionReference(),
                "Transaction",
                transaction.getTransactionReference(),
                fee.isFree()
                        ? "No charge applied"
                        : "Charge of %s %s assessed".formatted(
                                fee.totalFee(), currency),
                Map.of(
                        "ruleCode", String.valueOf(fee.ruleCode()),
                        "description", String.valueOf(fee.description()),
                        "totalFee", fee.totalFee(),
                        "debtorFee", fee.debtorFee(),
                        "creditorFee", fee.creditorFee(),
                        "chargeBearer", chargeBearer.getCode()
                )
        );

        /*
         * A payment at or above the threshold stops here: nothing is booked
         * until a second person releases it, so an unapproved payment leaves no
         * trace on the ledger. On-us payments are held to the same rule as
         * outbound ones — a large internal transfer is no less worth a second
         * look for staying inside the bank.
         */
        if (approvalService.requiresApproval(transaction)) {
            return approvalService.park(transaction);
        }

        return executeInternal(transaction);
    }

    /**
     * Books an on-us payment that is cleared to proceed.
     *
     * Reached directly when no approval was needed, or from the approval
     * service once released — the same path either way.
     */
    @Transactional
    public Transaction executeInternal(Transaction transaction) {

        /*
         * Re-check at the moment of booking. A payment held for approval may
         * have been affordable when it was instructed and not by the time it is
         * released.
         */
        fundsCheck.assertCanCover(transaction);

        Long sourceAccountId = transaction.getSourceAccountId();
        Long destinationAccountId = transaction.getDestinationAccountId();
        BigDecimal amount = transaction.getAmount();
        Currency currency = transaction.getCurrency();

        /*
         * Move transaction to PROCESSING before interacting
         * with account-service.
         */
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction = transactionRepository.save(transaction);

        boolean sourceDebited = false;

        try {

            /*
             * Both legs go through the shared poster, which moves the balance,
             * writes the ledger entry and audits the movement as one step. That
             * is also what records the operation id on the entry, without which
             * the movement can never be reconciled against what actually moved.
             */
            LedgerEntry debit = ledgerPoster.debit(
                    transaction,
                    sourceAccountId,
                    amount,
                    transaction.getTransactionReference() + "-DEBIT"
            );

            sourceDebited = true;

            LedgerEntry credit = ledgerPoster.credit(
                    transaction,
                    destinationAccountId,
                    amount,
                    transaction.getTransactionReference() + "-CREDIT"
            );

            /*
             * Verify that the transaction is balanced.
             */
            validateAccountingBalance(debit, credit);

            /*
             * Record the posted pair. Each entry is also audited individually
             * as an account movement; this event is what proves the two sides
             * net to zero, which is the invariant end-of-day reconciliation
             * depends on.
             */
            Map<String, Object> posting = new LinkedHashMap<>();

            posting.put("debitEntry", debit.getEntryReference());
            posting.put("debitAccountId", debit.getAccountId());
            posting.put("debitAmount", debit.getAmount());
            posting.put("creditEntry", credit.getEntryReference());
            posting.put("creditAccountId", credit.getAccountId());
            posting.put("creditAmount", credit.getAmount());
            posting.put("currency", currency.name());
            posting.put("bookingDate", transaction.getBookingDate().toString());

            posting.put(
                    "net",
                    debit.getAmount().subtract(credit.getAmount())
            );

            auditService.record(
                    AuditEventType.LEDGER_ENTRY_POSTED,
                    transaction.getTransactionReference(),
                    "LedgerEntry",
                    debit.getEntryReference() + "/" + credit.getEntryReference(),
                    "Double entry posted: DR %s %s / CR %s %s, net %s".formatted(
                            debit.getAmount(), currency,
                            credit.getAmount(), currency,
                            debit.getAmount().subtract(credit.getAmount())
                    ),
                    posting
            );

            /*
             * Transaction completed successfully.
             */
            transaction.setStatus(TransactionStatus.COMPLETED);

            Transaction completed = transactionRepository.save(transaction);

            auditService.record(
                    AuditEventType.PAYMENT_SETTLED,
                    completed.getTransactionReference(),
                    "Transaction",
                    completed.getTransactionReference(),
                    "Payment of %s %s settled internally".formatted(
                            completed.getAmount(),
                            completed.getCurrency()
                    ),
                    Map.of(
                            "status", completed.getStatus().name(),
                            "debitEntry", debit.getEntryReference(),
                            "creditEntry", credit.getEntryReference()
                    )
            );

            return completed;

        } catch (BusinessException exception) {

            handleTransferFailure(
                    transaction,
                    sourceDebited,
                    sourceAccountId,
                    amount,
                    currency
            );

            throw exception;

        } catch (Exception exception) {

            handleTransferFailure(
                    transaction,
                    sourceDebited,
                    sourceAccountId,
                    amount,
                    currency
            );

            throw new BusinessException(
                    "Transfer failed",
                    exception
            );
        }
    }

    /**
     * Stamps who initiated the payment.
     *
     * Recorded on the transaction itself, not only in the audit log, because
     * the four-eyes rule needs to compare maker against approver without
     * replaying history.
     */
    private void applyInitiator(Transaction transaction) {

        try {

            AuthenticatedUser user = SecurityUtils.getCurrentUser();

            transaction.setCreatedByUserId(user.userId());
            transaction.setCreatedByUsername(user.username());

        } catch (IllegalStateException exception) {
            // No principal: leave the maker fields empty rather than guess.
        }
    }

    /**
     * The scheme requires a party name; an IBAN is a truthful stand-in until
     * the teller form supplies the real one.
     */
    private String firstNonBlank(String... values) {

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private void validateTransferRequest(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            Currency currency,
            String endToEndId,
            String instructionId) {

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException(
                    "Transaction amount must be greater than zero"
            );
        }

        /*
         * Only on-us payments reach here, and both legs of one are our own
         * accounts. External routing arrives with the payment engine.
         */
        if (sourceAccountId == null ||
                destinationAccountId == null) {

            throw new BusinessException(
                    "Source and destination accounts are required "
                            + "for an internal transfer"
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

        if (endToEndId == null ||
                endToEndId.isBlank()) {

            throw new BusinessException(
                    "End-to-end ID is required"
            );
        }

        if (instructionId == null ||
                instructionId.isBlank()) {

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

        LocalDate today = businessCalendar.today();

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

        transaction.setSourceAccountId(
                sourceAccountId
        );

        transaction.setDestinationAccountId(
                destinationAccountId
        );

        transaction.setAmount(amount);
        transaction.setCurrency(currency);

        transaction.setBookingDate(today);
        transaction.setValueDate(today);

        return transaction;
    }

    private void validateAccountingBalance(
            LedgerEntry debit,
            LedgerEntry credit) {

        if (debit.getAmount() == null ||
                credit.getAmount() == null ||
                debit.getAmount()
                        .compareTo(credit.getAmount()) != 0) {

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
            Currency currency) {

        if (sourceDebited) {

            try {

                /*
                 * Roll back the source debit by crediting the source account.
                 *
                 * A distinct operation id, so the reversal is idempotent in its
                 * own right rather than colliding with the debit it undoes. It
                 * goes through the poster like any other movement: a rollback
                 * moves real money and needs its own ledger entry, or the books
                 * will not reconcile against what actually happened.
                 */
                ledgerPoster.credit(
                        transaction,
                        sourceAccountId,
                        amount,
                        transaction.getTransactionReference() + "-ROLLBACK"
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

        auditService.record(
                AuditEventType.PAYMENT_FAILED,
                transaction.getTransactionReference(),
                "Transaction",
                transaction.getTransactionReference(),
                sourceDebited
                        ? "Payment failed after debit; source account credited back"
                        : "Payment failed before any debit was made",
                Map.of(
                        "sourceDebited", sourceDebited,
                        "sourceAccountId", sourceAccountId,
                        "amount", amount,
                        "currency", currency.name()
                )
        );
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
    public List<AuditEvent> getAuditTrail(String transactionReference) {

        return auditEventRepository
                .findByTransactionReferenceOrderByOccurredAtAsc(
                        transactionReference
                );
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

        if (fromDate != null &&
                toDate != null &&
                fromDate.isAfter(toDate)) {

            throw new BusinessException(
                    "From date cannot be after to date"
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