"""Loads the source list: wave_scraper/data/sources.yaml, or the file named by WAVE_SOURCES
(e.g. a Kubernetes ConfigMap) so URLs can change without a release."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from functools import cache
from importlib import resources
from pathlib import Path
from typing import Any

import yaml


@dataclass(frozen=True)
class Source:
    operator: str
    name: str
    start_urls: tuple[str, ...]
    allowed_domains: tuple[str, ...]
    currency: str | None = None
    playwright: bool = True
    wait_for: str = "body"
    firecrawl: bool = True
    xhr_patterns: tuple[str, ...] = ()
    discovery: tuple[str, ...] = ()


@dataclass(frozen=True)
class Sources:
    operators: dict[str, Source]
    rates: dict[str, Any] = field(default_factory=dict)


def _source(code: str, raw: dict[str, Any], defaults: dict[str, Any]) -> Source:
    merged = {**defaults, **raw}
    return Source(
        operator=code,
        name=merged.get("name", code),
        start_urls=tuple(merged.get("start_urls", [])),
        allowed_domains=tuple(merged.get("allowed_domains", [])),
        currency=merged.get("currency"),
        playwright=bool(merged.get("playwright", True)),
        wait_for=str(merged.get("wait_for", "body")),
        firecrawl=bool(merged.get("firecrawl", True)),
        xhr_patterns=tuple(merged.get("xhr_patterns", [])),
        discovery=tuple(merged.get("discovery", [])),
    )


@cache
def load_sources(path: str | None = None) -> Sources:
    override = path or os.environ.get("WAVE_SOURCES")
    text = (
        Path(override).read_text("utf-8")
        if override
        else resources.files("wave_scraper").joinpath("data/sources.yaml").read_text("utf-8")
    )
    raw = yaml.safe_load(text)
    defaults = raw.get("defaults", {})
    operators = {code: _source(code, cfg or {}, defaults) for code, cfg in (raw.get("operators") or {}).items()}
    return Sources(operators=operators, rates=raw.get("rates") or {})
