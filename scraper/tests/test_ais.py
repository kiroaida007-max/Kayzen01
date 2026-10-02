from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

import pyais
from pyais.encode import encode_dict

from wave_scraper.ais import AisForwarder, fix_from_message
from wave_scraper.models import AisFix


def decoded(**fields: Any) -> Any:
    sentences = encode_dict({"type": 1, "mmsi": 605016420, **fields}, sentence_type="VDM")
    return pyais.decode(*sentences)


def test_position_reports_become_fixes() -> None:
    fix = fix_from_message(
        decoded(lat=38.5, lon=4.2, speed=20.6, course=19.3, heading=20), now=datetime(2026, 10, 2, tzinfo=UTC)
    )
    assert fix == AisFix(
        mmsi="605016420",
        lat=38.5,
        lon=4.2,
        speedKn=20.6,
        courseDeg=19.3,
        heading=20.0,
        timestamp=datetime(2026, 10, 2, tzinfo=UTC),
    )


def test_not_available_sentinels() -> None:
    assert fix_from_message(decoded(lat=91, lon=181, speed=10, course=0)) is None
    assert fix_from_message(decoded(lat=38.5, lon=4.2, speed=102.3, course=0)) is None
    fix = fix_from_message(decoded(lat=38.5, lon=4.2, speed=0, course=360, heading=511))
    assert fix is not None and fix.courseDeg == 0.0 and fix.heading is None
    static = pyais.decode(
        *encode_dict({"type": 5, "mmsi": 605016420, "shipname": "BADJI MOKHTAR III"}, sentence_type="VDM")
    )
    assert fix_from_message(static) is None


class FakeClient:
    def __init__(self) -> None:
        self.sent: list[list[AisFix]] = []

    def send_positions(self, fixes: list[AisFix]) -> dict[str, Any]:
        self.sent.append(fixes)
        return {"accepted": len(fixes)}


def test_forwarder_filters_throttles_and_batches() -> None:
    now = [0.0]
    client = FakeClient()
    forwarder = AisForwarder(client, {"605016420"}, min_interval_s=20, flush_every_s=5, clock=lambda: now[0])  # type: ignore[arg-type]
    forwarder.handle(decoded(lat=38.5, lon=4.2, speed=20, course=10))
    other = pyais.decode(
        *encode_dict(
            {"type": 1, "mmsi": 227000000, "lat": 43.0, "lon": 5.0, "speed": 12, "course": 90}, sentence_type="VDM"
        )
    )
    forwarder.handle(other)  # not one of ours
    now[0] = 3
    forwarder.handle(decoded(lat=38.51, lon=4.21, speed=20, course=10))  # too soon after the previous fix
    assert client.sent == []
    now[0] = 25
    forwarder.handle(decoded(lat=38.6, lon=4.3, speed=20, course=10))
    assert len(client.sent) == 1 and [f.lat for f in client.sent[0]] == [38.6]
    assert forwarder.forwarded == 1
