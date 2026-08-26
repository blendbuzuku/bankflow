-- Removes database-level coupling between transaction-service and
-- account-service.
--
-- ledger_entries.account_id used to carry a foreign key to accounts(id), left
-- behind by a duplicate LedgerEntry entity that account-service no longer has.
-- Accounts belong to another service: referencing them from here hard-couples
-- two services in the database and would block ever giving each service its own
-- schema. transaction-service's LedgerEntry deliberately maps account_id as a
-- plain Long for exactly this reason.
--
-- Named with the Hibernate-generated identifier because that is what exists on
-- databases created before Flyway was adopted. Fresh databases never create it,
-- so the drop is written defensively.

ALTER TABLE ledger_entries
    DROP CONSTRAINT IF EXISTS fkccr7q587j5bf49xqb0fqieefp;

-- Duplicate index on the same column, created when both services mapped this
-- table. idx_ledger_transaction_id (from transaction-service) is kept.
DROP INDEX IF EXISTS idx_ledger_transaction;
