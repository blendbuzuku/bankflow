-- Links each ledger entry to the balance operation it caused.
--
-- Balances live in account-service and commit independently of this service's
-- transaction, so a failure between the two can leave money moved with no
-- ledger record behind it. The trial balance cannot see that: it only proves
-- the ledger is internally consistent, and a ledger that is missing both sides
-- of a movement still nets to zero.
--
-- Recording the operation id makes the two sets comparable movement by
-- movement, so reconciliation can name exactly which movement is unmatched
-- rather than only reporting that a total is wrong.
--
-- Nullable: entries written before this column existed have no operation id and
-- are reported as unverifiable rather than as breaks.

ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS operation_id varchar(100);

CREATE INDEX IF NOT EXISTS idx_ledger_operation_id
    ON ledger_entries (operation_id);
