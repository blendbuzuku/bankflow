package com.bankflow.transactionservice.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "transaction_reference"),
                @UniqueConstraint(columnNames = "end_to_end_id")
        }
)
public class Transaction {

    /** Currencies here carry two minor units. */
    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Internal banking transaction reference.
     *
     * Example:
     * TXN-20260820-000001
     */
    @Column(
            name = "transaction_reference",
            nullable = false,
            unique = true,
            length = 40
    )
    private String transactionReference;

    /**
     * End-to-end payment identifier.
     *
     * Used to trace a payment through its complete lifecycle.
     */
    @Column(
            name = "end_to_end_id",
            nullable = false,
            unique = true,
            length = 50
    )
    private String endToEndId;

    /**
     * Identifier of the original payment instruction.
     */
    @Column(
            name = "instruction_id",
            nullable = false,
            length = 50
    )
    private String instructionId;

    /**
     * Unique End-to-end Transaction Reference — a UUIDv4 that stays with an
     * RTGS payment for its whole life, including any status report or return a
     * counterparty sends back about it.
     *
     * Only RTGS carries one; ACH identifies transactions with TxId instead.
     */
    @Column(name = "uetr", length = 36)
    private String uetr;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "transaction_type",
            nullable = false,
            length = 20
    )
    private TransactionType transactionType;

    /**
     * The rail this payment travels on. Drives routing and price.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_type",
            nullable = false,
            length = 30
    )
    private PaymentType paymentType;

    /**
     * Whether the payment stays inside the bank, leaves it, or arrives.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "direction",
            nullable = false,
            length = 10
    )
    private PaymentDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "service_level",
            length = 10
    )
    private ServiceLevel serviceLevel;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "charge_bearer",
            nullable = false,
            length = 10
    )
    private ChargeBearer chargeBearer;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "purpose_code",
            length = 10
    )
    private PurposeCode purposeCode;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private TransactionStatus status;

    /**
     * Source account.
     *
     * This is an ID belonging to account-service.
     * We deliberately do not create a JPA relationship
     * because Account belongs to another microservice.
     */
    /**
     * Debtor account, when we hold it.
     *
     * Null for inbound payments: the payer banks elsewhere and is identified by
     * the debtor IBAN and agent BIC instead. Mirrors
     * {@link #destinationAccountId}, which is null for outbound payments.
     */
    @Column(name = "source_account_id")
    private Long sourceAccountId;

    /**
     * Destination account, when we hold it.
     *
     * Null for outbound payments: an external creditor has an IBAN and an agent
     * BIC, but no row in our database. The creditor party fields below carry
     * that case.
     */
    @Column(name = "destination_account_id")
    private Long destinationAccountId;

    // --- ISO 20022 party block -------------------------------------------
    // These are the fields a teller fills in and that a pacs.008 carries.

    @Column(name = "debtor_name", length = 140)
    private String debtorName;

    @Column(name = "debtor_iban", length = 34)
    private String debtorIban;

    @Column(name = "debtor_agent_bic", length = 11)
    private String debtorAgentBic;

    /*
     * The debtor's address as it stood when the payment was made.
     *
     * Copied onto the transaction rather than looked up from the client each
     * time, because a message has to stay reproducible: regenerating the
     * pacs.008 for a payment sent last year must produce what was actually
     * sent, and the customer may have moved since.
     */
    @Column(name = "debtor_address_line1", length = 70)
    private String debtorAddressLine1;

    @Column(name = "debtor_address_line2", length = 70)
    private String debtorAddressLine2;

    @Column(name = "debtor_city", length = 35)
    private String debtorCity;

    @Column(name = "debtor_postal_code", length = 16)
    private String debtorPostalCode;

    @Column(name = "debtor_country", length = 2)
    private String debtorCountry;

    @Column(name = "creditor_name", length = 140)
    private String creditorName;

    @Column(name = "creditor_iban", length = 34)
    private String creditorIban;

    @Column(name = "creditor_agent_bic", length = 11)
    private String creditorAgentBic;

    /**
     * Where the money is going, as an ISO country code.
     *
     * Written into the creditor's PstlAdr. A domestic payment can infer it
     * from the rail, but an international one cannot, and it is what screening
     * against a destination country needs in order to have anything to run on.
     */
    @Column(name = "creditor_country", length = 2)
    private String creditorCountry;

    /**
     * Unstructured remittance information. ISO caps this at 140 characters.
     */
    @Column(name = "remittance_information", length = 140)
    private String remittanceInformation;

    @Column(
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 3
    )
    private Currency currency;

    /**
     * Date on which the transaction is booked.
     */
    @Column(
            name = "booking_date",
            nullable = false
    )
    private LocalDate bookingDate;

    /**
     * Date on which the funds are considered effective.
     */
    @Column(
            name = "value_date",
            nullable = false
    )
    private LocalDate valueDate;

    // --- charges ----------------------------------------------------------

    /**
     * Total fee assessed for this payment, in {@link #currency}.
     *
     * Recorded here for reporting; the money itself moves through a separate
     * FEE transaction so it appears as its own line on a statement.
     */
    @Column(name = "fee_amount", precision = 19, scale = 2)
    private BigDecimal feeAmount;

    /** Share of the fee charged to the debtor. */
    @Column(name = "debtor_fee_amount", precision = 19, scale = 2)
    private BigDecimal debtorFeeAmount;

    /** Share deducted from what the creditor receives. */
    @Column(name = "creditor_fee_amount", precision = 19, scale = 2)
    private BigDecimal creditorFeeAmount;

    /**
     * For a FEE, REVERSAL or RETURN, the payment that caused it.
     */
    @Column(name = "parent_transaction_id")
    private Long parentTransactionId;

    // --- four-eyes trail ---------------------------------------------------

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_by_username", length = 50)
    private String createdByUsername;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_by_username", length = 50)
    private String approvedByUsername;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    /**
     * ISO 20022 reason code when a payment is rejected or returned,
     * e.g. AC01 unknown account, AM04 insufficient funds.
     */
    @Column(name = "reason_code", length = 10)
    private String reasonCode;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Transaction() {
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(Long destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Normalises the scale as the amount comes in.
     *
     * A BigDecimal parsed from the JSON number {@code 2500} carries scale zero
     * and renders as "2500" everywhere downstream — in the scheme message, the
     * audit trail and the ledger. An interbank instruction stating an amount
     * without its minor units reads as a rounding error, so the scale is fixed
     * once here rather than formatted at each of the places that display it.
     */
    public void setAmount(BigDecimal amount) {

        this.amount = amount == null
                ? null
                : amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public String getUetr() {
        return uetr;
    }

    public void setUetr(String uetr) {
        this.uetr = uetr;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public PaymentDirection getDirection() {
        return direction;
    }

    public void setDirection(PaymentDirection direction) {
        this.direction = direction;
    }

    public ServiceLevel getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(ServiceLevel serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public ChargeBearer getChargeBearer() {
        return chargeBearer;
    }

    public void setChargeBearer(ChargeBearer chargeBearer) {
        this.chargeBearer = chargeBearer;
    }

    public PurposeCode getPurposeCode() {
        return purposeCode;
    }

    public void setPurposeCode(PurposeCode purposeCode) {
        this.purposeCode = purposeCode;
    }

    public String getDebtorName() {
        return debtorName;
    }

    public void setDebtorName(String debtorName) {
        this.debtorName = debtorName;
    }

    public String getDebtorIban() {
        return debtorIban;
    }

    public void setDebtorIban(String debtorIban) {
        this.debtorIban = debtorIban;
    }

    public String getDebtorAgentBic() {
        return debtorAgentBic;
    }

    public void setDebtorAgentBic(String debtorAgentBic) {
        this.debtorAgentBic = debtorAgentBic;
    }

    public String getDebtorAddressLine1() {
        return debtorAddressLine1;
    }

    public void setDebtorAddressLine1(String debtorAddressLine1) {
        this.debtorAddressLine1 = debtorAddressLine1;
    }

    public String getDebtorAddressLine2() {
        return debtorAddressLine2;
    }

    public void setDebtorAddressLine2(String debtorAddressLine2) {
        this.debtorAddressLine2 = debtorAddressLine2;
    }

    public String getDebtorCity() {
        return debtorCity;
    }

    public void setDebtorCity(String debtorCity) {
        this.debtorCity = debtorCity;
    }

    public String getDebtorPostalCode() {
        return debtorPostalCode;
    }

    public void setDebtorPostalCode(String debtorPostalCode) {
        this.debtorPostalCode = debtorPostalCode;
    }

    public String getDebtorCountry() {
        return debtorCountry;
    }

    public void setDebtorCountry(String debtorCountry) {
        this.debtorCountry = debtorCountry;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public void setCreditorName(String creditorName) {
        this.creditorName = creditorName;
    }

    public String getCreditorIban() {
        return creditorIban;
    }

    public void setCreditorIban(String creditorIban) {
        this.creditorIban = creditorIban;
    }

    public String getCreditorAgentBic() {
        return creditorAgentBic;
    }

    public void setCreditorAgentBic(String creditorAgentBic) {
        this.creditorAgentBic = creditorAgentBic;
    }

    public String getCreditorCountry() {
        return creditorCountry;
    }

    public void setCreditorCountry(String creditorCountry) {
        this.creditorCountry = creditorCountry;
    }

    public String getRemittanceInformation() {
        return remittanceInformation;
    }

    public void setRemittanceInformation(String remittanceInformation) {
        this.remittanceInformation = remittanceInformation;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public BigDecimal getDebtorFeeAmount() {
        return debtorFeeAmount;
    }

    public void setDebtorFeeAmount(BigDecimal debtorFeeAmount) {
        this.debtorFeeAmount = debtorFeeAmount;
    }

    public BigDecimal getCreditorFeeAmount() {
        return creditorFeeAmount;
    }

    public void setCreditorFeeAmount(BigDecimal creditorFeeAmount) {
        this.creditorFeeAmount = creditorFeeAmount;
    }

    public Long getParentTransactionId() {
        return parentTransactionId;
    }

    public void setParentTransactionId(Long parentTransactionId) {
        this.parentTransactionId = parentTransactionId;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
    }

    public Long getApprovedByUserId() {
        return approvedByUserId;
    }

    public void setApprovedByUserId(Long approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }

    public String getApprovedByUsername() {
        return approvedByUsername;
    }

    public void setApprovedByUsername(String approvedByUsername) {
        this.approvedByUsername = approvedByUsername;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null) {
            status = TransactionStatus.PENDING;
        }

        if (chargeBearer == null) {
            chargeBearer = ChargeBearer.DEBT;
        }

        if (serviceLevel == null && paymentType != null) {
            serviceLevel = paymentType.getServiceLevel();
        }

        if (bookingDate == null) {
            bookingDate = LocalDate.now();
        }

        if (valueDate == null) {
            valueDate = bookingDate;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}