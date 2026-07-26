---
name: categorize-spend
description: Assign a merchant name and category to imported bank transactions that are still unclassified. Use when the spending page reports transactions waiting to be categorised, or when the user asks to categorise, classify or clean up spending data.
---

# Categorising imported spend

Statement imports store exactly what the bank sent. A descriptor like
`LYFT *SCHD AIR 07-22` or `SQ *MARUWU SEICHA - SF` is not a merchant name and
the bank's own category is often wrong or missing, so naming and categorising is
done here instead — where the decisions can be reviewed and steered.

**Read `RULES.md` in this directory before classifying anything.** It holds the
household's own conventions and overrides everything below.

## How the work is shaped

Decisions are made **per merchant, not per transaction**. One answer about Uber
settles every Uber row at once, and keeps settling them on future imports —
because the decision is stored against the merchant, not the row.

## Configuration

```bash
API="${JAM_API_URL:-http://localhost:8080}"
AUTH="AdminUser:${JAM_AUTH_PASSWORD:?set JAM_AUTH_PASSWORD}"
```

Never hardcode the password. If `JAM_AUTH_PASSWORD` is unset, stop and ask.

## The loop

Repeat until `remainingMerchants` is 0.

### 1. Fetch a batch

```bash
curl -sS -u "$AUTH" "$API/api/v1/spend/pending?limit=20"
```

Returns the valid category codes and up to 20 merchants, busiest first:

```json
{
  "categories": ["GROCERIES","DINING","TRANSPORT", "..."],
  "merchants": [
    { "lookupKey": "UBER",
      "samples": ["UBER", "UBER *TRIP HELP.UBER.COM"],
      "bankCategories": ["Transportation-Taxis & Coach"],
      "txnCount": 28, "totalAmount": 412.55,
      "firstSeen": "2026-05-30", "lastSeen": "2026-07-23" }
  ],
  "remainingMerchants": 149,
  "remainingTransactions": 246
}
```

### 2. Decide

For each merchant, produce a **display name** and a **category code**.

**Merchant name** — what a person would call it. Drop store numbers, city and
state codes, payment-processor prefixes (`SQ *`, `TST*`, `AplPay`) and order ids.

| Descriptor | Merchant |
|---|---|
| `LYFT *SCHD AIR 07-22` | Lyft |
| `SQ *MARUWU SEICHA - SF` | Maruwu Seicha |
| `WHOLEFDS ROG 10820` | Whole Foods |
| `AplPay PUBLIX #1614MIAMI FL` | Publix |
| `AMAZON MKTPL*5T8H34L53` | Amazon |

**Category** — prefer a code from `categories`. `bankCategories` is a hint, not
an answer: banks routinely file a supermarket run as "Wholesale Stores".

Only invent a new code when the spending genuinely does not belong anywhere on
the list *and* is worth seeing on its own line — a visa fee, a car repair. A new
code is created automatically and appears in the list next time, so inventing
casually produces a mess of near-duplicates. When in doubt use `OTHER`.

#### Look it up when you do not recognise it

A statement descriptor is often the only clue, and guessing from it produces a
category that is quietly wrong forever. **Search the web instead of guessing.**

Search when the descriptor is not something you can identify with confidence —
especially when the amount is large or the merchant recurs. Do not search names
you already know; looking up Uber or Whole Foods wastes a step.

What to search:

```
"SP CSB" credit card charge Irvine CA what is this merchant
"EVERWASH" charge on statement
```

Include the distinctive fragment plus a word or two of context — the city or
state from the descriptor, or "credit card charge". Searching the descriptor
verbatim rarely works, because banks mangle spacing and truncate names.

Reading the result:

- Confirm it against what you already have. The amount, the city and the bank's
  own category should all be consistent with what you found. A car wash
  subscription billing $26 twice fits "EVERWASH"; a $2,000 charge would not.
- A processor prefix is worth knowing rather than treating as the merchant.
  `SP *` is Shopify Payments, `SQ *` is Square, `TST*` is Toast — the real
  merchant is the text after it. The importer already strips these, but a
  search result naming one tells you where the merchant name actually starts.
- If the search is inconclusive, say so. Use `OTHER`, tell the user which
  descriptors you could not identify, and let them recognise it from their own
  memory of the purchase — they were there.

**Never put an account number, card reference or transaction id into a search.**
Merchant names and city codes are fine; identifiers that tie back to the
cardholder are not.

When a search settles something durable — that a prefix belongs to a processor,
or that a cryptic name is a particular shop — propose it as a `RULES.md` entry
so the next run does not repeat the lookup.

### 3. Submit

```bash
curl -sS -u "$AUTH" -X PUT "$API/api/v1/spend/pending" \
  -H 'Content-Type: application/json' \
  -d '{"decisions":[
        {"lookupKey":"UBER","merchant":"Uber","category":"TRANSPORT"},
        {"lookupKey":"WHOLEFDS ROG","merchant":"Whole Foods","category":"GROCERIES"}
      ]}'
```

`lookupKey` must be copied exactly from the batch. The response reports
`merchantsUpdated`, `transactionsUpdated` and any `categoriesCreated`.

Submitting is idempotent — re-running after a failure re-applies the same
answers rather than duplicating anything.

### 4. Report

When the queue is empty, tell the user how many merchants and transactions were
classified, and list any categories that were created so they can be reviewed.

## Rules

- **Never overwrite a manual correction.** Anything the user set by hand is
  skipped automatically; do not try to work around that.
- **Do not invent a `lookupKey`.** Only use keys returned by the API.
- **Stop and ask if more than about a quarter of a batch is unclear** — that
  usually means `RULES.md` needs a new entry, which is worth more than guessing
  through one batch.
- **Suggest a `RULES.md` entry** whenever a judgement call comes up that will
  recur.
