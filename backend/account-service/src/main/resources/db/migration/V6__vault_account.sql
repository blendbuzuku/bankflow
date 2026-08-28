/*
 * Give cash somewhere of its own to sit.
 *
 * A deposit at the counter was booking its balancing leg to suspense, which
 * was the only internal account that could take it. Suspense means one thing:
 * value that has left one party and not yet reached the other, waiting on the
 * scheme to say which. It clears when a pacs.002 arrives.
 *
 * A banknote handed across a counter is not in flight and never clears. So
 * every deposit left a residue in suspense that nothing could resolve, and the
 * suspense balance stopped meaning anything: it mixed cash sitting in a drawer
 * with payments awaiting the scheme, which is the one question the account
 * exists to answer.
 *
 * The vault is an asset account holding one thing: notes over the counter.
 * That makes its movements countable against a drawer, which is the whole
 * point -- suspense, holding cash and in-flight payments together, never was.
 *
 * Its stored balance runs negative. The balance column is kept
 * credits-minus-debits, which is right for a customer account because the bank
 * owes the holder; an asset is the other way round, so a drawer holding 380
 * reads as -380. The ledger nets to zero regardless.
 *
 * The accounts themselves are not inserted here. InternalAccountSeeder creates
 * one per currency at startup, generating a proper IBAN for each, and is
 * idempotent. Writing them in SQL would mean duplicating the IBAN check-digit
 * calculation in a migration.
 */
ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_account_type_check;

ALTER TABLE accounts
    ADD CONSTRAINT accounts_account_type_check
    CHECK (account_type IN ('CURRENT', 'SAVINGS', 'SUSPENSE', 'INCOME', 'VAULT'));
