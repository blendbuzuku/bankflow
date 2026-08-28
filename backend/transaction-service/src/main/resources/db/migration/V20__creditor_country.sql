/*
 * Say which country the money is going to.
 *
 * The debtor now travels with a full postal address, and the creditor with a
 * name and nothing else. For a domestic payment that is survivable -- the IBAN
 * is Kosovo by definition of the rail. For anything leaving the country it is
 * not: the destination country is what sanctions screening runs against, and
 * it was nowhere in the message.
 *
 * One column rather than a full address, because the two sides are not
 * symmetric. The payer's details are what the funds transfer rules require to
 * accompany a transfer; the payee is identified by their account. Country is
 * the part that carries real weight on its own.
 */
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS creditor_country VARCHAR(2);
