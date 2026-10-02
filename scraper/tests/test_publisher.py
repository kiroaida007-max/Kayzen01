from __future__ import annotations

import hashlib
import hmac
import json
from datetime import UTC, datetime
from typing import Any

import httpx
import pytest

from wave_scraper.models import AisFix, Batch, Rates, Sailing
from wave_scraper.publisher import IngestClient, IngestConfig, IngestError, sign

SECRET = "dev-ingest-secret-please-change"


def sailing(i: int = 0) -> Sailing:
    return Sailing.model_validate(
        {
            "operator": "BAL",
            "from": "ESVLC",
            "to": "DZMOS",
            "departureLocal": f"2026-10-{14 + i % 10:02d}T23:00",
            "durationMin": 840,
        }
    )


def client(handler: Any, sleeps: list[float] | None = None) -> IngestClient:
    return IngestClient(
        IngestConfig("http://backend", "scraper", SECRET),
        transport=httpx.MockTransport(handler),
        sleep=(sleeps.append if sleeps is not None else lambda _: None),
        clock=lambda: 1_790_000_000,
    )


def test_signature_matches_the_backend_scheme() -> None:
    body = b'{"a":1}'
    expected = hmac.new(SECRET.encode(), b"1790000000." + body, hashlib.sha256).hexdigest()
    assert sign(SECRET, 1_790_000_000, body) == expected


def test_signed_ascii_request() -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(200, json={"accepted": 1, "rejected": [], "catalogVersion": 7})

    batch = Batch(source="test", fetchedAt=datetime(2026, 10, 2, 8, 0, tzinfo=UTC), sailings=[sailing()])
    answer = client(handler).send_sailings(batch)
    assert answer == {"accepted": 1, "rejected": [], "catalogVersion": 7}
    (request,) = seen
    assert request.url.path == "/internal/v1/ingest/sailings"
    assert request.headers["X-Wave-Key"] == "scraper"
    assert request.headers["X-Wave-Timestamp"] == "1790000000"
    assert request.headers["X-Wave-Signature"] == sign(SECRET, 1_790_000_000, request.content)
    request.content.decode("ascii")  # non-ASCII is escaped, so both sides hash identical bytes
    payload = json.loads(request.content)
    assert payload["fetchedAt"] == "2026-10-02T08:00:00Z"
    assert payload["sailings"][0]["from"] == "ESVLC" and "origin" not in payload["sailings"][0]


def test_retries_transient_errors_with_backoff() -> None:
    answers = iter(
        [
            httpx.Response(503),
            httpx.Response(429, headers={"Retry-After": "7"}),
            httpx.Response(200, json={"accepted": 1}),
        ]
    )
    sleeps: list[float] = []
    result = client(lambda _: next(answers), sleeps).send_rates(
        Rates(rates={"EUR": "151.06", "USD": "132.73"}, asOf=datetime.now(UTC), source="test")
    )
    assert result == {"accepted": 1}
    assert sleeps == [1.0, 7.0]


def test_client_errors_are_not_retried() -> None:
    calls: list[int] = []

    def handler(_: httpx.Request) -> httpx.Response:
        calls.append(1)
        return httpx.Response(401, json={"code": "UNAUTHORIZED"})

    with pytest.raises(IngestError) as error:
        client(handler).send_positions(
            [AisFix(mmsi="605016420", lat=38.5, lon=4.2, speedKn=20.6, courseDeg=19.3, timestamp=datetime.now(UTC))]
        )
    assert error.value.status == 401 and len(calls) == 1


def test_large_batches_are_split() -> None:
    sizes: list[int] = []

    def handler(request: httpx.Request) -> httpx.Response:
        count = len(json.loads(request.content)["sailings"])
        sizes.append(count)
        return httpx.Response(200, json={"accepted": count, "rejected": ["x"] if count < 500 else []})

    batch = Batch(source="test", fetchedAt=datetime.now(UTC), sailings=[sailing(i) for i in range(1200)])
    answer = client(handler).send_sailings(batch)
    assert sizes == [500, 500, 200]
    assert answer["accepted"] == 1200 and answer["rejected"] == ["x"]


def test_models_reject_bad_payloads() -> None:
    with pytest.raises(ValueError, match="yyyy-MM-ddTHH:mm"):
        Sailing.model_validate(
            {
                "operator": "AF",
                "from": "DZALG",
                "to": "FRMRS",
                "departureLocal": "2026-10-14 20:00",
                "durationMin": 1200,
            }
        )
    with pytest.raises(ValueError, match="arrival time or duration"):
        Sailing.model_validate({"operator": "AF", "from": "DZALG", "to": "FRMRS", "departureLocal": "2026-10-14T20:00"})
    with pytest.raises(ValueError, match="implausible EUR"):
        Rates(rates={"EUR": "275.00"} | {"EUR": "15.1"}, asOf=datetime.now(UTC), source="x")
