"""Official exchange rates from the Banque d'Algérie (dinars per euro and per US dollar)."""

from __future__ import annotations

import re
from collections.abc import AsyncIterator, Iterator
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any

import scrapy
from parsel import Selector
from scrapy.http import Response, TextResponse
from scrapy.linkextractors import LinkExtractor

from ..catalog import fold
from ..items import RatesItem
from ..models import Rates
from ..normalize import parse_amount
from ..sources import load_sources

_CURRENCY_ROW = {
    "EUR": re.compile(r"\b(eur|euro|euros)\b"),
    # Not a bare "dollar": Canadian or Australian dollar rows must not be taken for USD.
    "USD": re.compile(r"\b(usd|dollar us|dollar u s|dollar des etats unis|dollar americain|us dollar)\b"),
}


def rates_from_html(html: str) -> dict[str, Decimal]:
    """Finds EUR and USD rows in any table; uses the selling rate when buy/sell columns exist
    (the rate the bank applies to purchases abroad) and keeps the value only if it is plausible."""
    found: dict[str, Decimal] = {}
    for row in Selector(text=html).css("tr"):
        cells = [" ".join(c.css("*::text").getall()).strip() for c in row.css("td, th")]
        if len(cells) < 2:
            continue
        label = fold(" ".join(cells[:2]))
        for code, pattern in _CURRENCY_ROW.items():
            if code in found or not pattern.search(label):
                continue
            numbers = [
                n
                for n in (parse_amount(c.replace(",", ".")) if re.fullmatch(r"[\d\s.,]+", c) else None for c in cells)
                if n
            ]
            plausible = [n for n in numbers if Decimal(80) <= n <= Decimal(400)]
            if plausible:
                found[code] = plausible[-1].quantize(Decimal("0.0001"))
    return found


class RatesSpider(scrapy.Spider):
    name = "rates"
    custom_settings = {  # noqa: RUF012 - Scrapy reads this class attribute
        "DEPTH_LIMIT": 2,
        "CLOSESPIDER_ITEMCOUNT": 1,
    }

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        config = load_sources().rates
        self.start_urls = list(config.get("start_urls", []))
        self.allowed_domains = list(config.get("allowed_domains", []))
        self.links = LinkExtractor(allow=list(config.get("discovery") or []), allow_domains=self.allowed_domains)
        self.published = False

    async def start(self) -> AsyncIterator[Any]:
        for url in self.start_urls:
            yield scrapy.Request(url, callback=self.parse)

    def parse(self, response: Response, **kwargs: Any) -> Iterator[Any]:
        if not isinstance(response, TextResponse) or self.published:
            return
        values = rates_from_html(response.text)
        if {"EUR", "USD"} <= values.keys() and Decimal("1.0") <= values["EUR"] / values["USD"] <= Decimal("1.4"):
            self.published = True
            yield RatesItem(
                Rates(
                    rates={"EUR": str(values["EUR"]), "USD": str(values["USD"])},
                    asOf=datetime.now(UTC),
                    source="Banque d'Algérie — cours officiel",
                )
            )
            return
        for link in self.links.extract_links(response):
            yield scrapy.Request(link.url, callback=self.parse)
