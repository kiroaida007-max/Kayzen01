"""Items flowing through the Scrapy pipelines."""

from __future__ import annotations

from dataclasses import dataclass

from .extract.records import Context, RawRecord
from .models import Rates, Sailing


@dataclass
class RawItem:
    """A crossing as extracted, with what the spider knows about the page."""

    record: RawRecord
    context: Context


@dataclass
class SailingItem:
    sailing: Sailing
    extractor: str


@dataclass
class RatesItem:
    rates: Rates


@dataclass
class DiscoveredUrl:
    """A page worth adding to sources.yaml, found by the discovery crawler."""

    operator: str
    url: str
    title: str
    sailings_found: int
