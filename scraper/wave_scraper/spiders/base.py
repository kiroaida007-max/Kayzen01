"""Shared operator spider: render the page (Playwright), capture the JSON the site's own front-end
loads, run every extractor, and fall back to Firecrawl when a page yields nothing."""

from __future__ import annotations

import json
import logging
import os
import re
from collections import defaultdict
from collections.abc import AsyncIterator, Iterator
from datetime import date
from typing import Any

import scrapy
from scrapy.http import Response, TextResponse

from .. import firecrawl
from ..catalog import catalog
from ..extract import Context, RawRecord, extract_page, from_json_payload
from ..items import RawItem
from ..sources import Source, load_sources

log = logging.getLogger(__name__)

MAX_PAYLOAD_BYTES = 5_000_000


async def settle(page: Any, selector: str, timeout_ms: int = 20_000) -> None:
    """Waits for the content and for the timetable XHRs, without failing the request on slow sites."""
    try:
        await page.wait_for_selector(selector, timeout=timeout_ms)
    except Exception:
        log.debug("selector %s not found on %s", selector, page.url)
    try:
        await page.wait_for_load_state("networkidle", timeout=10_000)
    except Exception:
        log.debug("network never idle on %s", page.url)


class OperatorSpider(scrapy.Spider):
    operator: str = ""

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        self.source: Source = load_sources().operators[self.operator]
        self.allowed_domains = list(self.source.allowed_domains)
        self.firecrawl_key = os.environ.get("FIRECRAWL_API_KEY") if self.source.firecrawl else None
        self._xhr_patterns = [re.compile(p) for p in self.source.xhr_patterns]
        self._captured: dict[int, list[tuple[str, Any]]] = defaultdict(list)
        self.today = date.today()

    # ------------------------------------------------------------------ requests

    async def start(self) -> AsyncIterator[Any]:
        for url in self.source.start_urls:
            yield self.page_request(url)

    def page_request(self, url: str, **meta: Any) -> scrapy.Request:
        request_meta: dict[str, Any] = {"operator": self.operator, **meta}
        if self.source.playwright:
            request_meta.update(
                playwright=True,
                playwright_include_page=True,
                playwright_page_methods=[scrapy_playwright_settle(self.source.wait_for)],
                playwright_page_event_handlers={"response": "capture_xhr"},
            )
        return scrapy.Request(url, callback=self.parse_page, errback=self.page_failed, meta=request_meta)

    async def capture_xhr(self, response: Any) -> None:
        """Playwright 'response' event: keep JSON answers of timetable/price API calls."""
        try:
            if response.request.resource_type not in ("xhr", "fetch"):
                return
            if not any(p.search(response.url) for p in self._xhr_patterns):
                return
            if "json" not in (response.headers.get("content-type") or ""):
                return
            body = await response.body()
            if len(body) > MAX_PAYLOAD_BYTES:
                return
            self._captured[id(response.frame.page)].append((response.url, json.loads(body)))
        except Exception as e:
            log.debug("XHR capture failed for %s: %s", getattr(response, "url", "?"), e)

    # ------------------------------------------------------------------ parsing

    def context_for(self, url: str) -> Context:
        """Route hinted by the URL ("bateau-valence-mostaganem" → Valencia → Mostaganem)."""
        ports = catalog().ports_in(url)
        origin, destination = (ports[0], ports[1]) if len(ports) >= 2 else (None, None)
        if origin and destination and catalog().route(self.operator, origin, destination) is None:
            origin, destination = None, None
        return Context(
            operator=self.operator,
            origin=origin,
            destination=destination,
            currency=self.source.currency,
            today=self.today,
            source_url=url,
        )

    async def parse_page(self, response: Response) -> AsyncIterator[Any]:
        page = response.meta.get("playwright_page")
        captured = self._captured.pop(id(page), []) if page is not None else []
        if page is not None:
            await page.close()
        if not isinstance(response, TextResponse):
            return
        context = self.context_for(response.url)
        records = list(self.extract(response, captured))
        self.crawler.stats.inc_value(f"wave/pages/{self.operator}")
        if records:
            for record in records:
                yield RawItem(record=record, context=context)
        elif self.firecrawl_key:
            self.crawler.stats.inc_value("wave/firecrawl/requests")
            yield firecrawl.scrape_request(
                response.url,
                self.firecrawl_key,
                callback=self.parse_firecrawl,
                meta={"allow_offsite": True, "page_url": response.url},
            )
        for follow in self.follow_links(response):
            yield follow

    def extract(self, response: TextResponse, captured: list[tuple[str, Any]]) -> Iterator[RawRecord]:
        yield from extract_page(response.text, response.url)
        for _url, payload in captured:
            yield from from_json_payload(payload, response.url)

    def follow_links(self, response: TextResponse) -> Iterator[scrapy.Request]:
        """Operator spiders stay on their start pages; DiscoverySpider explores."""
        return iter(())

    def parse_firecrawl(self, response: Response) -> Iterator[RawItem]:
        url = response.meta["page_url"]
        try:
            payload = json.loads(response.text)
        except (json.JSONDecodeError, AttributeError):
            self.crawler.stats.inc_value("wave/firecrawl/errors")
            return
        context = self.context_for(url)
        for record in firecrawl.records_from_response(payload, url):
            yield RawItem(record=record, context=context)

    async def page_failed(self, failure: Any) -> None:
        page = failure.request.meta.get("playwright_page")
        if page is not None:
            self._captured.pop(id(page), None)
            await page.close()
        self.crawler.stats.inc_value(f"wave/page_errors/{self.operator}")
        log.warning("Page failed %s: %s", failure.request.url, failure.getErrorMessage())


def scrapy_playwright_settle(selector: str) -> Any:
    from scrapy_playwright.page import PageMethod

    return PageMethod(settle, selector)
