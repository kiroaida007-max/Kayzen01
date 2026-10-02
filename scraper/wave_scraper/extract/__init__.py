"""Extractors: every function returns RawRecords; build_sailing() validates them."""

from .records import Context, RawPrice, RawRecord, Rejected, build_sailing
from .structured import from_embedded_state, from_json_ld, from_json_payload
from .tables import from_tables

__all__ = [
    "Context",
    "RawPrice",
    "RawRecord",
    "Rejected",
    "build_sailing",
    "extract_page",
    "from_embedded_state",
    "from_json_ld",
    "from_json_payload",
    "from_tables",
]


def extract_page(html: str, source_url: str | None = None) -> list[RawRecord]:
    """All extractors over one HTML page, most reliable first."""
    return [*from_json_ld(html, source_url), *from_embedded_state(html, source_url), *from_tables(html, source_url)]
