/*
 * Admit camt.056 to the message log, and retire pain.001 from it.
 *
 * The constraint was written when the bank spoke three definitions. It now
 * sends cancellation requests too, and dropping pain.001 keeps the column
 * honest: KIPS pain messages are direct debit mandates, so a customer credit
 * transfer initiation was never something this bank would store here.
 */
ALTER TABLE pacs_messages
    DROP CONSTRAINT IF EXISTS pacs_messages_message_type_check;

/*
 * Nothing should have been stored under the retired value, but a constraint
 * added over rows that violate it fails at migration time rather than at the
 * point of the mistake — so make it true before asserting it.
 */
UPDATE pacs_messages
SET message_type = 'PACS_008'
WHERE message_type = 'PAIN_001';

ALTER TABLE pacs_messages
    ADD CONSTRAINT pacs_messages_message_type_check
    CHECK (message_type IN ('PACS_008', 'PACS_002', 'PACS_004', 'CAMT_056'));
