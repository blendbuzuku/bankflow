/*
 * What an account is for.
 *
 * A client may legitimately hold more than one current account in the same
 * currency — bills kept apart from spending, a joint account beside a sole one.
 * What is not legitimate is holding several that nobody can tell apart, which
 * is what happens when opening the same product twice silently succeeds.
 *
 * The purpose is what makes a second account a deliberate act rather than an
 * accident: without one, a duplicate of the same type and currency is refused.
 */
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(60);

/*
 * Remediate what was opened before the rule existed.
 *
 * Duplicates that never held money are closed, keeping the earliest of each
 * group — closing an empty account moves nothing, so the ledger is untouched
 * and the day still nets to zero.
 */
UPDATE accounts a
SET status = 'CLOSED',
    updated_at = NOW()
WHERE a.status <> 'CLOSED'
  AND a.balance = 0
  AND EXISTS (
      SELECT 1
      FROM accounts keeper
      WHERE keeper.client_id    = a.client_id
        AND keeper.account_type = a.account_type
        AND keeper.currency     = a.currency
        AND keeper.status      <> 'CLOSED'
        AND COALESCE(keeper.purpose, '') = COALESCE(a.purpose, '')
        AND (keeper.created_at, keeper.id) < (a.created_at, a.id)
  );

/*
 * Anything still duplicated holds a balance, so it cannot simply be closed.
 * Give it a purpose derived from its own account number: the pair stays
 * distinguishable and nothing is lost, and staff can rename it afterwards.
 */
UPDATE accounts a
SET purpose = 'Account ' || RIGHT(a.account_number, 4),
    updated_at = NOW()
WHERE a.status <> 'CLOSED'
  AND a.purpose IS NULL
  AND EXISTS (
      SELECT 1
      FROM accounts other
      WHERE other.client_id    = a.client_id
        AND other.account_type = a.account_type
        AND other.currency     = a.currency
        AND other.status      <> 'CLOSED'
        AND other.purpose IS NULL
        AND other.id <> a.id
        AND (other.created_at, other.id) < (a.created_at, a.id)
  );

/*
 * A client may not hold two open accounts of the same type and currency
 * sharing a purpose, and only one with none. Postgres treats NULLs as distinct
 * in a unique index, so the coalesce collapses "no purpose" into a single value
 * the constraint can actually catch.
 *
 * Closed accounts are excluded: a purpose has to be reusable once the account
 * it belonged to is shut.
 */
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_client_product_purpose
    ON accounts (client_id, account_type, currency, COALESCE(purpose, ''))
    WHERE status <> 'CLOSED';
