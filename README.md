# WAVE — ferry booking between Algeria and Europe

WAVE compares and books ferry crossings between Algeria and France, Spain and Italy:
Algérie Ferries, Corsica Linea, Baleària, GNV, Nouris Elbahr and Armas Trasmed on 44 routes
and 14 ports. Prices show in dinars, euros or dollars at the official Banque d'Algérie rate;
payment by CIB / Edahabia (SATIM), international card, or cash at the agency; e-tickets are
signed QR codes; a live map follows every ship.

| Part | Stack | Folder |
|---|---|---|
| App (Android, iOS, web) | Flutter 3.47, Riverpod, go_router; Android host in Kotlin | [`mobile/`](mobile/README.md) |
| API | Kotlin 2.4, Ktor 3 (Netty), PostgreSQL, Redis | [`backend/`](backend/) |
| Live data pipeline | Python, Scrapy + Playwright, Firecrawl, pyais | [`scraper/`](scraper/README.md) |
| Deployment | nginx load balancer, docker-compose, Kubernetes, CI | [`infra/`](infra/), [`.github/`](.github/workflows/ci.yml) |

```
            travellers (app / web)                      operator sites, Banque d'Algérie, AIS
                    │ HTTPS + WSS                                     │
        ┌───────────▼────────────┐                     ┌──────────────▼──────────────┐
        │ load balancer / ingress│                     │ scraper jobs (Scrapy +      │
        │ TLS, per-install and   │                     │ Playwright, Firecrawl), AIS │
        │ per-IP limits, routing │                     │ forwarder                   │
        └─────┬────────────┬─────┘                     └──────────────┬──────────────┘
              │ /api, /ws  │ /                          HMAC-signed   │ /internal/v1/ingest
   ┌──────────▼──────────┐ │                                          │
   │ API pods (Ktor) ×N  │◄┼──────────────────────────────────────────┘
   │ in-memory catalog,  │ └──► web (nginx, Flutter build)
   │ rules, pricing      │
   └──┬──────────────┬───┘
      │              │
 ┌────▼─────┐  ┌─────▼──────────────────────────────┐
 │ Postgres │  │ Redis: seat holds (Lua), idempotency│
 │ bookings │  │ keys, pod-to-pod bus, AIS leader    │
 └──────────┘  └─────────────────────────────────────┘
```

## What a booking goes through

1. **Search** (`POST /api/v1/search`): every crossing of the day ± nearby days, evaluated against
   the company's rules (age bands, cabins, pets, vehicle size, summer regulations, cut-off time)
   and priced per fare (promo / standard / flex) with season, demand, round-trip and promotion
   adjustments. Times are shown on each port's own clock.
2. **Quote and booking** (`POST /api/v1/bookings`, `Idempotency-Key`): passports checked
   (valid for the whole trip, no ID cards), ages re-derived from dates of birth, the price
   re-computed (`PRICE_CHANGED` if it moved) and seats/cabins/lane metres **held for 20
   minutes** atomically in Redis.
3. **Payment**: CIB/Edahabia through SATIM (amount in DZD, verified server-side), Stripe for
   international cards, or agency cash (hold extended to 24 h).
4. **E-ticket**: HMAC-signed QR, cancellation with the fare's real penalty tiers.

The rules, with their sources, are in [`docs/booking-rules.md`](docs/booking-rules.md) and
[`docs/research/ferry-booking-algeria.md`](docs/research/ferry-booking-algeria.md).

## Live data — what is live and what is reference

* **Timetables and prices**: the API ships with a researched reference timetable and fare
  model for every route (flagged *Prix indicatif*). The scraper pipeline overlays real
  crossings and prices from the companies' sites (flagged *Prix en direct*); the backend keeps
  serving reference data if a source breaks, so search never goes down with a scraper.
  The spiders were verified on fixtures and end-to-end against the API, **not against the live
  sites** (unreachable from the build sandbox): run `wave-scraper check --live` before launch.
  Long term, agency/B2B feeds from partnerships should replace scraping.
* **Exchange rates**: official Banque d'Algérie rates, collected by the rates spider (or pushed
  through the same signed ingestion endpoint).
* **Ship positions**: AIS from aisstream.io (and an optional receiver at the port), otherwise
  positions estimated from the timetable, labelled as such.
* **Payments**: sandbox until SATIM/Stripe credentials are configured; SATIM requires the
  merchant certification by the acquiring bank.

## Run it locally

```bash
# Postgres 16+ and Redis 7 running locally (or: docker compose -f infra/docker-compose.yml up postgres redis)
cd backend && ./gradlew installDist
DATABASE_URL=postgres://wave:pass@localhost:5432/wave REDIS_URL=redis://localhost:6379/0 \
  build/install/wave-backend/bin/wave-backend          # API on :8080, metrics on :9090

cd mobile && flutter run -d chrome --dart-define=WAVE_API_URL=http://localhost:8080

cd scraper && pip install -r requirements-dev.txt && pip install --no-deps -e . \
  && WAVE_INGEST_SECRET=... wave-scraper --dry-run out.ndjson crawl
```

Production: `infra/docker-compose.yml` (single host, 3 API replicas behind nginx) or
`infra/k8s/` (autoscaling 6→40 API pods, CloudNativePG, PgBouncer, network policies).
See [`docs/scaling-100k.md`](docs/scaling-100k.md) and [`docs/security.md`](docs/security.md).

## Verified in this repository

| Check | Result |
|---|---|
| Backend tests (real Postgres + Redis) | 46 passed |
| Scraper tests (+ integration against the API through the LB) | 78 passed |
| Flutter analyze / tests | clean / 6 passed |
| Browser E2E: search → travellers → hold → CIB (sandbox) → QR e-ticket | passed |
| Production web build behind the LB, CSP enforced | no CSP violations; API, WebSocket and install id OK; fonts all bundled (no external font requests in the French and Arabic flows) |
| Load: 3 API replicas behind nginx on one 4-core VM (generator on the same VM) | 2,900 req/s, 0 errors, search p99 75 ms |
| Failover: one replica SIGKILLed under load | 1,500/1,500 requests OK |
| Redis frozen 20 s under traffic | 8,761 searches all OK (p99 10 ms); bookings `503 Retry-After`, then OK |
| Carrier NAT: 300 users behind one IP vs. one flooding client | 300/300 OK vs. throttled after burst |
| Backend container: prod mode, read-only root FS, non-root, no capabilities | healthy |
| Kubernetes manifests (kubeconform, strict, CRDs) / Prometheus rules (promtool) | 33/33 valid / 7/7 valid |
| GitHub Actions CI: backend, pipeline, app (analyze, tests, web, release APK), infra checks, 3 container images | all green |

The release APK and the container images are built by CI (no Android SDK in the sandbox).
Not run anywhere yet: the iOS build (needs macOS and Xcode) and the scrapers against the live
operator sites.

## Documentation

* [Architecture](docs/architecture.md) — components, data flows, consistency, failure modes
* [Booking rules](docs/booking-rules.md) — what the engine enforces and why
* [Scaling to 100k concurrent users](docs/scaling-100k.md) — capacity plan, measurements, load tests
* [Security](docs/security.md) — personal data, payments, abuse protection
* [Domain research](docs/research/ferry-booking-algeria.md) — operators, routes, rules, prices, sources
