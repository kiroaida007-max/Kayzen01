"""Forwards positions from an AIS receiver (e.g. an RTL-SDR dongle running rtl_ais on the port
roof, or a partner's NMEA feed) to the backend live map.

The backend already listens to aisstream.io; a local receiver adds coverage near the Algerian
coast, where community AIS stations are sparse."""

from __future__ import annotations

import logging
import time
from collections.abc import Callable, Iterable
from datetime import UTC, datetime
from typing import Any

from pydantic import ValidationError

from .models import AisFix
from .publisher import IngestClient

log = logging.getLogger(__name__)

POSITION_TYPES = {1, 2, 3, 18, 19, 27}


def fix_from_message(decoded: Any, now: datetime | None = None) -> AisFix | None:
    """AIS 'not available' sentinels (lat 91, lon 181, speed 102.3, course 360, heading 511) are
    dropped or mapped to None, so the map never shows a ship at 91°N."""
    if getattr(decoded, "msg_type", None) not in POSITION_TYPES:
        return None
    lat, lon = getattr(decoded, "lat", None), getattr(decoded, "lon", None)
    speed = getattr(decoded, "speed", None)
    if lat is None or lon is None or abs(lat) > 90 or abs(lon) > 180 or speed is None or speed >= 102.2:
        return None
    heading_raw = getattr(decoded, "heading", None)
    heading = float(heading_raw) if heading_raw is not None and 0 <= heading_raw < 360 else None
    course_raw = getattr(decoded, "course", None)
    course = float(course_raw) if course_raw is not None and 0 <= course_raw < 360 else (heading or 0.0)
    try:
        return AisFix(
            mmsi=str(decoded.mmsi).zfill(9),
            lat=float(lat),
            lon=float(lon),
            speedKn=float(speed),
            courseDeg=course,
            heading=heading,
            timestamp=now or datetime.now(UTC),
        )
    except ValidationError:
        return None


class AisForwarder:
    def __init__(
        self,
        client: IngestClient,
        mmsis: set[str] | None,
        min_interval_s: float = 20.0,
        flush_every_s: float = 5.0,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self.client = client
        self.mmsis = mmsis
        self.min_interval_s = min_interval_s
        self.flush_every_s = flush_every_s
        self.clock = clock
        self.buffer: dict[str, AisFix] = {}
        self.last_sent: dict[str, float] = {}
        self.last_flush = clock()
        self.forwarded = 0

    def handle(self, decoded: Any) -> None:
        fix = fix_from_message(decoded)
        if fix is None or (self.mmsis is not None and fix.mmsi not in self.mmsis):
            return
        now = self.clock()
        if now - self.last_sent.get(fix.mmsi, -1e9) < self.min_interval_s:
            return  # Class A ships report every 2-10 s underway: 20 s is plenty for a map.
        self.last_sent[fix.mmsi] = now
        self.buffer[fix.mmsi] = fix
        self.maybe_flush()

    def maybe_flush(self, force: bool = False) -> None:
        now = self.clock()
        if not self.buffer or (not force and now - self.last_flush < self.flush_every_s):
            return
        fixes, self.buffer = list(self.buffer.values()), {}
        self.last_flush = now
        try:
            self.client.send_positions(fixes)
            self.forwarded += len(fixes)
        except Exception as e:
            log.warning("Could not forward %d AIS fixes: %s", len(fixes), e)

    def consume(self, messages: Iterable[Any]) -> None:
        for message in messages:
            try:
                decoded = message.decode()
            except Exception:  # noqa: S112 - corrupted radio sentences are normal
                continue
            self.handle(decoded)


def run(client: IngestClient, mmsis: set[str] | None, udp: str | None = None, tcp: str | None = None) -> None:
    from pyais.stream import TCPConnection, UDPReceiver

    forwarder = AisForwarder(client, mmsis)
    target = udp or tcp
    if not target:
        raise ValueError("--udp or --tcp is required")
    host, _, port = target.rpartition(":")
    stream = UDPReceiver(host or "0.0.0.0", int(port)) if udp else TCPConnection(host, int(port))  # noqa: S104
    log.info("Forwarding AIS from %s %s for %s ships", "udp" if udp else "tcp", target, len(mmsis) if mmsis else "all")
    try:
        forwarder.consume(stream)
    finally:
        forwarder.maybe_flush(force=True)
