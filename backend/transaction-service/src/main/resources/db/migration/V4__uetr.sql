-- Unique End-to-end Transaction Reference.
--
-- A UUIDv4 that follows an RTGS payment for its entire life, including status
-- reports and returns sent back about it by a counterparty. KIPS RTGS carries
-- it in PmtId/UETR; ACH has no equivalent and uses TxId, so the column is
-- nullable rather than required.

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS uetr varchar(36);

-- Not unique-constrained: an inbound return or status report legitimately
-- reuses the original payment's UETR, and later phases will store those.
CREATE INDEX IF NOT EXISTS idx_transactions_uetr ON transactions (uetr);
