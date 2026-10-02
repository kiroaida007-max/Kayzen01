-- WAVE schema. Personal data (travellers, contact, vehicle plate) is stored AES-GCM encrypted in
-- bookings.pii; the rest of the booking is JSONB for support tooling and analytics.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    full_name     TEXT        NOT NULL,
    phone         TEXT,
    role          TEXT        NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);

CREATE TABLE bookings (
    id              UUID PRIMARY KEY,
    reference       CHAR(6)     NOT NULL UNIQUE,
    status          TEXT        NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'TICKETED', 'CANCELLED', 'EXPIRED', 'REFUNDED')),
    user_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    pii             BYTEA       NOT NULL,
    document        JSONB       NOT NULL,
    hold_id         TEXT,
    hold_expires_at TIMESTAMPTZ,
    total_minor     BIGINT      NOT NULL,
    currency        CHAR(3)     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX bookings_user_idx ON bookings (user_id, created_at DESC);
CREATE INDEX bookings_status_idx ON bookings (status, created_at DESC);
CREATE INDEX bookings_hold_expiry_idx ON bookings (hold_expires_at) WHERE status = 'HELD';

-- LIVE sailings observed by the ingestion pipeline (scrapers / operator APIs).
CREATE TABLE sailing_overrides (
    id         TEXT PRIMARY KEY,
    operator   TEXT        NOT NULL,
    route_id   TEXT        NOT NULL,
    departure  TIMESTAMPTZ NOT NULL,
    data       JSONB       NOT NULL,
    fetched_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sailing_overrides_departure_idx ON sailing_overrides (departure);
