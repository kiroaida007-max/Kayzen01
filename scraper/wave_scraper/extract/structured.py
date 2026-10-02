"""Structured data: schema.org JSON-LD, state embedded in scripts, and XHR payloads.

Operator sites change their markup often but rarely the data their own front-end consumes, so the
walker looks for "sailing-shaped" objects anywhere in a JSON tree instead of fixed paths."""

from __future__ import annotations

import json
import re
from collections.abc import Iterator
from typing import Any

from parsel import Selector

from .records import RawPrice, RawRecord, first

DEPARTURE_KEYS = (
    "departureDateTime",
    "departure_datetime",
    "departureDate",
    "departure_date",
    "departureTime",
    "departure_time",
    "departure",
    "dateDeparture",
    "date_depart",
    "dateDepart",
    "depart",
    "datedepart",
    "fechaSalida",
    "fecha_salida",
    "fechaHoraSalida",
    "salida",
    "dataPartenza",
    "partenza",
    "sailingDate",
    "sailing_date",
    "etd",
    "std",
    "startDate",
)
ARRIVAL_KEYS = (
    "arrivalDateTime",
    "arrival_datetime",
    "arrivalDate",
    "arrival_date",
    "arrivalTime",
    "arrival_time",
    "arrival",
    "dateArrival",
    "date_arrivee",
    "dateArrivee",
    "arrivee",
    "fechaLlegada",
    "fecha_llegada",
    "llegada",
    "dataArrivo",
    "arrivo",
    "eta",
    "sta",
    "endDate",
)
ORIGIN_KEYS = (
    "departurePort",
    "departure_port",
    "originPort",
    "origin_port",
    "portFrom",
    "fromPort",
    "from",
    "origin",
    "origen",
    "puertoOrigen",
    "puerto_origen",
    "portoPartenza",
    "port_depart",
    "portDepart",
    "originCode",
    "departureBoatTerminal",
)
DESTINATION_KEYS = (
    "arrivalPort",
    "arrival_port",
    "destinationPort",
    "destination_port",
    "portTo",
    "toPort",
    "to",
    "destination",
    "destino",
    "puertoDestino",
    "puerto_destino",
    "portoArrivo",
    "port_arrivee",
    "portArrivee",
    "destinationCode",
    "arrivalBoatTerminal",
)
VESSEL_KEYS = (
    "vesselName",
    "vessel_name",
    "shipName",
    "ship_name",
    "vessel",
    "ship",
    "boat",
    "barco",
    "buque",
    "nave",
    "navire",
)
DURATION_KEYS = ("durationMinutes", "duration_minutes", "duration", "crossingTime", "duracion", "durata", "duree")
STATUS_KEYS = ("status", "state", "estado", "stato", "statut", "sailingStatus")
AVAILABILITY_KEYS = ("availability", "available", "soldOut", "sold_out", "disponibilite", "disponibilidad", "places")
OPERATOR_KEYS = ("operator", "company", "carrier", "naviera", "compagnie", "provider", "brand")
PRICE_LIST_KEYS = ("prices", "fares", "tariffs", "rates", "offers", "tarifas", "tarifs", "prezzi", "products")
PRICE_KEYS = (
    "lowestPrice",
    "lowest_price",
    "minPrice",
    "min_price",
    "fromPrice",
    "from_price",
    "totalPrice",
    "price",
    "amount",
    "precio",
    "prezzo",
    "prix",
    "tarif",
    "fare",
    "value",
    "lowPrice",
)
CURRENCY_KEYS = ("currency", "currencyCode", "currency_code", "priceCurrency", "moneda", "valuta", "devise")
LABEL_KEYS = ("label", "name", "category", "type", "productName", "description", "fareName", "accommodation", "nombre")

_DATE_LIKE = re.compile(r"\d{4}-\d{2}-\d{2}|\d{1,2}[/.]\d{1,2}[/.]\d{2,4}|\d{1,2}\s+[A-Za-zéû]{3,}")


def _text(value: Any) -> str | None:
    """Ports and companies often come as {"code": "MRS", "name": "Marseille"}: keep both, the
    catalog lookup matches whichever it knows (UN/LOCODE or a name)."""
    if value is None:
        return None
    if isinstance(value, dict):
        parts = [first(value, ("code", "portCode", "locode")), first(value, ("name", "label", "nombre", "nome", "nom"))]
        joined = " ".join(str(p) for p in parts if isinstance(p, str | int | float))
        return joined or None
    if isinstance(value, list):
        return None
    return str(value)


def _date_value(obj: dict[str, Any], keys: tuple[str, ...], time_keys: tuple[str, ...] = ()) -> str | None:
    value = first(obj, keys)
    text = _text(value)
    if not text:
        return None
    if time_keys and not re.search(r"\d{1,2}[:h]\d{2}", text):
        clock = _text(first(obj, time_keys))
        if clock:
            text = f"{text} {clock}"
    return text


def _prices(obj: dict[str, Any]) -> list[RawPrice]:
    currency = _text(first(obj, CURRENCY_KEYS))
    out: list[RawPrice] = []
    for key in PRICE_LIST_KEYS:
        nested = first(obj, (key,))
        items = nested if isinstance(nested, list) else ([nested] if isinstance(nested, dict) else [])
        for item in items:
            if not isinstance(item, dict):
                continue
            amount = first(item, PRICE_KEYS)
            if isinstance(amount, dict):
                amount = first(amount, ("amount", "value", "price"))
            if amount is None:
                continue
            out.append(
                RawPrice(
                    label=_text(first(item, LABEL_KEYS)),
                    amount=amount if isinstance(amount, int | float | str) else None,
                    currency=_text(first(item, CURRENCY_KEYS)) or currency,
                )
            )
    if not out:
        amount = first(obj, PRICE_KEYS)
        if isinstance(amount, dict):
            currency = _text(first(amount, CURRENCY_KEYS)) or currency
            amount = first(amount, ("amount", "value", "price"))
        if isinstance(amount, int | float | str):
            out.append(RawPrice(label=None, amount=amount, currency=currency))
    return out


def looks_like_sailing(obj: dict[str, Any]) -> bool:
    departure = _date_value(obj, DEPARTURE_KEYS)
    return bool(departure and _DATE_LIKE.search(departure)) and (
        first(obj, ARRIVAL_KEYS) is not None
        or first(obj, DURATION_KEYS) is not None
        or first(obj, ORIGIN_KEYS) is not None
    )


def record_from_object(obj: dict[str, Any], extractor: str, source_url: str | None = None) -> RawRecord:
    availability = first(obj, AVAILABILITY_KEYS)
    if isinstance(availability, bool):
        availability = None if availability else "sold out"
    return RawRecord(
        operator=_text(first(obj, OPERATOR_KEYS)),
        origin=_text(first(obj, ORIGIN_KEYS)),
        destination=_text(first(obj, DESTINATION_KEYS)),
        departure=_date_value(obj, DEPARTURE_KEYS, ("departureHour", "horaSalida", "heureDepart", "oraPartenza")),
        arrival=_date_value(obj, ARRIVAL_KEYS, ("arrivalHour", "horaLlegada", "heureArrivee", "oraArrivo")),
        duration=_duration(first(obj, DURATION_KEYS)),
        vessel=_text(first(obj, VESSEL_KEYS)),
        status=_text(first(obj, STATUS_KEYS)),
        availability=_text(availability),
        prices=_prices(obj),
        source_url=source_url,
        extractor=extractor,
    )


def _duration(value: Any) -> str | int | None:
    if isinstance(value, int | float) and value > 0:
        # Minutes when large, hours when small ("duration": 20 means 20 h on most APIs).
        return int(value) if value > 72 else int(value * 60)
    return _text(value)


def walk(data: Any, extractor: str, source_url: str | None = None, depth: int = 0) -> Iterator[RawRecord]:
    """Every sailing-shaped object in a JSON tree (bounded depth, so hostile payloads cannot recurse forever)."""
    if depth > 12:
        return
    if isinstance(data, dict):
        if looks_like_sailing(data):
            yield record_from_object(data, extractor, source_url)
            return
        for value in data.values():
            yield from walk(value, extractor, source_url, depth + 1)
    elif isinstance(data, list):
        for item in data[:2000]:
            yield from walk(item, extractor, source_url, depth + 1)


# ------------------------------------------------------------------ JSON-LD (schema.org BoatTrip)


def from_json_ld(html: str, source_url: str | None = None) -> list[RawRecord]:
    selector = Selector(text=html)
    records: list[RawRecord] = []
    for block in selector.css('script[type="application/ld+json"]::text').getall():
        try:
            data = json.loads(block)
        except json.JSONDecodeError:
            continue
        for node in _ld_nodes(data):
            types = node.get("@type")
            types = types if isinstance(types, list) else [types]
            if not {"BoatTrip", "Trip"} & set(types):
                continue
            offers = node.get("offers")
            offers = offers if isinstance(offers, list) else ([offers] if isinstance(offers, dict) else [])
            records.append(
                RawRecord(
                    operator=_text(node.get("provider")),
                    origin=_text(node.get("departureBoatTerminal") or node.get("departureLocation")),
                    destination=_text(node.get("arrivalBoatTerminal") or node.get("arrivalLocation")),
                    departure=_text(node.get("departureTime")),
                    arrival=_text(node.get("arrivalTime")),
                    vessel=_text(node.get("vessel")) or _subject_name(node),
                    availability=" ".join(
                        str(o.get("availability", "")) for o in offers if isinstance(o, dict)
                    ).replace("https://schema.org/", ""),
                    prices=[
                        RawPrice(
                            label=_text(o.get("name") or o.get("category")),
                            amount=o.get("price", o.get("lowPrice")),
                            currency=_text(o.get("priceCurrency")),
                        )
                        for o in offers
                        if isinstance(o, dict) and o.get("price", o.get("lowPrice")) is not None
                    ],
                    source_url=source_url,
                    extractor="json-ld",
                )
            )
    return records


def _subject_name(node: dict[str, Any]) -> str | None:
    subject = node.get("subjectOf")
    return _text(subject.get("name")) if isinstance(subject, dict) else None


def _ld_nodes(data: Any) -> Iterator[dict[str, Any]]:
    if isinstance(data, list):
        for item in data:
            yield from _ld_nodes(item)
    elif isinstance(data, dict):
        if "@graph" in data:
            yield from _ld_nodes(data["@graph"])
        else:
            yield data


# ------------------------------------------------------------------ state embedded in <script>

_ASSIGNMENT = re.compile(
    r"(?:window\.)?(__NEXT_DATA__|__NUXT__|__INITIAL_STATE__|__APOLLO_STATE__|__PRELOADED_STATE__|initialState|"
    r"sailings|timetable|horaires|schedules)\s*=\s*",
)


def from_embedded_state(html: str, source_url: str | None = None) -> list[RawRecord]:
    selector = Selector(text=html)
    records: list[RawRecord] = []
    for script in selector.css("script"):
        body = script.xpath("string()").get() or ""
        script_type = (script.attrib.get("type") or "").lower()
        if script_type in ("application/json", "application/ld+json") or script.attrib.get("id") == "__NEXT_DATA__":
            if script_type == "application/ld+json":
                continue
            records.extend(_parse_and_walk(body, source_url))
            continue
        for match in _ASSIGNMENT.finditer(body):
            blob = _balanced_json(body, match.end())
            if blob:
                records.extend(_parse_and_walk(blob, source_url))
    return records


def from_json_payload(payload: Any, source_url: str | None = None) -> list[RawRecord]:
    return list(walk(payload, "xhr", source_url))


def _parse_and_walk(text: str, source_url: str | None) -> list[RawRecord]:
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return []
    return list(walk(data, "embedded", source_url))


def _balanced_json(text: str, start: int) -> str | None:
    """The JSON object or array starting at `start`, found by bracket matching outside strings."""
    while start < len(text) and text[start].isspace():
        start += 1
    if start >= len(text) or text[start] not in "{[":
        return None
    depth, in_string, escape = 0, False, False
    for i in range(start, min(len(text), start + 5_000_000)):
        c = text[i]
        if in_string:
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True
        elif c in "{[":
            depth += 1
        elif c in "}]":
            depth -= 1
            if depth == 0:
                return text[start : i + 1]
    return None
