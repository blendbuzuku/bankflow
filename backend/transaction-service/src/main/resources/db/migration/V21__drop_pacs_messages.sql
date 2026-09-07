/*
 * Retire the scheme message table.
 *
 * The archive moved to MongoDB. Everything about a scheme message is
 * document-shaped -- the payload is literally a document, it is written once
 * and never updated, a pacs.008 and a camt.056 share almost no fields, and it
 * is searched by attribute and body text rather than joined to anything.
 *
 * Nothing has read or written this table since. Leaving it would be worse than
 * useless: a table that looks like data and is not. Somebody querying Postgres
 * in a month would find messages that stop dead on the day of the migration
 * and reasonably conclude the bank had stopped talking to the scheme.
 *
 * V3 created it and V10 constrained it. Those migrations stay exactly as they
 * are -- Flyway checksums applied migrations, and editing one makes it refuse
 * to start against any database that already ran it. Retiring a table is
 * another migration, not an edit to an old one, so the history reads honestly:
 * built here, changed there, retired when its contents moved elsewhere.
 *
 * Irreversible. Anything still in this table is gone.
 */
DROP TABLE IF EXISTS pacs_messages;
