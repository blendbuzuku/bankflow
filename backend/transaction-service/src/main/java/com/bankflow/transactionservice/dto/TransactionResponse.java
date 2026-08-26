package com.bankflow.transactionservice.dto;

import com.bankflow.transactionservice.entity.ChargeBearer;
import com.bankflow.transactionservice.entity.PaymentDirection;
import com.bankflow.transactionservice.entity.PaymentType;
import com.bankflow.transactionservice.entity.PurposeCode;
import com.bankflow.transactionservice.entity.ServiceLevel;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A payment as the outside world sees it.
 *
 * Carries the classification and party details as well as the amounts, because
 * a screen showing a payment for approval has to say what kind of payment it is
 * and who it is going to — an amount and a reference alone are not enough to
 * decide on.
 */
public record TransactionResponse(
        Long id,
        String transactionReference,
        String endToEndId,
        String instructionId,
        String uetr,

        TransactionType transactionType,
        PaymentType paymentType,
        String paymentTypeCode,
        String paymentTypeName,
        PaymentDirection direction,
        ServiceLevel serviceLevel,
        ChargeBearer chargeBearer,
        PurposeCode purposeCode,
        TransactionStatus status,

        Long sourceAccountId,
        Long destinationAccountId,

        BigDecimal amount,
        String currency,

        String debtorName,
        String debtorIban,
        String debtorAgentBic,
        String creditorName,
        String creditorIban,
        String creditorAgentBic,
        String remittanceInformation,

        BigDecimal feeAmount,
        BigDecimal debtorFeeAmount,
        BigDecimal creditorFeeAmount,

        String createdByUsername,
        String approvedByUsername,
        LocalDateTime approvedAt,
        String rejectionReason,
        String reasonCode,

        LocalDate bookingDate,
        LocalDate valueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TransactionResponse fromEntity(Transaction transaction) {

        PaymentType paymentType = transaction.getPaymentType();

        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionReference(),
                transaction.getEndToEndId(),
                transaction.getInstructionId(),
                transaction.getUetr(),

                transaction.getTransactionType(),
                paymentType,
                paymentType == null ? null : paymentType.getCode(),
                paymentType == null ? null : paymentType.getDisplayName(),
                transaction.getDirection(),
                transaction.getServiceLevel(),
                transaction.getChargeBearer(),
                transaction.getPurposeCode(),
                transaction.getStatus(),

                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),

                transaction.getAmount(),
                transaction.getCurrency() == null
                        ? null
                        : transaction.getCurrency().name(),

                transaction.getDebtorName(),
                transaction.getDebtorIban(),
                transaction.getDebtorAgentBic(),
                transaction.getCreditorName(),
                transaction.getCreditorIban(),
                transaction.getCreditorAgentBic(),
                transaction.getRemittanceInformation(),

                transaction.getFeeAmount(),
                transaction.getDebtorFeeAmount(),
                transaction.getCreditorFeeAmount(),

                transaction.getCreatedByUsername(),
                transaction.getApprovedByUsername(),
                transaction.getApprovedAt(),
                transaction.getRejectionReason(),
                transaction.getReasonCode(),

                transaction.getBookingDate(),
                transaction.getValueDate(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
