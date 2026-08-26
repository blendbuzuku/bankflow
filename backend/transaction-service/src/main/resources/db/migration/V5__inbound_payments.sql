-- An inbound payment has no source account of ours.
--
-- The payer banks elsewhere and is identified by debtor IBAN and agent BIC,
-- exactly as an outbound payment's creditor is. destination_account_id was
-- already relaxed for that direction in V3; this is the mirror image.
--
-- Exactly one side is always ours: outbound holds a source, inbound holds a
-- destination, and an on-us transfer holds both.

ALTER TABLE transactions ALTER COLUMN source_account_id DROP NOT NULL;

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_has_own_account;
ALTER TABLE transactions ADD CONSTRAINT transactions_has_own_account
    CHECK (source_account_id IS NOT NULL OR destination_account_id IS NOT NULL);
