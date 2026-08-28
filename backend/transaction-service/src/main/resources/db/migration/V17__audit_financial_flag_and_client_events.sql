/*
 * Split the audit trail into money and everything else.
 *
 * The trail already mixed two kinds of event that read alike and answer to
 * different questions: a ledger posting is money leaving somebody's account,
 * while a recall request or a day close is a decision about money that moved
 * nothing. Told apart only by reading each event type and knowing which is
 * which, the trail could not answer "what did this account actually do"
 * without a hand-maintained list of type names in the query.
 *
 * One column settles it. It is derived from the event type in code and has no
 * setter of its own, so a row cannot claim to be something its type is not.
 *
 * The same column is what lets the non-financial half grow: admitting client
 * approvals below would otherwise have buried the money movements under
 * administrative noise.
 */
ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS financial BOOLEAN NOT NULL DEFAULT FALSE;

/*
 * Backfill by type, so history reads the same as anything written from now on.
 *
 * PAYMENT_SENT counts: the debtor is debited and suspense credited before the
 * message leaves. PAYMENT_FAILED does not -- it records that nothing happened.
 */
UPDATE audit_events
   SET financial = TRUE
 WHERE event_type IN (
        'FEE_CHARGED',
        'ACCOUNT_DEBITED',
        'ACCOUNT_CREDITED',
        'LEDGER_ENTRY_POSTED',
        'PAYMENT_SENT',
        'PAYMENT_SETTLED',
        'PAYMENT_REJECTED',
        'PAYMENT_RETURNED',
        'PAYMENT_REVERSED'
    );

/*
 * Drop the default now the existing rows are settled. Every insert comes from
 * the entity, which always states the column; leaving a default of FALSE would
 * mean a future write that forgot it recorded a settlement as moving no money,
 * and that is exactly the row nobody would think to check.
 */
ALTER TABLE audit_events
    ALTER COLUMN financial DROP DEFAULT;

CREATE INDEX IF NOT EXISTS idx_audit_events_financial
    ON audit_events (financial, occurred_at DESC);

/*
 * Admit the events that decide who may bank here at all.
 *
 * A client being approved is the moment somebody at this bank vouched that the
 * person is who they claim to be, and every payment they later make rests on
 * it. Until now that decision left only a status column, which shows what a
 * client is and never who decided it or when.
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
        'ACCOUNT_OPENED', 'ACCOUNT_CLOSED'
    ));
