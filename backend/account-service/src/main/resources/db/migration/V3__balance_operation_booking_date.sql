/*
 * The day a movement belongs to, as distinct from the moment it was recorded.
 *
 * Reconciliation pairs these movements against ledger entries, and the ledger
 * books to the bank's business date rather than to the clock. Once a day is
 * closed the two diverge — work done that evening is the next day's business —
 * and matching on the timestamp would report every one of those movements as a
 * break, when nothing is wrong at all.
 *
 * Existing rows are backfilled from the moment they were recorded, which is
 * what they meant before the distinction existed.
 */
ALTER TABLE balance_operations
    ADD COLUMN IF NOT EXISTS booking_date date;

UPDATE balance_operations
SET booking_date = created_at::date
WHERE booking_date IS NULL;

ALTER TABLE balance_operations
    ALTER COLUMN booking_date SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_balance_operations_booking_date
    ON balance_operations (booking_date);
