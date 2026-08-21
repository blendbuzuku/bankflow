package com.bankflow.accountservice.entity;

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED,
    REVERSED
}