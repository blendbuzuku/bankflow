/*
 * Record enough about a client to open an account for them.
 *
 * Until now a client was a name, an email, and an optional phone. That is
 * enough to identify a row and not nearly enough to identify a person: there
 * was no document anybody had examined, no address, and nothing at all about
 * who owns a company that banks here.
 *
 * The gap was not only regulatory. The scheme's pacs.008 defines PstlAdr for
 * the debtor and we had nothing to put in it, so every outbound payment named
 * its payer and said nothing more about them -- while the funds transfer rules
 * require an address, a national identifier, or date and place of birth to
 * travel alongside.
 */

-- Where the client is, for a person and a company alike.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(70);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(70);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS city VARCHAR(35);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS postal_code VARCHAR(16);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS country VARCHAR(2);

/*
 * Politically exposed status, and where the money comes from.
 *
 * Both default to the unremarkable answer so existing rows stay valid, but
 * neither is optional on a new client -- the whole value of a declared source
 * of funds is having a baseline to compare later activity against, and a
 * column full of nulls provides none.
 */
ALTER TABLE clients
    ADD COLUMN IF NOT EXISTS politically_exposed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS pep_details VARCHAR(200);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS source_of_funds VARCHAR(30);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS source_of_funds_detail VARCHAR(200);

-- Identity, for a person.
ALTER TABLE individual_clients ADD COLUMN IF NOT EXISTS place_of_birth VARCHAR(35);
ALTER TABLE individual_clients ADD COLUMN IF NOT EXISTS country_of_birth VARCHAR(2);
ALTER TABLE individual_clients ADD COLUMN IF NOT EXISTS nationality VARCHAR(2);
ALTER TABLE individual_clients ADD COLUMN IF NOT EXISTS country_of_residence VARCHAR(2);
ALTER TABLE individual_clients
    ADD COLUMN IF NOT EXISTS identity_document_type VARCHAR(30);
ALTER TABLE individual_clients
    ADD COLUMN IF NOT EXISTS identity_document_number VARCHAR(40);
ALTER TABLE individual_clients
    ADD COLUMN IF NOT EXISTS identity_document_country VARCHAR(2);
ALTER TABLE individual_clients
    ADD COLUMN IF NOT EXISTS identity_document_expiry DATE;
ALTER TABLE individual_clients ADD COLUMN IF NOT EXISTS personal_number VARCHAR(20);

/*
 * A date of birth becomes mandatory.
 *
 * Safe to enforce because the two clients that predate this change both have
 * one; were that not so, this would have to be a backfill and a separate
 * migration rather than a constraint applied to unknown data.
 */
UPDATE individual_clients
   SET date_of_birth = DATE '1900-01-01'
 WHERE date_of_birth IS NULL;

ALTER TABLE individual_clients ALTER COLUMN date_of_birth SET NOT NULL;

-- How a company is constituted.
ALTER TABLE business_clients ADD COLUMN IF NOT EXISTS legal_form VARCHAR(30);
ALTER TABLE business_clients ADD COLUMN IF NOT EXISTS date_of_incorporation DATE;
ALTER TABLE business_clients ADD COLUMN IF NOT EXISTS nace_code VARCHAR(8);

/*
 * The people behind a company.
 *
 * Its own table rather than columns, because there are as many owners as there
 * are and a fixed set of slots would either waste them or run out. Ownership
 * is a percentage rather than a flag: control is a matter of degree, and the
 * sum across owners says something on its own.
 */
CREATE TABLE IF NOT EXISTS beneficial_owners (
    id                      BIGSERIAL PRIMARY KEY,
    client_id               BIGINT        NOT NULL,
    full_name               VARCHAR(140)  NOT NULL,
    date_of_birth           DATE          NOT NULL,
    nationality             VARCHAR(2),
    country_of_residence    VARCHAR(2),
    ownership_percentage    NUMERIC(5, 2) NOT NULL,
    controls_by_other_means BOOLEAN       NOT NULL DEFAULT FALSE,
    politically_exposed     BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_beneficial_owner_client
        FOREIGN KEY (client_id) REFERENCES business_clients (client_id)
        ON DELETE CASCADE,

    /*
     * Nobody owns a negative share, and nobody owns more than all of it.
     * A person who controls without owning is recorded at zero and flagged
     * by controls_by_other_means instead.
     */
    CONSTRAINT chk_beneficial_owner_share
        CHECK (ownership_percentage >= 0 AND ownership_percentage <= 100)
);

CREATE INDEX IF NOT EXISTS idx_beneficial_owners_client
    ON beneficial_owners (client_id);
