"""Crawls an operator's site within its domains to find timetable and fare pages.

Every page is also run through the extractors, so a newly found timetable feeds the backend
immediately; the pages that yield crossings are reported for addition to sources.yaml."""

from __future__ import annotations

import re
from collections.abc import AsyncIterator, Iterator
from typing import Any

import scrapy
from scrapy.http import Response, TextResponse
from scrapy.linkextractors import LinkExtractor

from ..items import DiscoveredUrl, RawItem
from .base import OperatorSpider


class DiscoverySpider(OperatorSpider):
    name = "discovery"
    custom_settings = {  # noqa: RUF012 - Scrapy reads this class attribute
        "DEPTH_LIMIT": 3,
        "CLOSESPIDER_PAGECOUNT": 300,
    }

    def __init__(self, *args: Any, operator: str = "BAL", **kwargs: Any) -> None:
        self.operator = operator
        super().__init__(*args, **kwargs)
        self.firecrawl_key = None  # discovery only maps sites; extraction fallbacks run in the operator spiders
        self.links = LinkExtractor(
            allow=[re.compile(p) for p in self.source.discovery] or (),
            allow_domains=list(self.source.allowed_domains),
            deny_extensions=["pdf", "jpg", "jpeg", "png", "gif", "svg", "webp", "mp4", "zip"],
            unique=True,
        )

    async def start(self) -> AsyncIterator[Any]:
        for url in self.source.start_urls:
            yield self.page_request(url)

    async def parse_page(self, response: Response) -> AsyncIterator[Any]:
        found = 0
        async for item in super().parse_page(response):
            if isinstance(item, RawItem):
                found += 1
            yield item
        if isinstance(response, TextResponse) and found:
            title = (response.css("title::text").get() or "").strip()
            yield DiscoveredUrl(operator=self.operator, url=response.url, title=title, sailings_found=found)

    def follow_links(self, response: TextResponse) -> Iterator[scrapy.Request]:
        for link in self.links.extract_links(response):
            yield self.page_request(link.url)
