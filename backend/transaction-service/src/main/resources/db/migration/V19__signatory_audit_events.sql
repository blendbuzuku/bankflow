/*
 * Admit the signatory events to the audit trail.
 *
 * Granting somebody authority to instruct payments on an account that is not
 * theirs is among the more consequential things a bank does without any money
 * moving, and it belongs on the record beside the payments it later explains.
 */
ALTER TABLE audit_events
    DROP CONSTRAINT IF EXISTS audit_events_event_type_check;

ALTER TABLE audit_events
    ADD CONSTRAINT audit_events_event_type_check
    CHECK (event_type IN (
        'PAYMENT_INITIATED', 'PAYMENT_VALIDATED', 'PAYMENT_SUBMITTED_FOR_APPROVAL',
        'PAYMENT_APPROVED', 'PAYMENT_DECLINED', 'FEE_ASSESSED', 'FEE_CHARGED',
        'ACCOUNT_DEBITED', 'ACCOUNT_CREDITED', 'LEDGER_ENTRY_POSTED',
        'PAYMENT_SENT', 'PAYMENT_SETTLED', 'PAYMENT_REJECTED', 'PAYMENT_RETURNED',
        'PAYMENT_FAILED', 'PAYMENT_REVERSED',
        'RECALL_REQUESTED', 'RECALL_RECEIVED', 'RECALL_ACCEPTED', 'RECALL_REJECTED',
        'PACS_MESSAGE_GENERATED', 'PACS_MESSAGE_RECEIVED',
        'BANK_DIRECTORY_CHANGED',
        'DAY_CLOSED', 'DAY_CLOSE_REFUSED',
        'TARIFF_RULE_CREATED', 'TARIFF_RULE_CHANGED',
        'CLIENT_REGISTERED', 'CLIENT_APPROVED', 'CLIENT_SUSPENDED',
        'ACCOUNT_OPENED', 'ACCOUNT_CLOSED',
        'SIGNATORY_ADDED', 'SIGNATORY_REMOVED'
    ));
