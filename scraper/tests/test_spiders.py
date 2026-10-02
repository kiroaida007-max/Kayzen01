from __future__ import annotations

import json
from decimal import Decimal
from typing import Any

import pytest
from conftest import TODAY, fixture_json, fixture_text
from scrapy import Request
from scrapy.exceptions import DropItem
from scrapy.http import HtmlResponse, TextResponse
from scrapy.utils.test import get_crawler
from twisted.internet import defer

from wave_scraper import pipelines
from wave_scraper.extract import RawRecord
from wave_scraper.items import RatesItem, RawItem, SailingItem
from wave_scraper.models import Price
from wave_scraper.pipelines import DedupPipeline, IngestPipeline, NormalizePipeline
from wave_scraper.spiders.operators import OPERATOR_SPIDERS, BaleariaSpider, GnvSpider
from wave_scraper.spiders.rates import RatesSpider, rates_from_html

BAL_URL = "https://www.balearia.com/fr/routes-horaires/bateau-valence-mostaganem"


def make_spider(cls: type, **kwargs: Any) -> Any:
    crawler = get_crawler(cls)
    spider = cls.from_crawler(crawler, **kwargs)
    spider.today = TODAY
    return spider


async def collect(gen: Any) -> list[Any]:
    return [item async for item in gen]


def html(url: str, body: str) -> HtmlResponse:
    return HtmlResponse(url=url, body=body.encode("utf-8"), encoding="utf-8", request=Request(url))


def test_every_operator_has_a_spider_and_sources() -> None:
    assert set(OPERATOR_SPIDERS) == {"AF", "CL", "BAL", "GNV", "NE", "ATM"}
    for cls in OPERATOR_SPIDERS.values():
        spider = make_spider(cls)
        assert spider.source.start_urls, cls.name
        assert spider.allowed_domains, cls.name


def test_requests_render_with_playwright_and_capture_xhr() -> None:
    spider = make_spider(BaleariaSpider)
    request = spider.page_request(BAL_URL)
    assert request.meta["playwright"] is True
    assert request.meta["playwright_include_page"] is True
    assert request.meta["playwright_page_event_handlers"] == {"response": "capture_xhr"}
    assert request.callback == spider.parse_page and request.errback == spider.page_failed


async def test_page_to_items_through_the_pipelines() -> None:
    spider = make_spider(BaleariaSpider)
    items = await collect(spider.parse_page(html(BAL_URL, fixture_text("balearia_next.html"))))
    assert len(items) == 2 and all(isinstance(i, RawItem) for i in items)
    assert items[0].context.origin == "ESVLC" and items[0].context.destination == "DZMOS"

    normalize = NormalizePipeline.from_crawler(spider.crawler)
    sailings = [normalize.process_item(i, spider) for i in items]
    assert all(isinstance(s, SailingItem) for s in sailings)
    assert spider.crawler.stats.get_value("wave/extracted/embedded") == 2


async def test_captured_xhr_payloads_are_extracted() -> None:
    spider = make_spider(GnvSpider)
    url = "https://www.gnv.it/fr/traghetti/sete-bejaia"
    records = list(
        spider.extract(
            html(url, "<html><body>loading…</body></html>"),
            [("https://www.gnv.it/api/x", fixture_json("gnv_xhr.json"))],
        )
    )
    assert [r.extractor for r in records] == ["xhr", "xhr"]


async def test_empty_pages_fall_back_to_firecrawl(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FIRECRAWL_API_KEY", "fc-test")
    spider = make_spider(BaleariaSpider)
    (request,) = await collect(spider.parse_page(html(BAL_URL, "<html><body><div id=app></div></body></html>")))
    assert isinstance(request, Request)
    assert request.url.endswith("/scrape") and request.method == "POST"
    assert request.headers["Authorization"] == b"Bearer fc-test"
    assert request.meta["allow_offsite"] is True
    body = json.loads(request.body)
    assert body["url"] == BAL_URL and body["location"]["country"] == "DZ"

    answer = TextResponse(url=request.url, body=json.dumps(fixture_json("firecrawl_v2.json")).encode(), request=request)
    # The Nouris fixture names other ports than the page: records keep their own route.
    assert len(list(spider.parse_firecrawl(answer))) == 2


def test_rejections_are_counted_not_published() -> None:
    spider = make_spider(BaleariaSpider)
    normalize = NormalizePipeline.from_crawler(spider.crawler)
    item = RawItem(
        record=RawRecord(origin="Valence", destination="Tunis", departure="2026-10-14 20:00", duration="14h"),
        context=spider.context_for(BAL_URL),
    )
    with pytest.raises(DropItem):
        normalize.process_item(item, spider)
    assert spider.crawler.stats.get_value("wave/rejected/unknown_port") == 1


async def test_duplicates_merge_their_prices() -> None:
    spider = make_spider(BaleariaSpider)
    normalize = NormalizePipeline.from_crawler(spider.crawler)
    dedup = DedupPipeline.from_crawler(spider.crawler)
    raw = (await collect(spider.parse_page(html(BAL_URL, fixture_text("balearia_next.html")))))[0]
    first = normalize.process_item(raw, spider)
    cabin_only = first.sailing.model_copy(update={"prices": [Price(category="SUITE", amount=480.0, currency="EUR")]})
    dedup.process_item(first, spider)
    merged = dedup.process_item(SailingItem(sailing=cabin_only, extractor="json-ld"), spider)
    assert {p.category for p in merged.sailing.prices} == {"ADULT_SEAT", "CABIN_INT_2", "VEHICLE_CAR", "SUITE"}
    assert spider.crawler.stats.get_value("wave/merged") == 1


def test_ingest_pipeline_publishes_batches(monkeypatch: pytest.MonkeyPatch, tmp_path: Any) -> None:
    out = tmp_path / "dry.ndjson"
    monkeypatch.setenv("WAVE_DRY_RUN", str(out))
    monkeypatch.setattr(pipelines, "deferToThread", lambda f, *a: defer.succeed(f(*a)))
    spider = make_spider(BaleariaSpider)
    normalize = NormalizePipeline.from_crawler(spider.crawler)
    ingest = IngestPipeline.from_crawler(spider.crawler)
    ingest.open_spider(spider)
    import asyncio

    raws = asyncio.run(collect(spider.parse_page(html(BAL_URL, fixture_text("balearia_next.html")))))
    for raw in raws:
        ingest.process_item(normalize.process_item(raw, spider), spider)
    ingest.close_spider(spider)
    (line,) = out.read_text("utf-8").splitlines()
    sent = json.loads(line)
    assert sent["path"] == "/internal/v1/ingest/sailings"
    assert sent["payload"]["source"] == "scrapy:balearia"
    assert [s["departureLocal"] for s in sent["payload"]["sailings"]] == ["2026-10-14T23:00", "2026-10-21T23:00"]
    assert spider.crawler.stats.get_value("wave/published/accepted") == 2


def test_rates_spider() -> None:
    assert rates_from_html(fixture_text("bank_of_algeria.html")) == {
        "USD": Decimal("132.7300"),
        "EUR": Decimal("151.0600"),
    }
    spider = make_spider(RatesSpider)
    (item,) = list(spider.parse(html("https://www.bank-of-algeria.dz/cours", fixture_text("bank_of_algeria.html"))))
    assert isinstance(item, RatesItem)
    assert item.rates.rates == {"EUR": "151.0600", "USD": "132.7300"}
    # Nothing on the page: follow the "cours de change" links instead.
    links = list(
        make_spider(RatesSpider).parse(
            html("https://www.bank-of-algeria.dz/", '<a href="/marche-interbancaire-des-changes">Cours</a>')
        )
    )
    assert [r.url for r in links] == ["https://www.bank-of-algeria.dz/marche-interbancaire-des-changes"]
