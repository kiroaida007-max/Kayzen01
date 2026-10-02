# Security

WAVE handles passports, phone numbers, payments and boarding documents. This is what protects
them, organised by asset.

## Personal data (passports, contacts)

| Control | Where |
|---|---|
| Travellers, contact and vehicle details encrypted at rest with **AES-256-GCM** (random 96-bit IV per record, authenticated); only non-personal fields stay queryable | `booking/Security.kt` `PiiCipher`, `BookingRepository` |
| API responses mask passport numbers (`•••• 123`) — support screens and screenshots never show the full number | `api/Dtos.kt` `toView()` |
| Booking lookup needs the reference **and** a traveller's last name; lookups are rate-limited (30/min per install) | `BookingService.find`, `Limits429.LOOKUP` |
| App: references and lookup names stored in the Android Keystore / iOS Keychain (`flutter_secure_storage`), never in plain preferences | `mobile/lib/state/providers.dart` |
| Android: the window is marked `FLAG_SECURE` while passport details are on screen (no screenshots, recordings or recent-apps thumbnail); backups and device transfer disabled | `MainActivity.kt`, `AndroidManifest.xml` |
| Logs contain request IDs, never names, passport numbers or card data | access log format, `CallLogging` |
| Retention: bookings are needed for boarding, refunds and accounting; purge or anonymise them after the legal retention period with a scheduled job | operations |

Algeria's **Law 18-07** (protection of personal data, ANPDP authority) and, for travellers in
Europe, the **GDPR** apply: declare the processing to the ANPDP, publish a privacy notice,
and sign data-processing agreements with the operators and payment providers.

## Payments

* Card data never reaches WAVE: CIB/Edahabia are entered on SATIM's page, international cards
  on Stripe Checkout; the app opens them in the system browser, never in a WebView.
* A payment is approved only after a **server-to-server check** with the provider: SATIM
  `confirmOrder`/status must return `OrderStatus = 2`, `actionCode = 0` and the expected amount
  in dinars; Stripe events are accepted only with a valid `Stripe-Signature`. Query strings on
  the return URL are never trusted.
* Amount tampering is impossible from the client: the server recomputes the quote and the
  client only states the total it expects (`409 PRICE_CHANGED` if different).
* Idempotency keys make retries safe; holds are atomic, so double booking cannot happen.

## Accounts and staff access

* Passwords hashed with **Argon2id** (OWASP parameters: 19 MiB, 2 iterations), constant-time
  comparison; 10 auth attempts per minute per install.
* JWT access tokens (HS256, issuer and audience checked, 15-minute lifetime) and rotating
  refresh tokens stored server-side as hashes (revocable on logout).
* Staff endpoints (ticket verification, ticketing, agency cash approval) require the `STAFF` or
  `ADMIN` role.

## E-tickets

* The QR payload is `WAVE1.<reference>.<issued-at>.<HMAC-SHA256>` with a server-side key:
  a forged or edited ticket fails verification at the port (`POST /tickets/verify`).

## Data pipeline

* Ingestion endpoints are not routed by the public load balancer/ingress (404) and are
  reachable only inside the private network (NetworkPolicy).
* Every request is signed: `X-Wave-Signature = HMAC-SHA256(secret, "<timestamp>.<body>")`,
  timestamp within ±5 minutes (replay window), constant-time comparison; the scraper sends
  ASCII-only bodies so both sides hash identical bytes.
* Scraped values are validated (known ports/routes/operators, plausible durations, prices
  and rates) before they can affect search.

## Abuse and availability

* Edge: TLS only (HSTS), per-install and per-IP rate limits, connection caps, body size and
  slow-client timeouts; the API repeats the per-install limits.
* API: request bodies limited (256 KB public), strict JSON parsing, input validation on every
  field, uniform error format without stack traces.
* Security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy`, a restrictive `Content-Security-Policy` on the API and a tailored one on
  the web app (no inline scripts, `wasm-unsafe-eval` only for the rendering engine,
  `frame-ancestors 'none'`). Verified in a browser: no CSP violations.
* CORS restricted to the production origins in production.

## Infrastructure

* Containers run as non-root with a read-only root filesystem, all Linux capabilities dropped,
  `no-new-privileges` and the `RuntimeDefault` seccomp profile; the namespace enforces the
  Kubernetes **restricted** Pod Security Standard.
* NetworkPolicies: default deny; only the ingress controller reaches the API and web pods,
  only the API reaches Redis and the PgBouncer poolers, only Prometheus reaches metrics.
* Secrets (`WAVE_JWT_SECRET`, `WAVE_PII_KEY`, `WAVE_TICKET_KEY`, `WAVE_INGEST_SECRET`, payment
  credentials) come from the environment; production refuses to start without them. Use a
  secret manager (External Secrets, Sealed Secrets, cloud KMS) and rotate the ingest secret
  and JWT secret periodically. Rotating `WAVE_PII_KEY` requires re-encrypting stored bookings.
* Mobile: HTTPS-only network configuration; release builds shrunk and obfuscated
  (`--obfuscate`, R8).

## Before going live

1. Penetration test of the API and the payment flows; SATIM merchant certification.
2. Dependency and image scanning in CI (e.g. Dependabot, Trivy) and a patch routine.
3. Backups: verify a point-in-time restore of Postgres.
4. Incident response: who is paged for `WaveHighErrorRate`, `WaveDependencyDown`,
   `WavePaymentsFailing`, and how travellers are informed.
