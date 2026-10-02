# Scaling to 100,000 concurrent users

## What "100k concurrent users" means in requests

A traveller with the app open does not send a request every second. From the app's behaviour
(one search per date/route change, metadata cached with ETag, map positions over one
WebSocket), the planning assumptions are:

| Assumption | Value |
|---|---|
| Average API requests per active session | 1 every 10 s |
| Request mix | ~40 % search, ~50 % catalogue/content/map snapshots, ~1 % booking & payment |
| Sessions with the live map open | 15 % (one WebSocket each) |
| Peak-to-average burst | × 1.5 |

**Design point: 100,000 sessions → 10,000 req/s sustained, 15,000 req/s bursts, 15,000
WebSockets, ~100–150 booking writes/s.** Summer departures and the days before Eid are the
peaks.

## What was measured

All on one 4-vCPU VM shared by the load generator (k6), the nginx load balancer, three API
replicas, Postgres and Redis, so per-pod numbers on dedicated nodes are higher.

| Test | Result |
|---|---|
| Realistic mix (`infra/loadtest/k6.js`), target 1,400 req/s | 98,016 requests, **0 errors**, search p95 1.8 ms, p99 3.6 ms |
| Same, target 3,500 req/s | 260,998 requests at ~2,900 req/s average, **0 errors**, search p95 34 ms, p99 75 ms (VM saturated, load average 10.5) |
| One replica `SIGKILL`ed under load | 1,500 / 1,500 requests successful; 12 in-flight requests retried on another pod by the load balancer |
| 300 users behind one carrier NAT IP vs. one client flooding | 300 / 300 served; the flooding client throttled after its burst (23 OK, then 429) |
| Redis frozen for 20 s (`CLIENT PAUSE`) during traffic | 8,761 searches, all 200, p99 10 ms; bookings answered `503 Retry-After: 5` in 2 s and worked again right after; no pod left the load balancer |
| Ingest on one replica | all three replicas reloaded the new catalog version (Redis bus) |

From the saturated run, the API pods account for roughly 60 % of the VM's CPU, i.e. about
1,200 search-heavy requests/s per vCPU. The plan below assumes **~1,500 req/s per 2-vCPU pod
at 60 % CPU** — conservative, and to be confirmed by a load test on the target nodes.

## Capacity plan

| Layer | Sizing for the design point | Headroom |
|---|---|---|
| API pods (2 vCPU, 1.5 GiB) | 15,000 / 1,500 ≈ **10 pods** at 60 % CPU | HPA 6 → 40 pods; scale-up doubles every 30 s |
| Ingress (ingress-nginx) | 3 replicas; ~10k req/s each | add replicas / a cloud L4 load balancer in front |
| WebSockets | 15,000 connections ≈ 1,500 per pod; one 5-second broadcast of ~2 KB each | SharedFlow drops frames for slow clients, never blocks |
| PostgreSQL (CloudNativePG) | 1 primary + 2 replicas, 2 vCPU / 8 GiB each; ~150 writes/s | PgBouncer: 40 pods × 16 connections → 60 server connections; reads can go to replicas (`DATABASE_READ_URL`) |
| Redis | 1 instance, ~2 ops per booking, cached hold counters for search | Managed Redis / Sentinel for HA |
| Web app | static, behind a CDN (CanvasKit ≈ 7 MB, cached) | CDN offloads almost all bytes |
| Data pipeline | CronJobs, one request per site at a time | independent of user traffic |

Search does not touch Postgres, so database load only grows with bookings, not with browsing.

## What keeps it stable

**Spread and isolate the load**
* Stateless API pods, in-memory immutable catalog, any pod serves any request; topology
  spread across zones and nodes; PodDisruptionBudget keeps 75 % during node maintenance.
* Least-connections / EWMA balancing with keep-alive pools; dead pods get no traffic within
  seconds (readiness) and in-flight GETs are retried elsewhere (`proxy_next_upstream`).

**Shed abuse, not customers**
* Rate limits per **app install** (`X-Wave-Client`) at the edge and in the API, plus a hard
  per-IP ceiling: Algerian mobile carriers put thousands of subscribers behind one NAT
  address, so per-IP-only limits would throttle real users during peaks.
* Request bodies capped (512 KB public), short header/body timeouts against slow clients.

**Fail fast and degrade**
* Postgres pool: 3 s connection timeout. Redis: 1 s command timeout, commands rejected (not
  queued) while disconnected.
* A Postgres or Redis outage answers `503 + Retry-After` on booking only; search, catalogue and
  map keep working (verified). Readiness never depends on shared services, so a dependency
  incident cannot empty the load balancer.
* Scraper outage → reference prices (labelled); tile outage → bundled coastline; card gateway
  outage → agency payment.

**Never lose or double money**
* Idempotency keys on booking creation; atomic all-or-nothing holds in Redis; optimistic
  locking on bookings; payment approval only after server-side verification with the bank.

**Cache what can be cached**
* Search results cached per pod (key: request + catalog version + rates version + 5-minute
  bucket); `GET /meta` revalidated with ETag (304); hold counters cached 5 s per pod.

**See problems first**
* Prometheus metrics (HTTP latency histograms, JVM, business counters:
  `wave_ingest_sailings_total`, `wave_bookings_total`, `wave_payments_total`,
  `wave_dependency_up`, `wave_live_sailings`) and alerts: 5xx rate, search p99, dependency
  down, autoscaler at max, payments failing, no live data. JSON logs with the request ID that
  the load balancer adds (`X-Request-Id`).

## Before a peak (summer, Eid)

1. Raise `minReplicas` (e.g. 12) a day before; pre-scale the node pool.
2. Load-test staging at 2× the expected peak with `infra/loadtest/k6.js` from several
   machines (or allowlist the generator in the load balancer's `geo $loadgen` block — the
   per-IP ceiling otherwise caps a single generator at 400 req/s, by design).
3. Freeze deployments; keep the previous image ready for `kubectl rollout undo`.
4. Watch: error rate, search p99, HPA replicas, Postgres connections, Redis memory, live-data
   freshness.

```bash
k6 run -e BASE_URL=https://staging.wave.dz -e SEARCH_RPS=6000 -e BROWSE_RPS=7500 \
       -e BOOK_RPS=20 -e DURATION=20m infra/loadtest/k6.js
```
