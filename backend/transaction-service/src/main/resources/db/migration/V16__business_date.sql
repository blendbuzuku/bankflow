/*
 * The day the bank is trading into.
 *
 * Distinct from the calendar date, and the difference is the point. Closing a
 * day rolls this forward, so work done after the close books into the next day
 * and reconciles on that day's proofs. Without it, closing would either seal
 * the books and halt the bank until midnight, or let entries land in a day
 * somebody has already signed off.
 *
 * One row. Trading happens on one day at a time, which is why the primary key
 * is fixed rather than generated.
 */
CREATE TABLE IF NOT EXISTS business_date (

    id                 bigint       PRIMARY KEY,

    /* Not named current_date: that is a reserved word in Postgres. */
    trading_date       date         NOT NULL,

    rolled_at          timestamp,
    rolled_by_username varchar(100),

    CONSTRAINT ck_business_date_singleton CHECK (id = 1)
);

/*
 * Seeded from the newest day the books already know about, so an existing
 * database does not jump backwards. A day already closed is not one to trade
 * into, so the seed starts after it.
 */
INSERT INTO business_date (id, trading_date)
SELECT 1, GREATEST(
    CURRENT_DATE,
    COALESCE((SELECT MAX(booking_date) FROM ledger_entries), CURRENT_DATE),
    COALESCE((SELECT MAX(booking_date) + 1 FROM day_closes), CURRENT_DATE)
)
ON CONFLICT (id) DO NOTHING;
