"""Scrapy settings: polite by default (robots.txt, auto-throttle, identified bot)."""

from __future__ import annotations

import os

BOT_NAME = "wave_scraper"
SPIDER_MODULES = ["wave_scraper.spiders"]
NEWSPIDER_MODULE = "wave_scraper.spiders"

USER_AGENT = os.environ.get("WAVE_USER_AGENT", "WAVE-FerryBot/1.0 (+https://wave.dz/bot)")
ROBOTSTXT_OBEY = True

CONCURRENT_REQUESTS = 8
CONCURRENT_REQUESTS_PER_DOMAIN = 2
DOWNLOAD_DELAY = 2.0
RANDOMIZE_DOWNLOAD_DELAY = True
AUTOTHROTTLE_ENABLED = True
AUTOTHROTTLE_START_DELAY = 2.0
AUTOTHROTTLE_MAX_DELAY = 30.0
AUTOTHROTTLE_TARGET_CONCURRENCY = 1.0
DOWNLOAD_TIMEOUT = 45
RETRY_ENABLED = True
RETRY_TIMES = 3
RETRY_HTTP_CODES = [408, 429, 500, 502, 503, 504, 522, 524]
DEPTH_LIMIT = 3
CLOSESPIDER_TIMEOUT = int(os.environ.get("WAVE_SPIDER_TIMEOUT", "1800"))
REQUEST_FINGERPRINTER_IMPLEMENTATION = "2.7"
FEED_EXPORT_ENCODING = "utf-8"
LOG_LEVEL = os.environ.get("LOG_LEVEL", "INFO")

# Local development: cache pages to iterate on extractors without hitting the sites again.
HTTPCACHE_ENABLED = os.environ.get("WAVE_HTTP_CACHE", "") == "1"
HTTPCACHE_EXPIRATION_SECS = 3600
HTTPCACHE_DIR = ".scrapy/httpcache"

# Pages flagged with meta["playwright"] are rendered in Chromium; everything else uses plain HTTP.
DOWNLOAD_HANDLERS = {
    "http": "scrapy_playwright.handler.ScrapyPlaywrightDownloadHandler",
    "https": "scrapy_playwright.handler.ScrapyPlaywrightDownloadHandler",
}
TWISTED_REACTOR = "twisted.internet.asyncioreactor.AsyncioSelectorReactor"
PLAYWRIGHT_BROWSER_TYPE = "chromium"
PLAYWRIGHT_LAUNCH_OPTIONS = {
    "headless": True,
    **(
        {"executable_path": os.environ["PLAYWRIGHT_CHROMIUM_EXECUTABLE"]}
        if os.environ.get("PLAYWRIGHT_CHROMIUM_EXECUTABLE")
        else {}
    ),
}
PLAYWRIGHT_MAX_PAGES_PER_CONTEXT = 4
PLAYWRIGHT_DEFAULT_NAVIGATION_TIMEOUT = 45_000


def _abort_heavy(request: object) -> bool:
    """Timetables do not need images, fonts or video: skipping them cuts bandwidth by ~80 %."""
    return getattr(request, "resource_type", "") in {"image", "media", "font"}


PLAYWRIGHT_ABORT_REQUEST = _abort_heavy

ITEM_PIPELINES = {
    "wave_scraper.pipelines.NormalizePipeline": 100,
    "wave_scraper.pipelines.DedupPipeline": 200,
    "wave_scraper.pipelines.DiscoveryReportPipeline": 300,
    "wave_scraper.pipelines.IngestPipeline": 900,
}
