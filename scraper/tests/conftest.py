from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from typing import Any

import pytest

from wave_scraper.catalog import Catalog, catalog
from wave_scraper.extract import Context

FIXTURES = Path(__file__).parent / "fixtures"
TODAY = date(2026, 10, 2)


def fixture_text(name: str) -> str:
    return (FIXTURES / name).read_text("utf-8")


def fixture_json(name: str) -> Any:
    return json.loads(fixture_text(name))


@pytest.fixture
def cat() -> Catalog:
    return catalog()


def context(operator: str, url: str, currency: str | None = "EUR") -> Context:
    ports = catalog().ports_in(url)
    return Context(
        operator=operator,
        origin=ports[0] if len(ports) >= 2 else None,
        destination=ports[1] if len(ports) >= 2 else None,
        currency=currency,
        today=TODAY,
        source_url=url,
    )
