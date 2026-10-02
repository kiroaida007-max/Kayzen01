# WAVE mobile & web app (Flutter)

The traveller app: search, compare and book ferry crossings between Algeria and Europe,
pay in dinars (CIB / Edahabia) or by card, keep e-tickets, and follow the ships on a live map.
One Flutter code base ships to Android, iOS and the web; the Android host is Kotlin.

## Screens

| Route | Screen |
|---|---|
| `/` | Home: hero, search form, popular destinations, partner companies, figures |
| `/results` | Crossings for the search: nearby-day prices, sort/filter, fares, price breakdown, rule notices |
| `/booking` | Travellers (passport data), vehicle, contact, live quote and price-change check |
| `/booking/:ref` | Payment while places are held, then the signed QR e-ticket; cancellation with fee preview |
| `/live` | Live map: ships (AIS or schedule-estimated), routes, ports; offline coastline under the tiles |
| `/routes`, `/companies`, `/ships`, `/deals`, `/guides`, `/tv`, `/community` | Content |
| `/trips` | "Ma vague": saved bookings (device keystore), booking lookup, favourite routes |

French (reference), English and Arabic (right-to-left) are built in; prices switch between
DZD, EUR and USD at the Banque d'Algérie rate returned by the API.

## Run

```bash
flutter pub get
flutter run --dart-define=WAVE_API_URL=http://10.0.2.2:8080   # Android emulator → local backend
flutter run -d chrome --dart-define=WAVE_API_URL=http://localhost:8080
flutter test && flutter analyze
```

Build-time settings (`--dart-define`):

| Key | Default | Purpose |
|---|---|---|
| `WAVE_API_URL` | same origin on web, `http://10.0.2.2:8080` on Android | Backend base URL |
| `WAVE_WHATSAPP_1`, `WAVE_WHATSAPP_2` | agency numbers | "Nous contacter" |
| `WAVE_POWERED_BY` | empty (hidden) | Optional line under the logo |
| `WAVE_TILE_URL`, `WAVE_SEAMARK_URL` | OSM / OpenSeaMap | Map tiles (use a commercial or self-hosted tile service in production) |

Release builds:

```bash
flutter build appbundle --release --obfuscate --split-debug-info=build/symbols --dart-define=WAVE_API_URL=https://api.wave.dz
flutter build web --release --no-web-resources-cdn
```

Android signing reads `android/key.properties` (not in git). The web build is served by nginx
(see `infra/`), which falls back to `index.html` for the clean URLs.

## Security notes

* Booking references and the lookup name are stored with `flutter_secure_storage`
  (Android Keystore / iOS Keychain), never in plain preferences.
* While passport details are on screen, Android marks the window secure
  (`MainActivity.kt`): no screenshots, recordings or recent-apps thumbnails.
* Android: HTTPS only (`network_security_config.xml`), backups and device transfer disabled,
  release builds shrunk with R8.
* Bank pages (SATIM, Stripe) open in the system browser, never in a WebView; the app then
  checks the payment with the API.
* Every booking POST carries an `Idempotency-Key` and the expected total, so a retry never books
  twice and a price change is always shown before it is charged.
