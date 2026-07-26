# Household categorisation rules

Edit this file freely — it is the steering wheel for the classifier. Anything
here overrides the general guidance in `SKILL.md`.

Add a line whenever a decision comes out wrong, and it stays fixed.

## Category conventions

- **Uber and Lyft are `TRANSPORT`**, not `TRAVEL`. `TRAVEL` is for flights,
  hotels and trips away — not getting across town.
- **Uber Eats, DoorDash and delivery apps are `DINING`**, not `TRANSPORT`,
  regardless of what the bank called them.
- **Supermarkets are `GROCERIES`** even when the bank files them as
  "Wholesale Stores" or "Discount Store" — Walmart, Costco, Target grocery runs
  included.
- **Coffee shops, cafés and bubble tea are `DINING`.**
- **Amazon defaults to `SHOPPING`.** It is not worth splitting by what was
  actually bought.
- **Airport and in-flight purchases are `TRAVEL`**, not `DINING`, when they are
  clearly part of a trip.
- **Petrol stations are `FUEL`.** A convenience purchase at one is still `FUEL`;
  it is not worth separating.

## Merchant naming

- Use the name a person would say out loud: "Whole Foods", not
  "WHOLEFDS ROG 10820".
- Drop city and state suffixes: "Publix", not "Publix Miami FL".
- Keep a distinguishing brand where it matters: "Amazon" and "Amazon Fresh" are
  worth telling apart; "Uber" and "Uber Eats" definitely are.

## Payment processors, not merchants

These prefixes say how something was paid for, not who was paid. The real
merchant is whatever follows. The importer strips them, but recognise them when
reading a raw descriptor:

- `SP *` — Shopify Payments (any small shop selling through Shopify)
- `SQ *` — Square
- `TST*` — Toast (restaurant point of sale)
- `AplPay` — Apple Pay
- `PY *`, `PAYPAL *` — PayPal

## Specific merchants

- `EVERWASH` → merchant "Everwash", category TRANSPORT — a car wash
  subscription, despite the bank filing it under Computer Supplies.
- `STM ...` → merchant "STM Montreal Transit", category TRANSPORT — the Montreal
  metro. The bank calls it Travel; by our convention getting across town is
  TRANSPORT. Station names vary, so each one needs its own entry.
- `AMK WALMART FC MICRO MKT` → merchant "Walmart Micro Market", category DINING
  — an office snack kiosk, not a shop.
- `PLATINUM RESY CREDIT` / `PLATINUM DIGITAL ENTERTAINMENT CREDIT` → Amex
  statement credits. Categorise them as the spending they offset (DINING,
  ENTERTAINMENT) so they net against it rather than sitting in FEES.

<!-- Add entries as they come up. -->

## When to create a new category

Only for spending that recurs or is large enough to want on its own line, and
that genuinely fits nowhere:

- An immigration or visa fee → `IMMIGRATION`
- A significant car repair → `CAR_REPAIR`

Anything small and one-off belongs in `OTHER`.
