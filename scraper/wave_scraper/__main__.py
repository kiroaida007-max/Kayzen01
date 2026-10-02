"""wave-scraper command line.

    wave-scraper crawl [--operators AF,BAL]      operator timetables and fares → backend
    wave-scraper discover --operator BAL          crawl a site for new timetable pages
    wave-scraper rates                            Banque d'Algérie rates → backend
    wave-scraper firecrawl URL --operator BAL     one page through Firecrawl → backend
    wave-scraper ais --udp 0.0.0.0:10110          AIS receiver → backend live map
    wave-scraper check [--live]                   validate sources (and count what each yields)

Publishing needs WAVE_INGEST_URL, WAVE_INGEST_KEY_ID and WAVE_INGEST_SECRET; --dry-run FILE (or
WAVE_DRY_RUN) writes the payloads as NDJSON instead."""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import tempfile
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from urllib.parse import urlparse

from .catalog import catalog
from .extract import Context, Rejected, build_sailing
from .models import Batch
from .publisher import IngestClient, IngestConfig
from .sources import load_sources


def _crawl(spiders: list[tuple[type, dict[str, str]]]) -> None:
    from scrapy.crawler import CrawlerProcess
    from scrapy.utils.project import get_project_settings

    os.environ.setdefault("SCRAPY_SETTINGS_MODULE", "wave_scraper.settings")
    process = CrawlerProcess(get_project_settings())
    for spider, kwargs in spiders:
        process.crawl(spider, **kwargs)
    process.start()


def cmd_crawl(args: argparse.Namespace) -> int:
    from .spiders.operators import OPERATOR_SPIDERS

    codes = args.operators.split(",") if args.operators else list(OPERATOR_SPIDERS)
    unknown = [c for c in codes if c not in OPERATOR_SPIDERS]
    if unknown:
        print(f"unknown operators: {', '.join(unknown)}", file=sys.stderr)
        return 2
    _crawl([(OPERATOR_SPIDERS[c], {}) for c in codes])
    return 0


def cmd_discover(args: argparse.Namespace) -> int:
    from .spiders.discovery import DiscoverySpider

    _crawl([(DiscoverySpider, {"operator": args.operator})])
    return 0


def cmd_rates(args: argparse.Namespace) -> int:
    from .spiders.rates import RatesSpider

    _crawl([(RatesSpider, {})])
    return 0


def cmd_firecrawl(args: argparse.Namespace) -> int:
    from .firecrawl import FirecrawlClient

    key = os.environ.get("FIRECRAWL_API_KEY")
    if not key:
        print("FIRECRAWL_API_KEY is not set", file=sys.stderr)
        return 2
    client = FirecrawlClient(key)
    try:
        records = client.scrape(args.url)
    finally:
        client.close()
    ports = catalog().ports_in(args.url)
    context = Context(
        operator=args.operator,
        origin=ports[0] if len(ports) >= 2 else None,
        destination=ports[1] if len(ports) >= 2 else None,
        currency=load_sources().operators[args.operator].currency,
        source_url=args.url,
    )
    sailings, reasons = [], Counter[str]()
    for record in records:
        try:
            sailings.append(build_sailing(record, context, catalog()))
        except Rejected as e:
            reasons[str(e)] += 1
    print(f"{len(records)} extracted, {len(sailings)} valid, rejected: {dict(reasons)}")
    if sailings:
        publisher = IngestClient(IngestConfig.from_env())
        try:
            print(publisher.send_sailings(Batch(source="firecrawl", fetchedAt=datetime.now(UTC), sailings=sailings)))
        finally:
            publisher.close()
    return 0


def cmd_ais(args: argparse.Namespace) -> int:
    from .ais import run

    mmsis = None if args.all_ships else catalog().tracked_mmsis
    run(IngestClient(IngestConfig.from_env()), mmsis, udp=args.udp, tcp=args.tcp)
    return 0


def cmd_check(args: argparse.Namespace) -> int:
    sources = load_sources()
    problems: list[str] = []
    for code, source in sources.operators.items():
        operator = catalog().operators.get(code)
        if operator is None:
            problems.append(f"{code}: not in the backend catalog")
        elif not operator.get("active", True):
            problems.append(f"{code}: operator inactive in the catalog")
        if not catalog().routes_of(code):
            problems.append(f"{code}: no routes in the catalog")
        for url in source.start_urls:
            host = urlparse(url).hostname or ""
            if not any(host == d or host.endswith("." + d) for d in source.allowed_domains):
                problems.append(f"{code}: {url} is outside allowed_domains")
    for problem in problems:
        print(f"PROBLEM {problem}")
    urls = sum(len(s.start_urls) for s in sources.operators.values())
    print(f"{len(sources.operators)} operators, {urls} start URLs, {len(problems)} problems")
    if not args.live:
        return 1 if problems else 0
    # Live: crawl everything without publishing and count valid crossings per company.
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "dry-run.ndjson"
        os.environ["WAVE_DRY_RUN"] = str(out)
        from .spiders.operators import OPERATOR_SPIDERS

        _crawl([(cls, {}) for cls in OPERATOR_SPIDERS.values()])
        counts: Counter[str] = Counter()
        if out.exists():
            for line in out.read_text("utf-8").splitlines():
                for sailing in json.loads(line)["payload"].get("sailings", []):
                    counts[sailing["operator"]] += 1
    for code in sources.operators:
        status = "OK " if counts[code] else "NONE"
        print(f"{status} {code}: {counts[code]} crossings")
    return 0 if all(counts[c] for c in sources.operators) else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="wave-scraper", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--dry-run", metavar="FILE", help="write payloads to FILE (NDJSON) instead of publishing")
    parser.add_argument("-v", "--verbose", action="store_true")
    sub = parser.add_subparsers(dest="command", required=True)

    crawl = sub.add_parser("crawl", help="scrape operator timetables and fares")
    crawl.add_argument("--operators", help="comma-separated codes (AF,CL,BAL,GNV,NE,ATM); default all")
    crawl.set_defaults(func=cmd_crawl)

    discover = sub.add_parser("discover", help="crawl an operator site for timetable pages")
    discover.add_argument("--operator", required=True)
    discover.set_defaults(func=cmd_discover)

    rates = sub.add_parser("rates", help="publish the Banque d'Algérie EUR/USD rates")
    rates.set_defaults(func=cmd_rates)

    fc = sub.add_parser("firecrawl", help="extract one page with Firecrawl")
    fc.add_argument("url")
    fc.add_argument("--operator", required=True)
    fc.set_defaults(func=cmd_firecrawl)

    ais = sub.add_parser("ais", help="forward an AIS receiver to the live map")
    group = ais.add_mutually_exclusive_group(required=True)
    group.add_argument("--udp", metavar="HOST:PORT")
    group.add_argument("--tcp", metavar="HOST:PORT")
    ais.add_argument("--all-ships", action="store_true", help="forward every ship, not only the catalog fleet")
    ais.set_defaults(func=cmd_ais)

    check = sub.add_parser("check", help="validate sources.yaml against the catalog")
    check.add_argument("--live", action="store_true", help="also crawl (dry run) and count crossings per company")
    check.set_defaults(func=cmd_check)

    args = parser.parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s"
    )
    if args.dry_run:
        os.environ["WAVE_DRY_RUN"] = args.dry_run
    code: int = args.func(args)
    return code


if __name__ == "__main__":
    sys.exit(main())
