"""Signed client for the backend ingestion API.

Every request carries `X-Wave-Key`, `X-Wave-Timestamp` and
`X-Wave-Signature = hex(HMAC-SHA256(secret, "<timestamp>.<body>"))`, the scheme verified by
`InternalRoutes.kt`. Bodies are ASCII-only JSON so both sides hash exactly the same bytes."""

from __future__ import annotations

import hashlib
import hmac
import json
import logging
import os
import time
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx

from .models import AisFix, Batch, Rates

log = logging.getLogger(__name__)

MAX_SAILINGS_PER_REQUEST = 500


def sign(secret: str, timestamp: int, body: bytes) -> str:
    return hmac.new(secret.encode("utf-8"), f"{timestamp}.".encode() + body, hashlib.sha256).hexdigest()


def encode(payload: Any) -> bytes:
    return json.dumps(payload, separators=(",", ":"), ensure_ascii=True).encode("ascii")


@dataclass(frozen=True)
class IngestConfig:
    base_url: str
    key_id: str
    secret: str
    dry_run_path: Path | None = None

    @staticmethod
    def from_env() -> IngestConfig:
        dry_run = os.environ.get("WAVE_DRY_RUN")
        secret = os.environ.get("WAVE_INGEST_SECRET", "")
        if not secret and not dry_run:
            raise RuntimeError("WAVE_INGEST_SECRET is required (or set WAVE_DRY_RUN=<file.ndjson>)")
        return IngestConfig(
            base_url=os.environ.get("WAVE_INGEST_URL", "http://localhost:8080").rstrip("/"),
            key_id=os.environ.get("WAVE_INGEST_KEY_ID", "scraper"),
            secret=secret,
            dry_run_path=Path(dry_run) if dry_run else None,
        )


class IngestClient:
    def __init__(
        self,
        config: IngestConfig,
        transport: httpx.BaseTransport | None = None,
        max_attempts: int = 5,
        sleep: Callable[[float], None] = time.sleep,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self.config = config
        self._max_attempts = max_attempts
        self._sleep = sleep
        self._clock = clock
        self._client = httpx.Client(
            base_url=config.base_url,
            timeout=httpx.Timeout(30.0, connect=10.0),
            transport=transport,
            headers={"User-Agent": "wave-scraper/1.0"},
        )

    def send_sailings(self, batch: Batch) -> dict[str, Any]:
        """Splits large batches; returns the summed backend answer."""
        total: dict[str, Any] = {"accepted": 0, "rejected": []}
        for start in range(0, max(len(batch.sailings), 1), MAX_SAILINGS_PER_REQUEST):
            chunk = batch.model_copy(update={"sailings": batch.sailings[start : start + MAX_SAILINGS_PER_REQUEST]})
            if not chunk.sailings:
                break
            answer = self._post("/internal/v1/ingest/sailings", chunk.payload())
            total["accepted"] += int(answer.get("accepted", 0))
            total["rejected"] += list(answer.get("rejected", []))
            total["catalogVersion"] = answer.get("catalogVersion")
        return total

    def send_rates(self, rates: Rates) -> dict[str, Any]:
        return self._post("/internal/v1/ingest/rates", rates.model_dump(mode="json"))

    def send_positions(self, fixes: Sequence[AisFix]) -> dict[str, Any]:
        if not fixes:
            return {"accepted": 0}
        return self._post(
            "/internal/v1/ingest/positions", [f.model_dump(mode="json", exclude_none=True) for f in fixes]
        )

    def _post(self, path: str, payload: Any) -> dict[str, Any]:
        body = encode(payload)
        if self.config.dry_run_path is not None:
            with self.config.dry_run_path.open("a", encoding="utf-8") as out:
                out.write(json.dumps({"path": path, "payload": payload}, ensure_ascii=False) + "\n")
            return {
                "accepted": len(payload["sailings"]) if isinstance(payload, dict) and "sailings" in payload else 1,
                "rejected": [],
                "dryRun": True,
            }
        delay = 1.0
        for attempt in range(1, self._max_attempts + 1):
            timestamp = int(self._clock())
            headers = {
                "Content-Type": "application/json; charset=utf-8",
                "X-Wave-Key": self.config.key_id,
                "X-Wave-Timestamp": str(timestamp),
                "X-Wave-Signature": sign(self.config.secret, timestamp, body),
            }
            try:
                response = self._client.post(path, content=body, headers=headers)
            except httpx.TransportError as e:
                if attempt == self._max_attempts:
                    raise
                log.warning("ingest %s: %s (attempt %d), retrying in %.0fs", path, e, attempt, delay)
            else:
                if response.status_code < 300:
                    answer: dict[str, Any] = response.json()
                    return answer
                if response.status_code not in (429, 502, 503, 504) or attempt == self._max_attempts:
                    # 4xx means a contract or signature problem: retrying cannot help.
                    raise IngestError(response.status_code, response.text[:500])
                retry_after = response.headers.get("Retry-After")
                if retry_after and retry_after.isdigit():
                    delay = max(delay, float(retry_after))
                log.warning(
                    "ingest %s: HTTP %d (attempt %d), retrying in %.0fs", path, response.status_code, attempt, delay
                )
            self._sleep(delay)
            delay = min(delay * 2, 30.0)
        raise IngestError(0, "unreachable")

    def close(self) -> None:
        self._client.close()


class IngestError(RuntimeError):
    def __init__(self, status: int, body: str) -> None:
        super().__init__(f"ingest failed with HTTP {status}: {body}")
        self.status = status
