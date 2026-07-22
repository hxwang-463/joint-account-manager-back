# Local testing sandbox

Spins up MySQL and the application together on your machine, with sample data,
so you can hit the API without touching your real database.

The app image is built from the repository's own `Dockerfile`, so this is the
same image that ships to production — you need Docker, but **not** a local JDK.

## Start

```bash
cd docker
docker compose up --build
```

This will:

1. start MySQL (exposed on host port **3307**),
2. build and start the app on **http://localhost:8080**,
3. let Liquibase create the schema on first boot,
4. seed sample data once the app is healthy.

First run takes a couple of minutes (it builds the app and initialises MySQL).
Add `-d` to run in the background.

## Try it

```bash
# current balance and this week's records
curl localhost:8080/api/v1/balance
curl localhost:8080/api/v1/records

# pay record 1, then correct its amount, then revert the payment
curl -X PUT localhost:8080/api/v1/records/1/paid
curl -X PUT localhost:8080/api/v1/records/1/amount -H 'Content-Type: text/plain' -d '1600.00'
curl -X PUT localhost:8080/api/v1/records/1/unpaid

# watch the ledger change
curl "localhost:8080/api/v1/balance/history?limit=10"
```

Swagger UI: http://localhost:8080/swagger-ui/index.html

## Seeded data

| Table | Rows |
|-------|------|
| balance | opening balance of 5000 |
| account | rent, hydro, internet |
| records | 3 unpaid records (ids 1–3), dated today |

Records are dated with `CURDATE()`, so they always appear in
`GET /api/v1/records` (which returns the last 7 days).

## Stop / reset

```bash
docker compose down       # stop, keep the database
docker compose down -v    # stop and wipe the database (fresh schema + reseed next up)
```

## Connect a DB client

Host `localhost`, port `3307`, user `root`, password `localpw`, database
`jointacct`.

## Notes

- The `seed` container runs once and exits 0; seeing it as `Exited (0)` in
  `docker compose ps` is normal.
- Seeding only happens when the `records` table is empty, so restarting the
  stack never duplicates rows. To start clean, use `down -v`.
- Everything here uses throwaway credentials (`localpw`) and is meant for local
  use only.
