/*
 * Let more than one person act for a client.
 *
 * clients.user_id is unique, so each client had exactly one login. For an
 * individual that is correct. For a company it was quietly wrong in a way that
 * mattered: a company had one human who could see its money or instruct a
 * payment, and four-eyes approval on a corporate account was impossible
 * because the second pair of eyes had nowhere to come from.
 *
 * clients.user_id stays as it is -- it records who opened the client and is
 * still what the registration flow writes. This table is the general answer to
 * "which client does this login act for", and the registrant is backfilled
 * into it as the primary signatory so there is one rule rather than two.
 */
CREATE TABLE IF NOT EXISTS client_signatories (
    id                BIGSERIAL    PRIMARY KEY,
    client_id         BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    username          VARCHAR(50),

    /*
     * SIGNATORY may instruct payments; VIEWER may only look. The line that
     * matters at a bank is whether you can move money, and a bookkeeper who
     * reconciles a company's statements should be on the wrong side of it.
     */
    authority         VARCHAR(20)  NOT NULL,

    is_primary        BOOLEAN      NOT NULL DEFAULT FALSE,
    added_by_username VARCHAR(50),
    added_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_client_signatory_client
        FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE,

    /*
     * A login acts for one client, not several. Somebody who genuinely acts
     * for two companies needs two logins -- which is the right answer, because
     * it keeps their actions attributable to the company they were acting for
     * at the time.
     */
    CONSTRAINT uk_client_signatory_user UNIQUE (user_id),

    CONSTRAINT chk_client_signatory_authority
        CHECK (authority IN ('SIGNATORY', 'VIEWER'))
);

CREATE INDEX IF NOT EXISTS idx_client_signatories_client
    ON client_signatories (client_id);

/*
 * Everyone who already had a client record becomes its primary signatory, so
 * existing logins keep working and the new lookup answers for them too.
 */
INSERT INTO client_signatories (client_id, user_id, authority, is_primary, added_at)
SELECT c.id, c.user_id, 'SIGNATORY', TRUE, COALESCE(c.created_at, NOW())
  FROM clients c
 WHERE NOT EXISTS (
       SELECT 1 FROM client_signatories s WHERE s.user_id = c.user_id
 );
