-- Backfills the operation id on ledger entries written before the link existed.
--
-- Operation ids are derived from the transaction reference with a suffix
-- naming the leg (-DEBIT, -CREDIT, -SUSPENSE, -FEE, -RETURN-*), so an entry can
-- be matched to its movement by transaction reference, account, amount and
-- direction without guessing.
--
-- Deliberately conservative: only rows with exactly one candidate are filled.
-- An ambiguous match is left null and reported as unverifiable, which is
-- honest, where a wrong match would silently manufacture a clean reconciliation.

UPDATE ledger_entries le
   SET operation_id = candidate.operation_id
  FROM (
        SELECT le2.id                AS ledger_id,
               MIN(bo.operation_id)  AS operation_id,
               COUNT(*)              AS candidates
          FROM ledger_entries le2
          JOIN transactions   t  ON t.id = le2.transaction_id
          JOIN balance_operations bo
            ON bo.account_id = le2.account_id
           AND bo.amount     = le2.amount
           AND bo.operation  = le2.entry_type
           AND bo.operation_id LIKE t.transaction_reference || '%'
         WHERE le2.operation_id IS NULL
         GROUP BY le2.id
       ) AS candidate
 WHERE le.id = candidate.ledger_id
   AND candidate.candidates = 1;
