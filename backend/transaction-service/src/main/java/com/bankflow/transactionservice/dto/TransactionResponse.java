package com.bankflow.transactionservice.dto;

import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TransactionResponse {

    private Long id;
    private String transactionReference;
    private String endToEndId;
    private String instructionId;

    private TransactionType transactionType;
    private TransactionStatus status;

    private Long sourceAccountId;
    private Long destinationAccountId;

    private BigDecimal amount;
    private String currency;

    private LocalDate bookingDate;
    private LocalDate valueDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TransactionResponse fromEntity(
            Transaction transaction) {

        TransactionResponse response =
                new TransactionResponse();

        response.id = transaction.getId();
        response.transactionReference =
                transaction.getTransactionReference();

        response.endToEndId =
                transaction.getEndToEndId();

        response.instructionId =
                transaction.getInstructionId();

        response.transactionType =
                transaction.getTransactionType();

        response.status =
                transaction.getStatus();

        response.sourceAccountId =
                transaction.getSourceAccountId();

        response.destinationAccountId =
                transaction.getDestinationAccountId();

        response.amount =
                transaction.getAmount();

        response.currency =
                transaction.getCurrency().name();

        response.bookingDate =
                transaction.getBookingDate();

        response.valueDate =
                transaction.getValueDate();

        response.createdAt =
                transaction.getCreatedAt();

        response.updatedAt =
                transaction.getUpdatedAt();

        return response;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}