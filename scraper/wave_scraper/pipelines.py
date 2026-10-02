"""Normalise → de-duplicate → publish. Invalid records are counted per reason in the crawl stats
(`wave/rejected/<reason>`) instead of reaching the API."""

from __future__ import annotations

import json
import logging
import os
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from scrapy import Spider
from scrapy.crawler import Crawler
from scrapy.exceptions import DropItem
from scrapy.statscollectors import StatsCollector
from twisted.internet.defer import Deferred
from twisted.internet.threads import deferToThread

from .catalog import catalog
from .extract.records import Rejected, build_sailing
from .items import DiscoveredUrl, RatesItem, RawItem, SailingItem
from .models import Batch, Sailing
from .publisher import IngestClient, IngestConfig

log = logging.getLogger(__name__)


class _StatsMixin:
    stats: StatsCollector

    @classmethod
    def from_crawler(cls, crawler: Crawler) -> Any:
        pipeline = cls()
        assert crawler.stats is not None
        pipeline.stats = crawler.stats
        return pipeline


class NormalizePipeline(_StatsMixin):
    def process_item(self, item: Any, spider: Spider) -> Any:
        if not isinstance(item, RawItem):
            return item
        try:
            sailing = build_sailing(item.record, item.context, catalog())
        except Rejected as e:
            self.stats.inc_value(f"wave/rejected/{e}")
            raise DropItem(f"rejected: {e}") from e
        self.stats.inc_value(f"wave/extracted/{item.record.extractor}")
        return SailingItem(sailing=sailing, extractor=item.record.extractor)


class DedupPipeline(_StatsMixin):
    """The same crossing can appear on several pages (timetable, fares, promotion): merge them."""

    def __init__(self) -> None:
        self.seen: dict[tuple[str, str, str, str], Sailing] = {}

    def process_item(self, item: Any, spider: Spider) -> Any:
        if not isinstance(item, SailingItem):
            return item
        key = item.sailing.key
        previous = self.seen.get(key)
        merged = previous.merged_with(item.sailing) if previous else item.sailing
        self.seen[key] = merged
        if previous:
            self.stats.inc_value("wave/merged")
        return SailingItem(sailing=merged, extractor=item.extractor)


class DiscoveryReportPipeline:
    """Writes discovered pages to discovery-<date>.jsonl for curating sources.yaml."""

    def __init__(self) -> None:
        self.path: Path | None = None

    def open_spider(self, spider: Spider) -> None:
        directory = Path(os.environ.get("WAVE_REPORT_DIR", "reports"))
        self.path = directory / f"discovery-{datetime.now(UTC):%Y%m%d}.jsonl"

    def process_item(self, item: Any, spider: Spider) -> Any:
        if not isinstance(item, DiscoveredUrl):
            return item
        assert self.path is not None
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as out:
            out.write(json.dumps(item.__dict__, ensure_ascii=False) + "\n")
        return item


class IngestPipeline(_StatsMixin):
    """Publishes sailings in batches while crawling (so a long crawl delivers fresh data early)
    and the remainder when the spider closes. HTTP runs in a thread, never on the reactor."""

    FLUSH_EVERY = 200

    def __init__(self) -> None:
        self.pending: dict[tuple[str, str, str, str], Sailing] = {}
        self.client: IngestClient | None = None
        self.source = "wave-scraper"

    def open_spider(self, spider: Spider) -> None:
        self.client = IngestClient(IngestConfig.from_env())
        self.source = f"scrapy:{spider.name}"

    def process_item(self, item: Any, spider: Spider) -> Any:
        if isinstance(item, SailingItem):
            self.pending[item.sailing.key] = item.sailing
            if len(self.pending) >= self.FLUSH_EVERY:
                return self._flush().addCallback(lambda _: item)
        elif isinstance(item, RatesItem):
            assert self.client is not None
            client = self.client
            return deferToThread(client.send_rates, item.rates).addCallback(
                lambda answer: self._rates_sent(answer, item)
            )
        return item

    def _rates_sent(self, answer: dict[str, Any], item: RatesItem) -> RatesItem:
        self.stats.set_value("wave/rates/published", json.dumps(item.rates.rates))
        log.info("Rates published: %s", answer)
        return item

    def _flush(self) -> Deferred[Any]:
        assert self.client is not None
        sailings, self.pending = list(self.pending.values()), {}
        batch = Batch(source=self.source, fetchedAt=datetime.now(UTC), sailings=sailings)
        deferred: Deferred[Any] = deferToThread(self.client.send_sailings, batch)
        deferred.addCallback(self._sent)
        deferred.addErrback(self._failed, len(sailings))
        return deferred

    def _sent(self, answer: dict[str, Any]) -> None:
        self.stats.inc_value("wave/published/accepted", int(answer.get("accepted", 0)))
        self.stats.inc_value("wave/published/rejected", len(answer.get("rejected", [])))
        for reason in answer.get("rejected", [])[:10]:
            log.warning("Backend rejected %s", reason)

    def _failed(self, failure: Any, count: int) -> None:
        self.stats.inc_value("wave/published/failed", count)
        log.error("Publishing %d sailings failed: %s", count, failure.getErrorMessage())

    def close_spider(self, spider: Spider) -> Deferred[Any] | None:
        if not self.pending:
            if self.client:
                self.client.close()
            return None
        client = self.client

        def _close(_: Any) -> None:
            if client:
                client.close()

        return self._flush().addBoth(_close)
