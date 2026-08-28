/*
 * Give a settled payment somewhere to land.
 *
 * Sending a payment credits suspense: the money has left the customer and not
 * yet reached anybody. Settlement was then recorded by changing a status and
 * booking nothing at all, so that credit stayed in suspense for ever.
 *
 * Suspense exists to answer one question -- how much has left our customers
 * and not yet arrived anywhere -- and that is exactly the question a balance
 * holding every payment ever completed cannot answer. It is the same defect
 * cash had before the vault: an account accumulating something that should
 * have moved on.
 *
 * The settlement account is the bank's own position at the central bank. When
 * the scheme settles, suspense is debited and this is credited, so suspense
 * returns to nil the moment a payment is answered for and this balance becomes
 * a figure with a meaning of its own.
 *
 * The accounts are created by InternalAccountSeeder at startup, one per
 * currency, so the IBAN check-digit calculation is not duplicated in SQL.
 */
ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_account_type_check;

ALTER TABLE accounts
    ADD CONSTRAINT accounts_account_type_check
    CHECK (account_type IN (
        'CURRENT', 'SAVINGS', 'SUSPENSE', 'INCOME', 'VAULT', 'SETTLEMENT'
    ));
