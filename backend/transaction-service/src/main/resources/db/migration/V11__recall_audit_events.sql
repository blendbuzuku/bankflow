/*
 * Admit the recall events to the audit trail.
 *
 * A recall is four distinct moments and each has to be recordable: we asked,
 * we were asked, we agreed, we refused. Without them the trail would carry the
 * money movement of an accepted recall but not the decision that caused it,
 * which is the half a reviewer actually needs.
 *
 * TARIFF_RULE_CHANGED is kept although no code emits it yet — the constraint
 * is a guard against typos, not an index of what has happened so far, and
 * narrowing it would only cost another migration later.
 */
ALTER TABLE audit_events
    DROP CONSTRAINT IF EXISTS audit_events_event_type_check;

ALTER TABLE audit_events
    ADD CONSTRAINT audit_events_event_type_check
    CHECK (event_type IN (
        'PAYMENT_INITIATED',
        'PAYMENT_VALIDATED',
        'PAYMENT_SUBMITTED_FOR_APPROVAL',
        'PAYMENT_APPROVED',
        'PAYMENT_DECLINED',
        'FEE_ASSESSED',
        'FEE_CHARGED',
        'ACCOUNT_DEBITED',
        'ACCOUNT_CREDITED',
        'LEDGER_ENTRY_POSTED',
        'PAYMENT_SENT',
        'PAYMENT_SETTLED',
        'PAYMENT_REJECTED',
        'PAYMENT_RETURNED',
        'PAYMENT_FAILED',
        'PAYMENT_REVERSED',
        'RECALL_REQUESTED',
        'RECALL_RECEIVED',
        'RECALL_ACCEPTED',
        'RECALL_REJECTED',
        'PACS_MESSAGE_GENERATED',
        'PACS_MESSAGE_RECEIVED',
        'TARIFF_RULE_CREATED',
        'TARIFF_RULE_CHANGED'
    ));
