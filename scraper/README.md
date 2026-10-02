# WAVE data pipeline (Python)

Keeps the WAVE backend's timetables, prices, exchange rates and ship positions live:

```
 operator sites ──► Scrapy + Playwright ──► extractors ──► normalise/validate ──► signed POST
 (timetables,        (renders JS, captures    JSON-LD, embedded    ports, routes,       /internal/v1/ingest/sailings
  fares, promos)      the site's own XHR)      state, XHR, tables   local times, fares
                           │
                           └── nothing found ──► Firecrawl (rendered page + JSON-schema extraction)

 Banque d'Algérie ──► rates spider ──────────────────────────────────────────────► /internal/v1/ingest/rates
 AIS receiver (NMEA over UDP/TCP) ──► pyais ──► fleet filter, throttle ──────────► /internal/v1/ingest/positions
```

The backend keeps serving its reference timetable when a source is down: scraped crossings
replace reference ones (±3 h, same route) and are flagged `LIVE`, so a broken spider degrades
freshness, never availability.

## Components

| Piece | What it does |
|---|---|
| `spiders/operators.py` | One spider per company (AF, CL, BAL, GNV, NE, ATM); URLs in `data/sources.yaml` |
| `spiders/base.py` | Chromium rendering (scrapy-playwright), XHR capture, extractors, Firecrawl fallback |
| `spiders/discovery.py` | Crawls a company's domains for timetable pages; reports the productive ones |
| `spiders/rates.py` | Official EUR/USD dinar rates (selling rate, plausibility-checked) |
| `extract/` | JSON-LD `BoatTrip`, `__NEXT_DATA__`/`__NUXT__`/inline state, XHR JSON, HTML timetables |
| `firecrawl.py` | Firecrawl v2/v1 `scrape` with a JSON schema, and `map` for site discovery |
| `normalize.py` | Prices ("21 620 DA", "1.234,56 €"), dates and times in FR/EN/ES/IT, fare labels → API categories |
| `models.py` | Pydantic models of the ingestion payloads (validated before sending) |
| `publisher.py` | HMAC-SHA256 signed client, batching (500), retries on 429/5xx only, dry-run to NDJSON |
| `ais.py` | AIS forwarder for a receiver at the port (pyais), fleet MMSIs only, 20 s per ship |

Every record is checked against the backend catalog (`data/catalog.json`, exported with
`tools/export_catalog.py`): unknown ports or routes, departures in the past, wrong company,
implausible durations or prices are dropped and counted in the crawl stats
(`wave/rejected/<reason>`), never published.

## Usage

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt && pip install --no-deps -e .
python -m playwright install chromium

export WAVE_INGEST_URL=http://localhost:8080 WAVE_INGEST_KEY_ID=scraper WAVE_INGEST_SECRET=...
wave-scraper crawl                       # all companies
wave-scraper crawl --operators BAL,GNV
wave-scraper --dry-run out.ndjson crawl  # no publishing
wave-scraper rates
wave-scraper discover --operator CL      # → reports/discovery-YYYYMMDD.jsonl
FIRECRAWL_API_KEY=fc-... wave-scraper firecrawl https://www.balearia.com/... --operator BAL
wave-scraper ais --udp 0.0.0.0:10110     # e.g. rtl_ais -n → UDP 10110
wave-scraper check --live                # crossings found per company (dry run)
```

| Variable | Default | |
|---|---|---|
| `WAVE_INGEST_URL` | `http://localhost:8080` | Backend base URL |
| `WAVE_INGEST_KEY_ID` / `WAVE_INGEST_SECRET` | `scraper` / — | Must match the backend's `WAVE_INGEST_KEY_ID` / `WAVE_INGEST_SECRET` |
| `WAVE_DRY_RUN` | — | Write payloads to this NDJSON file instead of publishing |
| `WAVE_SOURCES` | packaged `data/sources.yaml` | Override the source list (e.g. a ConfigMap) |
| `FIRECRAWL_API_KEY` | — | Enables the Firecrawl fallback |
| `FIRECRAWL_API_URL` / `FIRECRAWL_API_VERSION` | `https://api.firecrawl.dev` / `v2` | Self-hosted Firecrawl or v1 |
| `WAVE_HTTP_CACHE=1` | off | Cache pages locally while working on extractors |

## Tests

```bash
pytest                         # 75 offline tests (fixtures for every extractor)
WAVE_INGEST_URL=http://localhost:8080 WAVE_INGEST_SECRET=... pytest tests/test_backend_integration.py
ruff check . && mypy wave_scraper
```

## Operating notes

* **Be a good citizen.** robots.txt is obeyed, the bot identifies itself, auto-throttle keeps
  one request at a time per site with 2 s+ delays, images and fonts are never downloaded.
  Scraping can still conflict with a company's terms: the long-term source should be the
  agency/B2B feeds that come with a partnership, which plug into the same ingestion API.
* **Site redesigns.** Extractors read data, not layout, so most redesigns need no code change;
  when `check --live` reports `NONE` for a company, run `discover` and update `sources.yaml`.
* **Not verified against the live sites from the development sandbox**, which cannot reach
  them: extractors are verified on fixtures shaped like each site's data, and end-to-end against
  the backend. Run `wave-scraper check --live` from a normal network before going live.
