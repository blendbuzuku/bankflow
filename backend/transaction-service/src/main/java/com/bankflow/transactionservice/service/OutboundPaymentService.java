package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.calendar.BusinessCalendar;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.dto.TransferRequest;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.entity.*;
import com.bankflow.transactionservice.pacs.*;
import com.bankflow.transactionservice.repository.LedgerEntryRepository;
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
 * Sends payments out of the bank.
 *
 * <h2>How the money moves</h2>
 *
 * An outbound payment debits our customer immediately, but there is nobody to
 * credit — the beneficiary banks elsewhere. The balancing credit goes to a
 * suspense account, which holds our net position against the scheme while funds
 * are in flight:
 *
 * <pre>
 *   DR debtor        amount + their share of the fee
 *   CR suspense      amount
 *   CR income        fee
 * </pre>
 *
 * When the counterparty confirms (pacs.002 ACSC) nothing further is booked: the
 * suspense credit already records the outflow, and the payment simply becomes
 * SETTLED. When it is refused (RJCT) the entries are unwound and the customer
 * made whole, fee included — we do not charge for a payment that never happened.
 */
@Service
public class OutboundPaymentService {

    private final BusinessCalendar businessCalendar;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountClient accountClient;
    private final FeeService feeService;
    private final AuditService auditService;
    private final LedgerPoster ledgerPoster;
    private final SuspenseAccountResolver suspenseAccounts;
    private final PacsMessageService pacsMessageService;
    private final MessageIdGenerator messageIdGenerator;
    private final ApprovalService approvalService;
    private final FundsCheck fundsCheck;
    private final PaymentFieldValidator fieldValidator;

    public OutboundPaymentService(
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountClient accountClient,
            FeeService feeService,
            AuditService auditService,
            LedgerPoster ledgerPoster,
            SuspenseAccountResolver suspenseAccounts,
            PacsMessageService pacsMessageService,
            MessageIdGenerator messageIdGenerator,
            ApprovalService approvalService,
            FundsCheck fundsCheck,
            BusinessCalendar businessCalendar,
            PaymentFieldValidator fieldValidator
) {

        this.fieldValidator = fieldValidator;

        this.approvalService = approvalService;
        this.fundsCheck = fundsCheck;

        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountClient = accountClient;
        this.feeService = feeService;
        this.auditService = auditService;
        this.ledgerPoster = ledgerPoster;
        this.suspenseAccounts = suspenseAccounts;
        this.pacsMessageService = pacsMessageService;
        this.messageIdGenerator = messageIdGenerator;
        this.businessCalendar = businessCalendar;
    }

    @Transactional
    public Transaction send(TransferRequest request) {

        PaymentType paymentType = request.getPaymentType();

        ChargeBearer chargeBearer =
                request.getChargeBearer() != null
                        ? request.getChargeBearer()
                        : paymentType.defaultChargeBearer();

        validate(request, paymentType, chargeBearer);

        if (transactionRepository.existsByEndToEndId(request.getEndToEndId())) {
            throw new BusinessException(
                    "Transaction with this end-to-end ID already exists"
            );
        }

        AccountResponse debtor =
                accountClient.getAccount(request.getSourceAccountId());

        if (debtor == null || !"ACTIVE".equals(debtor.status())) {
            throw new BusinessException("Source account is not active");
        }

        if (!request.getCurrency().name().equals(debtor.currency())) {
            throw new BusinessException(
                    "Source account currency does not match the payment currency"
            );
        }

        FeeAssessment fee = feeService.assess(
                paymentType,
                request.getCurrency(),
                request.getAmount(),
                chargeBearer,
                PaymentDirection.OUTBOUND,
                businessCalendar.today()
        );

        BigDecimal totalDebit = fee.totalDebitFor(request.getAmount());

        if (debtor.balance().compareTo(totalDebit) < 0) {
            throw new BusinessException(
                    "Insufficient funds: %s %s required including charges"
                            .formatted(totalDebit, request.getCurrency())
            );
        }

        Transaction transaction =
                createTransaction(request, paymentType, chargeBearer, fee, debtor);

        transaction = transactionRepository.save(transaction);

        auditService.record(
                AuditEventType.PAYMENT_INITIATED,
                transaction.getTransactionReference(),
                "Transaction",
                transaction.getTransactionReference(),
                "%s payment of %s %s to %s initiated".formatted(
                        paymentType.getDisplayName(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getCreditorIban()
                ),
                Map.of(
                        "paymentType", paymentType.getCode(),
                        "amount", request.getAmount(),
                        "currency", request.getCurrency().name(),
                        "creditorIban", request.getCreditorIban(),
                        "creditorAgentBic",
                        String.valueOf(request.getCreditorAgentBic()),
                        "endToEndId", request.getEndToEndId()
                )
        );

        /*
         * A payment at or above the threshold stops here: nothing is booked and
         * no message is built until a second person releases it, so an
         * unapproved payment leaves no trace on the ledger.
         */
        if (approvalService.requiresApproval(transaction)) {
            return approvalService.park(transaction);
        }

        return execute(transaction);
    }

    /**
     * Books and dispatches a payment that is cleared to proceed.
     *
     * Reached either directly, when no approval was needed, or from the
     * approval service once a second person has released it — the same path
     * either way, so an approved payment is not handled differently from an
     * ordinary one.
     */
    @Transactional
    public Transaction execute(Transaction transaction) {

        /*
         * Re-check at the moment of booking. A payment held for approval may
         * have been affordable when it was instructed and not by the time it is
         * released.
         */
        fundsCheck.assertCanCover(transaction);

        FeeAssessment fee = ApprovalService.storedFee(transaction);

        /*
         * Build the scheme message before booking. If the instruction cannot be
         * expressed in a form KIPS accepts, nothing should move.
         */
        PacsMessage message =
                pacsMessageService.generateCreditTransfer(transaction);

        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction = transactionRepository.save(transaction);

        Long suspenseId =
                suspenseAccounts.suspenseAccountId(transaction.getCurrency());

        book(transaction, fee, suspenseId);

        /*
         * Handed to the scheme. Until a status report arrives the payment is in
         * flight, and its value sits in suspense.
         */
        message.setStatus(PacsMessageStatus.SENT);

        transaction.setStatus(TransactionStatus.SENT);
        transaction = transactionRepository.save(transaction);

        auditService.record(
                AuditEventType.PAYMENT_SENT,
                transaction.getTransactionReference(),
                "Transaction",
                transaction.getTransactionReference(),
                "pacs.008 %s dispatched; %s %s held in suspense pending settlement"
                        .formatted(
                                message.getMessageId(),
                                transaction.getAmount(),
                                transaction.getCurrency()
                        ),
                Map.of(
                        "messageId", message.getMessageId(),
                        "suspenseAccountId", suspenseId,
                        "amountInFlight", transaction.getAmount()
                )
        );

        return transaction;
    }

    private void validate(
            TransferRequest request,
            PaymentType paymentType,
            ChargeBearer chargeBearer) {

        if (paymentType == null || !paymentType.isExternal()) {
            throw new BusinessException(
                    "Not an outbound payment type"
            );
        }

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

        if (request.getSourceAccountId() == null) {
            throw new BusinessException("Source account is required");
        }

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException(
                    "Transaction amount must be greater than zero"
            );
        }

        if (isBlank(request.getCreditorName())) {
            throw new BusinessException(
                    "Creditor name is required: the scheme mandates Cdtr/Nm"
            );
        }

        /*
         * The customer self-service route reaches here too, so this is the
         * check that stops a beneficiary IBAN nobody at the counter ever saw.
         */
        fieldValidator.validate(
                request,
                request.getPaymentType(),
                true
        );
    }

    private Transaction createTransaction(
            TransferRequest request,
            PaymentType paymentType,
            ChargeBearer chargeBearer,
            FeeAssessment fee,
            AccountResponse debtor) {

        Transaction transaction = new Transaction();

        transaction.setTransactionReference(newReference());
        transaction.setEndToEndId(request.getEndToEndId());
        transaction.setInstructionId(request.getInstructionId());
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setPaymentType(paymentType);
        transaction.setDirection(PaymentDirection.OUTBOUND);
        transaction.setServiceLevel(paymentType.getServiceLevel());
        transaction.setChargeBearer(chargeBearer);
        transaction.setPurposeCode(request.getPurposeCode());
        transaction.setRemittanceInformation(request.getRemittanceInformation());

        transaction.setSourceAccountId(request.getSourceAccountId());
        transaction.setDestinationAccountId(null);

        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());

        transaction.setDebtorIban(debtor.iban());

        /*
         * Taken from the account holder, not the request. The payer's address
         * is a fact about them rather than something an instruction gets to
         * assert, and it is what goes into the message's PstlAdr.
         */
        transaction.setDebtorAddressLine1(debtor.clientAddressLine1());
        transaction.setDebtorAddressLine2(debtor.clientAddressLine2());
        transaction.setDebtorCity(debtor.clientCity());
        transaction.setDebtorPostalCode(debtor.clientPostalCode());
        transaction.setDebtorCountry(debtor.clientCountry());

        /*
         * The scheme requires a debtor name and it must be the account holder's,
         * not whatever the form happened to carry. Falling back to the IBAN
         * would put an account number where a person's name belongs.
         */
        transaction.setDebtorName(
                firstNonBlank(
                        request.getDebtorName(),
                        debtor.clientName(),
                        debtor.iban()
                )
        );

        transaction.setCreditorName(request.getCreditorName());
        transaction.setCreditorIban(request.getCreditorIban());
        transaction.setCreditorAgentBic(request.getCreditorAgentBic());
        transaction.setCreditorCountry(
                normaliseCountry(request.getCreditorCountry()));

        transaction.setFeeAmount(fee.totalFee());
        transaction.setDebtorFeeAmount(fee.debtorFee());
        transaction.setCreditorFeeAmount(fee.creditorFee());

        LocalDate today = businessCalendar.today();

        transaction.setBookingDate(today);
        transaction.setValueDate(today);
        transaction.setStatus(TransactionStatus.PENDING);

        if (paymentType == PaymentType.KIPS_RTGS) {
            transaction.setUetr(messageIdGenerator.newUetr());
        }

        try {
            AuthenticatedUser user = SecurityUtils.getCurrentUser();
            transaction.setCreatedByUserId(user.userId());
            transaction.setCreatedByUsername(user.username());
        } catch (IllegalStateException ignored) {
            // No principal; leave the maker fields empty rather than guess.
        }

        return transaction;
    }

    /**
     * Books the outbound legs. Debits and credits are posted through the shared
     * poster so every movement is audited and the pair is proved to net to zero.
     */
    private void book(
            Transaction transaction,
            FeeAssessment fee,
            Long suspenseAccountId) {

        String reference = transaction.getTransactionReference();
        BigDecimal amount = transaction.getAmount();
        Currency currency = transaction.getCurrency();

        /*
         * The payment and the charge are booked separately against the debtor.
         *
         * One combined debit of amount-plus-fee balances just as well, but it
         * leaves the customer a figure they cannot explain: their statement
         * shows 200.50 leaving for a payment of 200.00, and the difference
         * exists only on the bank's income account where they cannot see it.
         * A charge is a real movement and earns its own line.
         */
        ledgerPoster.debit(
                transaction,
                transaction.getSourceAccountId(),
                amount,
                reference + "-DEBIT"
        );

        ledgerPoster.credit(
                transaction,
                suspenseAccountId,
                amount,
                reference + "-SUSPENSE"
        );

        if (fee.debtorFee().compareTo(BigDecimal.ZERO) > 0) {

            ledgerPoster.debit(
                    transaction,
                    transaction.getSourceAccountId(),
                    fee.debtorFee(),
                    reference + "-FEE-DEBIT"
            );

            ledgerPoster.credit(
                    transaction,
                    suspenseAccounts.incomeAccountId(currency),
                    fee.debtorFee(),
                    reference + "-FEE"
            );

            auditService.record(
                    AuditEventType.FEE_CHARGED,
                    reference,
                    "Transaction",
                    reference,
                    "Charge of %s %s taken from the debtor".formatted(
                            fee.debtorFee(), currency
                    ),
                    Map.of(
                            "fee", fee.debtorFee(),
                            "ruleCode", String.valueOf(fee.ruleCode())
                    )
            );
        }

        Map<String, Object> posting = new LinkedHashMap<>();

        posting.put("debtorDebitedForPayment", amount);
        posting.put("debtorDebitedForCharge", fee.debtorFee());
        posting.put("suspenseCredited", amount);
        posting.put("incomeCredited", fee.debtorFee());
        posting.put("currency", currency.name());
        posting.put("net", BigDecimal.ZERO);

        auditService.record(
                AuditEventType.LEDGER_ENTRY_POSTED,
                reference,
                "LedgerEntry",
                reference,
                ("Outbound legs posted: DR debtor %s + charge %s / CR suspense %s "
                        + "+ income %s, net %s").formatted(
                        amount,
                        fee.debtorFee(),
                        amount,
                        fee.debtorFee(),
                        BigDecimal.ZERO
                ),
                posting
        );
    }

    private String newReference() {

        return "TXN-" + LocalDate.now() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {

        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }

        return null;
    }

    /** Stored upper-case, because it is compared and written into the message. */
    private String normaliseCountry(String country) {
        return country == null || country.isBlank()
                ? null : country.trim().toUpperCase();
    }
}
