# Booking rules

What the engine enforces, where, and why. Rules that change with seasons or company decisions
live in data (`backend/src/main/resources/catalog/*.json`), not in code; sources for each rule
are in [`research/ferry-booking-algeria.md`](research/ferry-booking-algeria.md).

| Stage | Code | Effect of a violation |
|---|---|---|
| Search form | `SearchValidator` | `422` with the list of problems (the app shows them on the fields) |
| Each crossing | `LegEvaluator` | Crossing listed but not bookable, with the reason (`reasons`) |
| Booking | `BookingService` | `422` per traveller field, or `409` (price changed, sold out) |
| Payment | `PaymentService`, providers | Booking stays `HELD` until a verified approval |

## The party

* Adults are 18–59, seniors 60+, and every child is entered with their **exact age**: each
  company draws its bands differently, so the same 3-year-old is an infant for GNV and a
  child for Algérie Ferries.
* 1 to 9 passengers per booking; at least one traveller aged 18+ (checked again from the
  dates of birth at booking time); no more infants than adults (each infant shares an adult's
  seat or berth).
* At booking, ages are recomputed from the dates of birth **on the departure date** and the
  price recomputed; if it differs from what the traveller saw, the API answers
  `409 PRICE_CHANGED` with the new quote and the app asks for confirmation.

| Company | Infant (free/reduced) | Child | Senior | Sales close | Baggage seat / cabin |
|---|---|---|---|---|---|
| Algérie Ferries | < 3 | 3–12 | — | 4 h before | 30 / 60 kg |
| Corsica Linea | < 3 | 3–12 | 60+ | 3 h | 30 / 60 kg |
| GNV | < 4 | 4–11 | — | 3 h | 30 / 60 kg |
| Baleària | < 1 | 1–13 | — | 2 h | 25 / 40 kg |
| Nouris Elbahr Ferries | < 3 | 3–11 | — | 4 h | 30 / 50 kg |
| Armas Trasmed | < 2 | 2–11 | — | 2 h | 25 / 40 kg |

## Travel documents

* **Passport only**, for every traveller including babies: national ID cards are not
  accepted between Algeria and Europe (`DOC_ID_CARD_NOT_ACCEPTED`).
* The passport must be valid for the whole trip (end of trip + 2 days), otherwise
  `DOC_EXPIRED`; less than 6 months of validity after the trip is a warning
  (`DOC_EXPIRES_SOON`) because several destinations expect it.
* Names: letters, spaces, apostrophes and hyphens (any script), as printed in the passport.
  Document number: 5–20 letters/digits. Nationality and issuing country: ISO codes.
* Every search shows the notices that apply: Schengen visa or residence permit for Algerian
  nationals going to Europe, check-in times, and, when the party includes minors, the exit
  authorisation (*AST*, France) or the certified paternal authorisation (Algeria).

## Vehicles

| Declared | Classified as | Rule |
|---|---|---|
| Car ≤ 5 m and ≤ 1.90 m (roof box adds 0.40 m) | `CAR` | standard fare |
| Higher or longer car, SUV, minivan | `CAR_HIGH` | high-vehicle fare |
| Motorcycle / scooter | `MOTORCYCLE` | |
| Motorhome | `CAMPER` | each ship's maximum length/height applies |
| Car + trailer or caravan | `CAR_TRAILER` | trailer length added to lane metres |
| Van / utility vehicle | `VAN` | refused 15 June – 15 September (below) |

* Absolute limits 4.5 m high and 18 m long; each ship has its own maximum
  (`VEHICLE_TOO_HIGH_VESSEL`, `VEHICLE_TOO_LONG_VESSEL`); lane metres are counted per crossing
  (`VEHICLE_SPACE_FULL`).
* **Summer regulations** (`regulations.json`, dated, renewed each year by the authorities):
  vans may not board towards Algerian ports from 15 June to 15 September; at Alger and Oran,
  new or under-3-year-old vehicles imported by residents are refused in the same period. The
  app warns in the vehicle picker; the engine blocks the crossing with the official wording.
* At booking: plate, make, model and registration country are required; the search shows the
  documents to carry (registration certificate, green card valid in Algeria, *TPD* temporary
  admission for vehicles registered abroad).

## Pets

* Up to 2 animals per booking for every company (`PETS_MAX` on the crossing; the search form
  accepts up to 4 so other companies' rules can be compared), in a
  **kennel** or a **pet-friendly cabin** depending on the ship (`PET_KENNEL_UNAVAILABLE`,
  `PET_CABIN_UNAVAILABLE`, `PETS_NOT_ACCEPTED`); never in seat lounges or ordinary cabins.
* Notice: microchip, rabies vaccination 21 days to 12 months old, vet certificate, and for the
  return to the EU a rabies antibody test done at least 3 months before.

## Accommodation and cabins

* Seat, or cabin preference (any, inside, outside, suite). Cabins are allocated as whole
  cabins, cheapest combination first, with at most one cabin per adult
  (`CABINS_NEED_ADULTS`); travelling with pets forces a pet cabin, with reduced mobility a
  PMR cabin (`PMR_CABIN_UNAVAILABLE`).
* Remaining seats, cabins by type, lane metres and kennels are capacity minus confirmed
  bookings minus **active holds**, so two people cannot book the last cabin.

## Prices

```
fare = route base fare (adult seat, cabin types, vehicle classes, pets)
     × season (summer peaks by direction, Eid al-Fitr and Eid al-Adha, year-end holidays,
               spring and autumn shoulders)
     × demand (1 + 0.4 × load factor³)
     × fare multiplier (promo / standard / flex)
     − age-band discounts (per company)
     + port and security taxes (per passenger, per vehicle)
     − best eligible promotion (never stacked, never on promo fares)
     + WAVE service fee (DZD, configurable)
```

* Fares are stored in each company's currency and converted at the **official Banque
  d'Algérie rate** of the quote (EUR 151.06 / USD 132.73 DZD on 1–2 October 2026); every quote
  also carries the amount in dinars charged by CIB/Edahabia. The parallel-market rate is never
  used.
* Promo fares close a number of days before departure and once the crossing is more than
  75 % full.
* Reference prices (no live data yet for that crossing) are labelled *Prix indicatif*; live
  ones *Prix en direct*.

## Fares and cancellation (per leg)

* **Algérie Ferries** — Promo (−15 %, from 21 days out): non-refundable · Standard (F0): 20 %
  fee ≥ 30 days before, 30 % ≥ 10 days, 50 % ≥ 48 h, 100 % after.
* **Corsica Linea** — Prix mini (−10 %, from 30 days): non-refundable · Flex: free ≥ 30 days,
  30 % ≥ 7 days, 50 % ≥ 48 h, 100 % after · Super Flex (+12 %): free ≥ 7 days, 25 % ≥ 24 h,
  100 % after.
* **GNV** — Promo (−15 %, from 14 days): non-refundable · Standard: 10 % ≥ 30 days, 30 % ≥
  7 days, 50 % ≥ 24 h, 100 % after · Flex (+15 %): free ≥ 24 h, 100 % after.
* **Baleària** — Basic (−12 %, from 21 days): non-refundable · Reduced fare: **free within 24 h
  of purchase** (if departure is more than 2 h away), then 10 % ≥ 48 h, 20 % ≥ 24 h, 100 %
  after · Flex (+12 %): free ≥ 24 h, 100 % after.
* **Nouris Elbahr** — Promo (−15 %, from 21 days): non-refundable · Standard: 20 % ≥ 30 days,
  30 % ≥ 10 days, 50 % ≥ 48 h, 100 % after.
* **Armas Trasmed** — Promo (−15 %, from 14 days): non-refundable · Standard: 15 % ≥ 48 h,
  100 % after · Flex (+10 %): free ≥ 24 h, 100 % after.

The cancellation preview (`POST /bookings/{ref}/cancel` with `dryRun: true`) shows the exact
fee and refund before the traveller confirms; the WAVE service fee is not refunded.

## Holds and payment

* Creating a booking holds its seats/cabins/lane metres/kennels for **20 minutes** (Redis Lua
  script, all-or-nothing). Unpaid holds expire and the booking becomes `EXPIRED`.
* **CIB / Edahabia** through SATIM: charged in dinars (minimum 50 DZD); approval only when
  SATIM's status check returns `OrderStatus = 2`, `actionCode = 0` and the expected amount —
  never from the browser's return URL alone.
* **International card** (Stripe Checkout, EUR or USD), confirmed by signed webhook or session
  check.
* **Agency**: the hold is extended to 24 hours; staff confirms the cash payment.
* Every booking request carries an `Idempotency-Key`: a retry after a network failure returns
  the same booking instead of creating a second one.

## Violation codes

| Group | Codes |
|---|---|
| Search | `PORT_UNKNOWN`, `ROUTE_SAME_PORT`, `NO_ROUTE`, `DATE_IN_PAST`, `DATE_TOO_FAR`, `RETURN_REQUIRED`, `RETURN_BEFORE_DEPARTURE`, `PAX_ADULT_REQUIRED`, `PAX_MAX`, `PAX_INVALID`, `CHILD_AGE_INVALID`, `INFANTS_EXCEED_ADULTS`, `VEHICLE_DIMENSIONS_INVALID`, `VEHICLE_TOO_HIGH`, `VEHICLE_TOO_LONG`, `PETS_MAX`, `PET_COUNT_INVALID`, `FLEX_DAYS_INVALID` |
| Crossing | `SCHEDULE_NOT_PUBLISHED`, `NO_SAILING_ON_DATE`, `SAILING_CANCELLED`, `SAILING_CLOSED`, `PETS_NOT_ACCEPTED`, `PET_CABIN_UNAVAILABLE`, `PET_KENNEL_UNAVAILABLE`, `VEHICLE_TOO_HIGH_VESSEL`, `VEHICLE_TOO_LONG_VESSEL`, `VEHICLE_NOT_ACCEPTED`, `VEHICLE_SPACE_FULL`, `PMR_CABIN_UNAVAILABLE`, `CABINS_NEED_ADULTS`, `CABINS_FULL`, `SEATS_FULL`, `ACCOMMODATION_NOT_OFFERED`, `TARIFF_UNAVAILABLE` |
| Booking | `TRAVELLER_COUNT_MISMATCH`, `TRAVELLER_AGE_MISMATCH`, `DOB_INVALID`, `NAME_INVALID`, `NATIONALITY_INVALID`, `DOC_NUMBER_INVALID`, `DOC_ID_CARD_NOT_ACCEPTED`, `DOC_EXPIRED`, `DOC_EXPIRES_SOON` (warning), `EMAIL_INVALID`, `PHONE_INVALID`, `TERMS_NOT_ACCEPTED`, `VEHICLE_DETAILS_REQUIRED`, `PLATE_INVALID`, `PRICE_CHANGED`, `SOLD_OUT_DURING_BOOKING`, `SAILING_NOT_FOUND`, `LEG_ORDER_INVALID`, `LEG_ROUTE_MISMATCH` |
| After booking | `BOOKING_NOT_FOUND`, `BOOKING_NOT_PAYABLE`, `BOOKING_NOT_CANCELLABLE`, `PAYMENT_METHOD_UNAVAILABLE` |
| Notices (info) | `DOC_PASSPORT`, `CHECKIN`, `MINOR_AUTHORIZATION`, `VEHICLE_DOCS`, `PET_DOCS`, `PMR_NOTICE`, `PETS_IN_KENNEL`, `PRICE_REFERENCE`, `VESSEL_TBA` |

All messages exist in French, English and Arabic (`rules/Messages.kt`).
