/*
 * Carry the payer's address with the payment.
 *
 * The scheme's pacs.008 defines PstlAdr on the debtor and until now there was
 * nothing to put in it, so every outbound payment named its payer and said
 * nothing further about them. The funds transfer rules expect an address, a
 * national identifier, or date and place of birth to accompany a transfer;
 * the receiving bank had none of the three.
 *
 * Copied onto the transaction rather than read from the client when a message
 * is built, because a message has to stay reproducible. Regenerating the
 * pacs.008 for a payment sent last year must produce what was actually sent,
 * and the customer may have moved in the meantime.
 *
 * Nullable, and deliberately so: payments already booked have no address to
 * backfill, and inventing one would put a false statement on a real payment.
 */
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS debtor_address_line1 VARCHAR(70);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS debtor_address_line2 VARCHAR(70);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS debtor_city VARCHAR(35);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS debtor_postal_code VARCHAR(16);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS debtor_country VARCHAR(2);
