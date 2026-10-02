# Architecture

## Components

| Component | Responsibility | State |
|---|---|---|
| **API pods** (`backend/`, Kotlin/Ktor on Netty) | Search, rules, pricing, quotes, bookings, payments, e-tickets, accounts, live map, ingestion | Stateless apart from caches; any pod serves any request |
| **PostgreSQL** | Bookings (PII encrypted), users and refresh tokens, scraped-sailing overlay | Source of truth for money and personal data |
| **Redis** | Seat/cabin/lane holds (atomic Lua), idempotency keys, pod-to-pod bus, leader lock | Short-lived coordination state (`noeviction`, AOF) |
| **Web** (`mobile/`, Flutter web on nginx) | Static app; same-origin calls to `/api` and `/ws` | None |
| **Edge** (nginx or ingress-nginx) | TLS, routing, per-install and per-IP limits, retries on dead pods | Rate-limit counters |
| **Pipeline** (`scraper/`) | Timetables and prices, official rates, AIS fixes → signed ingestion | None (jobs) |

## The catalog: why search needs no database

Every pod holds an immutable `CatalogSnapshot` in memory: ports, operators and their rules,
ships, routes with their sea-lane geometry, fare tables, seasons, regulations, promotions and
all sailings for the next 400 days (~1,100 crossings, generated from the companies' rotation
patterns). A new snapshot is built off to the side and swapped atomically (`AtomicReference`),
so readers never see a half-updated catalog and never take a lock.

Live observations from the pipeline form an **overlay**: a scraped crossing replaces the
reference one of the same route within ±3 hours and carries `priceSource = LIVE`. The overlay
is persisted in Postgres (`sailing_overrides`) so restarts and new pods start with it, and
every accepted ingest publishes the new catalog version on the Redis bus; the other pods reload
the overlay. (Verified: an ingest on one replica moved all three to the new version.)

Consequences:

* Search is CPU-bound and scales linearly with pods: ~1,500 search-heavy req/s per 2-vCPU pod.
* A Postgres or Redis outage does not stop search, only booking.
* A scraper outage does not stop anything: prices fall back to the reference model, flagged
  *indicatif*.

## Search

```
request ─► validation (SearchValidator: ports, dates, party, vehicle, pets…)
        ─► cache (Caffeine, key = request + catalog version + rates version + 5-minute bucket)
        ─► for each sailing of the day: LegEvaluator
              status & cut-off · operator age bands · vehicle limits & date-ranged regulations
              · pets (kennel / pet cabin) · accessibility (PMR cabin) · cabin allocation
              (cheapest whole cabins, ≤ 1 cabin per adult) · capacity minus active holds
        ─► pricing per open fare: base fares × season × demand (1 + 0.4·load³) × fare
              multiplier, taxes, round-trip discount, best eligible promotion (never stacked,
              never on promo fares), DZD/EUR/USD conversion at the official rate
        ─► offers (bookable first), nearby days ±3, notices (documents, minors, check-in…)
```

`GET /api/v1/meta` (ports, operators, ships, routes, rates, limits) is versioned and served with
an `ETag`, so the app revalidates it with a 304 on every start.

## Booking and payment

```
POST /bookings (Idempotency-Key, expectedTotal)
  ├─ idempotency: claim key in Redis → replay the stored result for retries
  ├─ traveller checks (passport only, validity vs. trip end, ages from dates of birth,
  │  names, nationality, e-mail, Algerian/intl phone, vehicle plate & details)
  ├─ fresh quote with the real ages → 409 PRICE_CHANGED (+ new quote) if it differs
  ├─ hold: one Lua script reserves seats, cabins by type, lane metres and kennels
  │  atomically across all needs ({inv} hash tag keeps it on one Redis slot) — TTL 20 min
  └─ insert HELD booking (PII AES-256-GCM encrypted, JSONB document, optimistic version)

POST /bookings/{ref}/payments {method}
  ├─ CIB / Edahabia → SATIM register.do (amount in DZD) → bank page
  ├─ CARD → Stripe Checkout (EUR/USD)              ├─ AGENCY → instructions, hold → 24 h
  └─ browser returns → GET /payments/return → server-side verification with the provider
       (SATIM confirmOrder: OrderStatus 2 + actionCode 0 + amount match; Stripe session or
       signed webhook) → CONFIRMED + HMAC-signed QR e-ticket
```

A sweeper on every pod expires unpaid holds each minute (idempotent; the Redis TTL is the
backstop). Cancellation applies the fare's penalty tiers per leg and marks approved payments
`REFUND_PENDING`.

## Live map

Positions come from AIS when available and are otherwise **dead-reckoned** from the timetable
along each route's sea-lane polyline (labelled "estimated"). One pod — elected through a Redis
lock — keeps the aisstream.io WebSocket open and publishes fixes on the bus; every pod streams
positions to its clients over `/ws/live` from a `SharedFlow` that drops the oldest frame for
slow clients, so one bad connection cannot slow the others. The app draws them on
OpenStreetMap/OpenSeaMap tiles with a bundled Natural Earth coastline underneath, so the map
stays readable when tiles fail.

## Failure modes

| Failure | Effect | Mitigation |
|---|---|---|
| One API pod dies | In-flight requests to it fail | Edge retries GETs on another pod (`proxy_next_upstream`); readiness probes; PDB 75 % |
| All pods busy | Latency grows | HPA (CPU 60 %, +100 %/30 s); per-install limits shed abusive clients first |
| Postgres primary fails | Booking answers `503 Retry-After` until failover (~30 s) | CloudNativePG promotes the sync replica; search, catalogue and map unaffected |
| Redis down or hung | No new holds / idempotency | 1 s timeouts, commands rejected while disconnected: booking answers `503 Retry-After`, search uses last known availability. Verified: 20 s Redis freeze → 8,761 searches all OK, bookings 503 then OK |
| Any shared dependency down | — | Readiness only reflects the pod itself, so pods stay in the load balancer; `/health/deps` and `wave_dependency_up` report the incident |
| Scraper broken | Prices stop updating | Reference timetable keeps search working; `WaveNoLiveData` alert after 3 h |
| SATIM / Stripe down | Card payment fails | Agency payment stays available; `WavePaymentsFailing` alert |
| Tile server down | No base map | Bundled coastline layer |
| Mobile network flaps | Requests fail | App retries idempotent GETs with backoff; WebSocket reconnects with jitter; booking retry is safe (idempotency key) |
