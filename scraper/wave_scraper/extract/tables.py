"""Timetables printed as HTML tables (common on Algerian operator and port sites)."""

from __future__ import annotations

import re

from parsel import Selector

from ..catalog import fold
from ..normalize import sold_out, status_from_text
from .records import RawPrice, RawRecord

# Column roles recognised from header text (folded), FR / EN / ES / IT / AR. Specific patterns
# come first: "Port de départ" is the origin column, "Heure de départ" the departure time.
_COLUMNS: list[tuple[str, str]] = [
    (
        "origin",
        r"\b(port de depart|puerto de (salida|origen)|porto di partenza|departure port|from port|origine|origen"
        r"|provenance)\b",
    ),
    (
        "destination",
        r"\b(port d arrivee|puerto de (llegada|destino)|porto di arrivo|arrival port|destination|destino)\b",
    ),
    ("date", r"\b(date|fecha|data|jour|day|dia|giorno|التاريخ)\b"),
    ("departure", r"\b(depart|departs|departure|salida|partenza|المغادرة)\b"),
    ("arrival", r"\b(arrivee|arrival|llegada|arrivo|الوصول)\b"),
    ("vessel", r"\b(navire|bateau|ship|vessel|buque|barco|nave|السفينة)\b"),
    ("duration", r"\b(duree|duration|duracion|durata|المدة)\b"),
    ("status", r"\b(statut|status|estado|stato|etat)\b"),
    ("price", r"\b(prix|tarif|tarifs|price|prices|fare|precio|prezzo|des|السعر)\b"),
]
# Bare one-word headers.
_EXACT = {
    "de": "origin",
    "from": "origin",
    "desde": "origin",
    "da": "origin",
    "vers": "destination",
    "a": "destination",
    "to": "destination",
    "hacia": "destination",
    "pour": "destination",
}


def _role(header: str) -> str | None:
    folded = fold(header)
    if folded in _EXACT:
        return _EXACT[folded]
    for role, pattern in _COLUMNS:
        if re.search(pattern, folded):
            return role
    return None


def from_tables(html: str, source_url: str | None = None) -> list[RawRecord]:
    selector = Selector(text=html)
    records: list[RawRecord] = []
    for table in selector.css("table"):
        header_cells = table.css("thead tr th, thead tr td") or table.css("tr:first-child th, tr:first-child td")
        headers = [" ".join(c.css("*::text").getall()).strip() for c in header_cells]
        roles = [_role(h) for h in headers]
        if "date" not in roles and "departure" not in roles:
            continue
        body_rows = table.css("tbody tr") or table.css("tr")[1:]
        for row in body_rows:
            cells = [" ".join(t.strip() for t in c.css("*::text").getall() if t.strip()) for c in row.css("td, th")]
            if len(cells) < 2:
                continue
            values: dict[str, str] = {}
            for role, cell in zip(roles, cells, strict=False):
                if role and cell and role not in values:
                    values[role] = cell
            date = values.get("date", "")
            departure = values.get("departure", "")
            # "Départ" may hold a time only, the date being in its own column.
            departure_text = (
                f"{date} {departure}".strip()
                if date and not re.search(r"\d{1,2}[/.-]\d{1,2}", departure)
                else departure or date
            )
            arrival = values.get("arrival")
            if arrival and not re.search(r"\d{1,2}[/.-]\d{1,2}|\d{4}-\d{2}", arrival) and date:
                arrival = f"{date} {arrival}"
            # Some timetables print "Annulé" or "Complet" in whatever column is free.
            status = values.get("status") or next((c for c in cells if status_from_text(c) or sold_out(c)), None)
            records.append(
                RawRecord(
                    origin=values.get("origin"),
                    destination=values.get("destination"),
                    departure=departure_text or None,
                    arrival=arrival,
                    duration=values.get("duration"),
                    vessel=values.get("vessel"),
                    status=status,
                    availability=status,
                    prices=[RawPrice(label=None, amount=values["price"])] if values.get("price") else [],
                    source_url=source_url,
                    extractor="table",
                )
            )
    return records
