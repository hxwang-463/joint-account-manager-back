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
- **Delivery apps take the category of what was delivered.** DoorDash from a
  restaurant is `DINING`, but from a supermarket it is `GROCERIES` and from a
  pharmacy it is `HEALTH`. Same for Instacart and Grubhub. The "delivery apps
  are `DINING`" rule above is about not filing them as `TRANSPORT`.
- **Online storefronts split from their shops.** `TARGET.COM` and
  `WALMART.COM` are `SHOPPING`; the in-store runs stay `GROCERIES`. Name them
  distinctly ("Target" vs "Target.com") so the split does not read as a bug.
- **Parking is `TRANSPORT`**, including airport parking — it is getting
  somewhere, not the trip itself.
- **Mobile carriers and travel eSIMs are `INTERNET`** (US Mobile, Airalo).
  There is no separate phone category and it is not worth one.
- **Haircuts, barbers and spas are `PERSONAL_CARE`.** Gyms stay in `HEALTH`.
  Cosmetics bought as products (Sephora, Ulta) are `SHOPPING`.
- **Bottle shops and off-licences are `GROCERIES`** — wine or spirits to take
  home is a shop, not a night out. Drinks bought at a bar stay `DINING`.
- **Intercity and commuter rail is `TRANSPORT`** (Amtrak, LIRR, SEPTA), same
  as the metro. Only flights, hotels and the trip itself are `TRAVEL`.

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
- `PP*` — PayPal (short form)
- `LS *` — Lightspeed (Montreal POS; common on Canadian charges)
- `DD *` — DoorDash; the text after it is the shop delivered from
- `UEP*`, `INKD*`, `FOOD AT*` — restaurant ordering platforms
- `FGT*` — Front Gate Tickets (events)
- `Vagaro_*` — salon and spa booking
- `QDI*` — Quest Diagnostics
- `CTLP*` — CSC ServiceWorks (laundry and forecourt machines)
- `EMPOWER*` — Empower rideshare. **The trailing text is the driver's name,
  not a merchant** — never use it as the display name. Always just "Empower".

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

- `SP CSB` → merchant "Crop Shop Boutique", category SHOPPING — an activewear
  brand selling through Shopify, not a bank or a card service.
- `ETHERNETSE*` → merchant "Ethernet Servers", category SUBSCRIPTIONS — VPS
  hosting, despite the bank filing it under Computer Supplies.
- `DPT OF TRANSPORTATION AND` / `DPT OF TRANSP` / `MDC TRANSIT AUTOMATED FAR`
  → merchant "Miami-Dade Transit", category TRANSPORT. The first two are $2.25
  contactless tap fares, the third is EASY Card auto-replenishment. The bank
  files them variously as Travel and Bills & Utilities; both are wrong.
- Universal Orlando in-park mobile ordering — `TOADSTOOL CAFE`, `SPIT FYRE`,
  `BAR MOONSHINE`, `CAF SIRENE`, each with a trailing 4-digit code → DINING,
  despite the bank filing them as Entertainment.
- `AMEX CENTURION` lounges and `HUDSON-*` airport shops → TRAVEL, per the
  airport rule, not DINING.
- `BENTONVILLE FW LLC` → merchant "First Watch", category DINING — the
  breakfast chain; "FW" is the franchisee's LLC, not a merchant name.
- `HOMEGROWN` → merchant "HomeGrown", category DINING — breakfast and lunch
  spot in Bentonville, not a grocer despite the name.
- `KING JAMES WINE SC` → merchant "King James Wine", category GROCERIES — a
  retail bottle shop. "SC" is "School", not a store code.
- `CITY OF BENTONVILLE, ARK` → merchant "City of Bentonville", category
  UTILITIES — municipal water, sewer and refuse.
- `COX KANSAS COMM` → merchant "Cox Communications", category INTERNET. The
  long trailing string is an account reference — never search it.

<!-- Add entries as they come up. -->

## When to create a new category

Only for spending that recurs or is large enough to want on its own line, and
that genuinely fits nowhere:

- An immigration or visa fee → `IMMIGRATION`
- A significant car repair → `CAR_REPAIR`

Anything small and one-off belongs in `OTHER`.
