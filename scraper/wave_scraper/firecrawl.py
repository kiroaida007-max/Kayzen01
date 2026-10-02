"""Firecrawl adapter: rendered-page extraction with a JSON schema (fallback for pages whose markup
the structured extractors cannot read) and site maps for source discovery.

Works with the v2 API (default) and v1. Requests can be sent by Scrapy (`scrape_request`) so they
share its throttling and retries, or directly with httpx (`FirecrawlClient`) from the CLI."""

from __future__ import annotations

import json
import os
from typing import Any

import httpx
from scrapy.http import JsonRequest

from .extract.records import RawPrice, RawRecord

API_URL = os.environ.get("FIRECRAWL_API_URL", "https://api.firecrawl.dev")
API_VERSION = os.environ.get("FIRECRAWL_API_VERSION", "v2")

SAILINGS_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "sailings": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "operator": {"type": "string", "description": "Ferry company"},
                    "from": {"type": "string", "description": "Departure port"},
                    "to": {"type": "string", "description": "Arrival port"},
                    "departure": {
                        "type": "string",
                        "description": "Departure date and time exactly as printed (local time)",
                    },
                    "arrival": {
                        "type": "string",
                        "description": "Arrival date and time exactly as printed (local time)",
                    },
                    "duration": {"type": "string"},
                    "vessel": {"type": "string", "description": "Ship name"},
                    "status": {"type": "string", "description": "e.g. scheduled, delayed, cancelled, sold out"},
                    "prices": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "label": {"type": "string", "description": "Fare, seat, cabin or vehicle type"},
                                "amount": {"type": "number"},
                                "currency": {"type": "string", "description": "EUR, DZD or USD"},
                            },
                            "required": ["amount"],
                        },
                    },
                },
                "required": ["from", "to", "departure"],
            },
        }
    },
    "required": ["sailings"],
}

PROMPT = (
    "List every ferry crossing shown on this page: departure and arrival ports, departure and arrival "
    "date and time exactly as printed (local times), ship name, status, and each price with its fare, "
    "seat, cabin or vehicle label and currency. Only copy values present on the page; never estimate."
)


def scrape_body(
    url: str, wait_ms: int = 3000, country: str = "DZ", languages: tuple[str, ...] = ("fr",)
) -> dict[str, Any]:
    location = {"country": country, "languages": list(languages)}
    if API_VERSION == "v1":
        return {
            "url": url,
            "formats": ["json"],
            "jsonOptions": {"schema": SAILINGS_SCHEMA, "prompt": PROMPT},
            "onlyMainContent": True,
            "waitFor": wait_ms,
            "location": location,
        }
    return {
        "url": url,
        "formats": [{"type": "json", "schema": SAILINGS_SCHEMA, "prompt": PROMPT}],
        "onlyMainContent": True,
        "waitFor": wait_ms,
        "location": location,
    }


def records_from_response(payload: dict[str, Any], source_url: str) -> list[RawRecord]:
    """Accepts v1 (`data.json` / `data.extract`) and v2 (`data.json`) response shapes."""
    if not payload.get("success", True):
        return []
    data = payload.get("data") or {}
    extracted = data.get("json") or data.get("extract") or {}
    if isinstance(extracted, str):
        try:
            extracted = json.loads(extracted)
        except json.JSONDecodeError:
            return []
    sailings = extracted.get("sailings", []) if isinstance(extracted, dict) else []
    records: list[RawRecord] = []
    for s in sailings:
        if not isinstance(s, dict):
            continue
        records.append(
            RawRecord(
                operator=s.get("operator"),
                origin=s.get("from"),
                destination=s.get("to"),
                departure=s.get("departure"),
                arrival=s.get("arrival"),
                duration=s.get("duration"),
                vessel=s.get("vessel"),
                status=s.get("status"),
                availability=s.get("status"),
                prices=[
                    RawPrice(label=p.get("label"), amount=p.get("amount"), currency=p.get("currency"))
                    for p in s.get("prices") or []
                    if isinstance(p, dict)
                ],
                source_url=source_url,
                extractor="firecrawl",
            )
        )
    return records


def links_from_map_response(payload: dict[str, Any]) -> list[str]:
    links = payload.get("links") or []
    return [link if isinstance(link, str) else str(link.get("url")) for link in links if link]


def scrape_request(url: str, api_key: str, callback: Any, meta: dict[str, Any] | None = None) -> JsonRequest:
    return JsonRequest(
        f"{API_URL}/{API_VERSION}/scrape",
        data=scrape_body(url),
        headers={"Authorization": f"Bearer {api_key}"},
        callback=callback,
        meta={**(meta or {}), "firecrawl_url": url, "dont_obey_robotstxt": True, "download_timeout": 120},
        dont_filter=True,
    )


class FirecrawlClient:
    """Direct client for one-off extraction (`wave-scraper firecrawl`) and discovery."""

    def __init__(self, api_key: str, timeout: float = 120.0, transport: httpx.BaseTransport | None = None) -> None:
        self._client = httpx.Client(
            base_url=f"{API_URL}/{API_VERSION}",
            headers={"Authorization": f"Bearer {api_key}"},
            timeout=timeout,
            transport=transport,
        )

    def scrape(self, url: str) -> list[RawRecord]:
        response = self._client.post("/scrape", json=scrape_body(url))
        response.raise_for_status()
        return records_from_response(response.json(), url)

    def map_site(self, url: str, search: str | None = None, limit: int = 200) -> list[str]:
        body: dict[str, Any] = {"url": url, "limit": limit}
        if search:
            body["search"] = search
        response = self._client.post("/map", json=body)
        response.raise_for_status()
        return links_from_map_response(response.json())

    def close(self) -> None:
        self._client.close()
