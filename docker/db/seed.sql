-- Sample data for local testing. Applied by seed-if-empty.sh only when the
-- database has no records yet, so re-running `docker compose up` is safe.
--
-- Liquibase owns the schema (tables already exist by the time this runs); this
-- file only inserts rows. Dates use CURDATE() so the records always fall inside
-- the 7-day window that GET /api/v1/records returns.

-- Opening balance the ledger builds on.
INSERT INTO balance (amount, delta, `date`, `comment`)
VALUES (5000.00, 0.00, CURDATE(), 'opening balance');

-- Accounts the scheduled job would generate monthly records from.
INSERT INTO account (id, `acct-name`, `day-of-month`, `default-amount`) VALUES
    (1, 'rent',     1, 1500.00),
    (2, 'hydro',    5,   90.00),
    (3, 'internet', 5,   75.00);

-- Three unpaid records to exercise the full lifecycle:
--   PUT /records/{id}/paid    -> mark paid (creates a linked balance row)
--   PUT /records/{id}/amount  -> change amount (corrects the ledger if paid)
--   PUT /records/{id}/unpaid  -> revert a payment
INSERT INTO records (`acct-name`, `date`, amount, `is-paid`) VALUES
    ('rent',     CURDATE(), 1500.00, 0),
    ('hydro',    CURDATE(),   90.00, 0),
    ('internet', CURDATE(),   75.00, 0);
