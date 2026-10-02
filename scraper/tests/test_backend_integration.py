"""Publishes fixture data to a running backend through the signed ingestion API.

WAVE_INGEST_URL=http://localhost:8080 WAVE_INGEST_SECRET=... pytest tests/test_backend_integration.py
"""

from __future__ import annotations

import contextlib
import os
from datetime import UTC, date, datetime

import httpx
import pytest
from conftest import context, fixture_json, fixture_text

from wave_scraper import firecrawl
from wave_scraper.catalog import catalog
from wave_scraper.extract import Rejected, build_sailing, extract_page, from_json_payload
from wave_scraper.models import Batch, Rates
from wave_scraper.publisher import IngestClient, IngestConfig

pytestmark = pytest.mark.skipif(not os.environ.get("WAVE_INGEST_SECRET"), reason="needs a running backend")

BAL_URL = "https://www.balearia.com/fr/routes-horaires/bateau-valence-mostaganem"


def fixture_sailings() -> list:
    cases = [
        ("BAL", extract_page(fixture_text("balearia_next.html"), BAL_URL), BAL_URL, "EUR"),
        ("CL", extract_page(fixture_text("cl_jsonld.html"), "https://x/cl"), "https://x/cl", "EUR"),
        ("GNV", from_json_payload(fixture_json("gnv_xhr.json")), "https://x/gnv", "EUR"),
        ("AF", extract_page(fixture_text("af_timetable.html"), "https://x/af"), "https://x/af", "DZD"),
        (
            "NE",
            firecrawl.records_from_response(fixture_json("firecrawl_v2.json"), "https://x/ne"),
            "https://x/ne",
            "DZD",
        ),
    ]
    sailings = []
    for operator, records, url, currency in cases:
        ctx = context(operator, url, currency)
        ctx.today = date.today()
        for record in records:
            with contextlib.suppress(Rejected):
                sailings.append(build_sailing(record, ctx, catalog()))
    return sailings


def test_backend_accepts_scraped_sailings_and_serves_them_live() -> None:
    config = IngestConfig.from_env()
    client = IngestClient(config)
    sailings = fixture_sailings()
    answer = client.send_sailings(Batch(source="integration-test", fetchedAt=datetime.now(UTC), sailings=sailings))
    assert answer["accepted"] == len(sailings), answer["rejected"]

    # The Baleària crossing now carries the scraped price as a LIVE offer in search.
    api = os.environ.get("WAVE_API_URL", config.base_url)
    search = httpx.post(
        f"{api}/api/v1/search",
        json={
            "tripType": "ONE_WAY",
            "from": "ESVLC",
            "to": "DZMOS",
            "departureDate": "2026-10-14",
            "passengers": {"adults": 1},
            "accommodation": "SEAT",
            "currency": "EUR",
        },
        timeout=20,
    ).json()
    offers = {o["sailingId"]: o for o in search["outbound"]["offers"]}
    live = [o for o in offers.values() if o["priceSource"] == "LIVE" and o["departure"].startswith("2026-10-14T23:00")]
    assert live, offers.keys()


def test_backend_accepts_official_rates() -> None:
    client = IngestClient(IngestConfig.from_env())
    info = client.send_rates(
        Rates(
            rates={"EUR": "151.06", "USD": "132.73"}, asOf=datetime.now(UTC), source="Banque d'Algérie — cours officiel"
        )
    )
    assert info["dzdPerUnit"]["EUR"].startswith("151.06")


def test_bad_signatures_are_refused() -> None:
    config = IngestConfig.from_env()
    forged = IngestClient(IngestConfig(config.base_url, config.key_id, "not-the-secret"), max_attempts=1)
    with pytest.raises(Exception, match="401"):
        forged.send_rates(Rates(rates={"EUR": "151.06"}, asOf=datetime.now(UTC), source="forged"))
