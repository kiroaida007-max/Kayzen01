// WAVE load test: browsing, varied searches and (optionally) bookings, each virtual user acting as
// one app install (X-Wave-Client) the way real travellers behind carrier NAT do.
//
//   k6 run -e BASE_URL=https://staging.wave.dz -e SEARCH_RPS=2000 -e DURATION=10m infra/loadtest/k6.js
//
// Thresholds encode the service objectives; k6 exits non-zero when they are missed.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8088';
const SEARCH_RPS = Number(__ENV.SEARCH_RPS || 200);
const BROWSE_RPS = Number(__ENV.BROWSE_RPS || SEARCH_RPS);
const BOOK_RPS = Number(__ENV.BOOK_RPS || 0);
const DURATION = __ENV.DURATION || '2m';
const RAMP = __ENV.RAMP || '30s';

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const between = (min, max) => min + Math.floor(Math.random() * (max - min + 1));

const ROUTES = [
  ['DZALG', 'FRMRS'], ['FRMRS', 'DZALG'], ['DZORN', 'FRMRS'], ['FRMRS', 'DZORN'], ['DZBJA', 'FRMRS'],
  ['FRMRS', 'DZBJA'], ['DZALG', 'ESBCN'], ['ESBCN', 'DZALG'], ['DZORN', 'ESVLC'], ['ESVLC', 'DZORN'],
  ['DZMOS', 'ESVLC'], ['ESVLC', 'DZMOS'], ['DZALG', 'ESALC'], ['ESALC', 'DZALG'], ['DZALG', 'FRSET'],
  ['FRSET', 'DZBJA'], ['DZAAE', 'ITCVV'], ['ITCVV', 'DZAAE'], ['DZGHZ', 'ESLEI'], ['ESLEI', 'DZGHZ'],
];
const ACCOMMODATION = ['SEAT', 'CABIN_ANY', 'CABIN_INTERIOR', 'CABIN_EXTERIOR'];
const CURRENCY = ['DZD', 'DZD', 'DZD', 'EUR', 'USD'];

const day = (offset) => new Date(Date.now() + offset * 86400000).toISOString().slice(0, 10);
// Traffic comes from a population of app installs (default 100k), each making a few requests, like
// real travellers. Run from several IPs or from an allowlisted generator (LB geo $loadgen).
const INSTALLS = Number(__ENV.INSTALLS || 100000);
const installId = () => `k6-install-${String(between(1, INSTALLS)).padStart(8, '0')}`;
const headers = () => ({ 'Content-Type': 'application/json', 'X-Wave-Client': installId() });

export const options = {
  discardResponseBodies: false,
  scenarios: {
    browse: {
      executor: 'ramping-arrival-rate', exec: 'browse', timeUnit: '1s', startRate: 1,
      preAllocatedVUs: 50, maxVUs: 2000,
      stages: [{ target: BROWSE_RPS, duration: RAMP }, { target: BROWSE_RPS, duration: DURATION }],
    },
    search: {
      executor: 'ramping-arrival-rate', exec: 'search', timeUnit: '1s', startRate: 1,
      preAllocatedVUs: 100, maxVUs: 4000,
      stages: [{ target: SEARCH_RPS, duration: RAMP }, { target: SEARCH_RPS, duration: DURATION }],
    },
    ...(BOOK_RPS > 0 ? {
      book: {
        executor: 'constant-arrival-rate', exec: 'book', timeUnit: '1s', rate: BOOK_RPS,
        duration: DURATION, preAllocatedVUs: 20, maxVUs: 500,
      },
    } : {}),
  },
  thresholds: {
    'http_req_failed{scenario:search}': ['rate<0.01'],
    'http_req_failed{scenario:browse}': ['rate<0.01'],
    'http_req_duration{scenario:search}': ['p(95)<400', 'p(99)<1000'],
    'http_req_duration{scenario:browse}': ['p(95)<250'],
    checks: ['rate>0.99'],
  },
};

export function browse() {
  const paths = ['/api/v1/routes', '/api/v1/live/vessels', '/api/v1/content/deals', '/api/v1/currency/rates'];
  const res = http.get(`${BASE}${pick(paths)}`, { headers: headers(), tags: { name: 'browse' } });
  check(res, { 'browse 200': (r) => r.status === 200 });
}

function searchBody() {
  const [from, to] = pick(ROUTES);
  const children = Math.random() < 0.4 ? [between(0, 17)] : [];
  return {
    tripType: Math.random() < 0.3 ? 'ROUND_TRIP' : 'ONE_WAY',
    from, to,
    departureDate: day(between(3, 150)),
    returnDate: day(between(160, 200)),
    passengers: { adults: between(1, 4), childrenAges: children },
    vehicle: Math.random() < 0.5 ? { type: 'CAR' } : undefined,
    accommodation: pick(ACCOMMODATION),
    currency: pick(CURRENCY),
  };
}

export function search() {
  const res = http.post(`${BASE}/api/v1/search`, JSON.stringify(searchBody()), { headers: headers(), tags: { name: 'search' } });
  check(res, { 'search 200': (r) => r.status === 200 && r.json('outbound') !== undefined });
}

// Bookings hold real inventory for 20 minutes: run against staging only.
export function book() {
  const body = searchBody();
  body.tripType = 'ONE_WAY';
  body.passengers = { adults: 1, childrenAges: [] };
  body.vehicle = undefined;
  body.accommodation = 'SEAT';
  const found = http.post(`${BASE}/api/v1/search`, JSON.stringify(body), { headers: headers(), tags: { name: 'book-search' } });
  if (found.status !== 200) return;
  const offer = (found.json('outbound.offers') || []).find((o) => o.bookable && o.cheapest);
  if (!offer) return;
  const selection = {
    outbound: { sailingId: offer.sailingId, accommodation: 'SEAT', tariff: offer.cheapest.code },
    passengers: { adults: 1, childrenAges: [] }, currency: body.currency,
  };
  const quote = http.post(`${BASE}/api/v1/quote`, JSON.stringify(selection), { headers: headers(), tags: { name: 'quote' } });
  if (quote.status !== 200) return;
  const booking = http.post(`${BASE}/api/v1/bookings`, JSON.stringify({
    selection,
    travellers: [{
      firstName: 'Load', lastName: 'Test', sex: 'F', dateOfBirth: '1990-01-01', nationality: 'DZ',
      document: { type: 'PASSPORT', number: `LT${between(1000000, 9999999)}`, expiry: '2034-01-01', issuingCountry: 'DZ' },
    }],
    contact: { email: 'loadtest@example.com', phone: '+213550000000' },
    acceptTerms: true,
    expectedTotal: quote.json('total'),
  }), { headers: { ...headers(), 'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}` }, tags: { name: 'book' } });
  check(booking, { 'booking held': (r) => r.status === 201 || r.status === 409 });
}
