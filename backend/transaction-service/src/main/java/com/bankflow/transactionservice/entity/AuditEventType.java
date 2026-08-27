package com.bankflow.transactionservice.entity;

/**
 * Business events worth answering to a regulator for.
 *
 * These describe what happened in banking terms, not what changed in a table.
 */
public enum AuditEventType {

    PAYMENT_INITIATED,
    PAYMENT_VALIDATED,
    PAYMENT_SUBMITTED_FOR_APPROVAL,
    PAYMENT_APPROVED,
    PAYMENT_DECLINED,

    FEE_ASSESSED,
    FEE_CHARGED,

    ACCOUNT_DEBITED,
    ACCOUNT_CREDITED,
    LEDGER_ENTRY_POSTED,

    PAYMENT_SENT,
    PAYMENT_SETTLED,
    PAYMENT_REJECTED,
    PAYMENT_RETURNED,
    PAYMENT_FAILED,
    PAYMENT_REVERSED,

    /** A recall was asked for, agreed to, refused, or arrived from a peer. */
    RECALL_REQUESTED,
    RECALL_RECEIVED,
    RECALL_ACCEPTED,
    RECALL_REJECTED,

    PACS_MESSAGE_GENERATED,
    PACS_MESSAGE_RECEIVED,

    /** The list of banks that can be paid was changed. */
    BANK_DIRECTORY_CHANGED,

    TARIFF_RULE_CREATED,
    TARIFF_RULE_CHANGED
}
