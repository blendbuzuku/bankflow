-- Correcting journal for balance movements that never reached the ledger.
--
-- Incident, 2026-08-26: an inbound RTGS payment booked its legs and then failed
-- while building the acknowledgement. Balances live in account-service and had
-- already committed, so this service's rollback could not undo them — the money
-- moved with no ledger entry behind it, and the trial balance still read
-- "balanced" because a ledger missing both sides of a movement still nets to
-- zero. The movements were reversed by hand the same day.
--
-- The cause is fixed: both payment paths now build and validate their outgoing
-- message before booking anything.
--
-- What remains is the record. The erroneous movements and their reversal both
-- genuinely happened and are visible in balance_operations, so the ledger has
-- to show them. An error is not erased; it is recorded together with its
-- correction, and the day then reconciles honestly rather than by pretending
-- nothing occurred.
--
-- Written to apply only where those exact movements exist and are still
-- unrecorded, so it is inert on a fresh database.

INSERT INTO transactions (
    transaction_reference, end_to_end_id, instruction_id,
    transaction_type, payment_type, direction, charge_bearer, status,
    source_account_id, destination_account_id,
    amount, currency, booking_date, value_date,
    rejection_reason, created_at, updated_at
)
SELECT
    'TXN-CORRECTION-20260826-01',
    'CORRECTION-20260826-01',
    'CORRECTION-20260826-01',
    'REVERSAL', 'KIPS_RTGS', 'INBOUND', 'SLEV', 'COMPLETED',
    NULL, 3,
    250000.00, 'EUR', DATE '2026-08-26', DATE '2026-08-26',
    'Correcting journal: unrecorded movements from failed inbound TXN-2026-08-26-77F51F37 and their manual reversal',
    now(), now()
WHERE EXISTS (
        SELECT 1 FROM balance_operations
         WHERE operation_id = 'TXN-2026-08-26-77F51F37-CREDIT'
      )
  AND NOT EXISTS (
        SELECT 1 FROM transactions
         WHERE transaction_reference = 'TXN-CORRECTION-20260826-01'
      );

-- One ledger entry per movement, each carrying the operation id it records so
-- reconciliation can match it.
INSERT INTO ledger_entries (
    transaction_id, entry_reference, account_id, operation_id,
    entry_type, amount, currency, booking_date, value_date, created_at
)
SELECT
    t.id,
    'LED-CORR-20260826-' || m.suffix,
    m.account_id,
    m.operation_id,
    m.entry_type,
    250000.00,
    'EUR',
    DATE '2026-08-26',
    DATE '2026-08-26',
    now()
  FROM transactions t
  CROSS JOIN (VALUES
        ('01', 5, 'TXN-2026-08-26-77F51F37-SUSPENSE', 'DEBIT'),
        ('02', 3, 'TXN-2026-08-26-77F51F37-CREDIT',   'CREDIT'),
        ('03', 3, 'REPAIR-RTGSINB-REVERSE-CDTR',      'DEBIT'),
        ('04', 5, 'REPAIR-RTGSINB-REVERSE-SUSP',      'CREDIT')
       ) AS m(suffix, account_id, operation_id, entry_type)
 WHERE t.transaction_reference = 'TXN-CORRECTION-20260826-01'
   AND EXISTS (
        SELECT 1 FROM balance_operations bo
         WHERE bo.operation_id = m.operation_id
       )
   AND NOT EXISTS (
        SELECT 1 FROM ledger_entries le
         WHERE le.operation_id = m.operation_id
       );
