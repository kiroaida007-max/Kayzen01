"""The common shape every extractor produces, and its conversion into a validated Sailing."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Any

from ..catalog import Catalog
from ..models import Availability, Price, Sailing
from ..normalize import (
    category_from_label,
    parse_currency,
    parse_duration_minutes,
    parse_price,
    sold_out,
    status_from_text,
    to_port_local,
)


@dataclass
class RawPrice:
    label: str | None = None
    amount: str | float | None = None
    currency: str | None = None


@dataclass
class RawRecord:
    """A crossing as found on a page or in an API payload, before any normalisation."""

    operator: str | None = None
    origin: str | None = None
    destination: str | None = None
    departure: str | datetime | None = None
    arrival: str | datetime | None = None
    duration: str | int | None = None
    vessel: str | None = None
    status: str | None = None
    availability: str | None = None
    prices: list[RawPrice] = field(default_factory=list)
    source_url: str | None = None
    extractor: str = "unknown"


@dataclass
class Context:
    """What the spider already knows about the page (operator, route in the URL, currency)."""

    operator: str
    origin: str | None = None
    destination: str | None = None
    currency: str | None = None
    today: date = field(default_factory=date.today)
    source_url: str | None = None


class Rejected(ValueError):
    """A record that cannot become a sailing; the message is the reason counted in the stats."""


def build_sailing(raw: RawRecord, ctx: Context, catalog: Catalog) -> Sailing:
    operator = catalog.operator_code(raw.operator) if raw.operator else ctx.operator
    if operator is None or operator != ctx.operator:
        raise Rejected("operator_mismatch")
    origin = catalog.port_code(raw.origin) if raw.origin else ctx.origin
    destination = catalog.port_code(raw.destination) if raw.destination else ctx.destination
    if origin is None or destination is None:
        raise Rejected("unknown_port")
    if catalog.route(operator, origin, destination) is None:
        raise Rejected("unknown_route")
    if raw.departure is None:
        raise Rejected("no_departure")
    from_zone, to_zone = catalog.ports[origin].zone, catalog.ports[destination].zone
    departure = to_port_local(raw.departure, from_zone, ctx.today)
    if departure is None:
        raise Rejected("bad_departure")
    departure_dt = datetime.fromisoformat(departure)
    if departure_dt.date() < ctx.today - timedelta(days=1):
        raise Rejected("past_departure")

    arrival = to_port_local(raw.arrival, to_zone, ctx.today) if raw.arrival is not None else None
    duration: int | None = None
    if isinstance(raw.duration, int):
        duration = raw.duration
    elif isinstance(raw.duration, str):
        duration = parse_duration_minutes(raw.duration)
    if arrival is not None and datetime.fromisoformat(arrival) < departure_dt - timedelta(hours=2):
        # Timetables often print the arrival time only: it is then on a later day.
        arrival_dt = datetime.fromisoformat(arrival)
        while arrival_dt < departure_dt - timedelta(hours=2):
            arrival_dt += timedelta(days=1)
        arrival = arrival_dt.strftime("%Y-%m-%dT%H:%M")

    prices: list[Price] = []
    for p in raw.prices:
        category = category_from_label(p.label) if p.label else "ADULT_SEAT"
        if category is None:
            continue
        currency = (parse_currency(p.currency) if p.currency else None) or ctx.currency
        if isinstance(p.amount, int | float | Decimal):
            amount: Decimal | None = Decimal(str(p.amount))
        elif isinstance(p.amount, str):
            parsed = parse_price(p.amount, currency)
            amount, currency = (parsed[0], parsed[1]) if parsed else (None, currency)
        else:
            amount = None
        if amount is None or currency is None or amount <= 0:
            continue
        prices.append(Price(category=category, amount=float(amount), currency=currency))  # type: ignore[arg-type]

    status = status_from_text(raw.status) or "SCHEDULED"
    availability = Availability(level="SOLD_OUT") if sold_out(raw.availability) or sold_out(raw.status) else None
    try:
        return Sailing.model_validate(
            {
                "operator": operator,
                "from": origin,
                "to": destination,
                "departureLocal": departure,
                "arrivalLocal": arrival,
                "durationMin": duration if arrival is None else None,
                "vessel": raw.vessel.strip() if raw.vessel else None,
                "status": status,
                "prices": _cheapest_per_category(prices),
                "availability": availability,
                "sourceUrl": raw.source_url or ctx.source_url,
            }
        )
    except ValueError as e:
        raise Rejected("invalid") from e


def _cheapest_per_category(prices: list[Price]) -> list[Price]:
    """Pages often list several fares per category (promo, flex): the search shows the lowest."""
    best: dict[str, Price] = {}
    for p in prices:
        current = best.get(p.category)
        if current is None or (p.currency == current.currency and p.amount < current.amount):
            best[p.category] = p
    return list(best.values())


def first(obj: dict[str, Any], keys: tuple[str, ...]) -> Any:
    """Value of the first key present (case-insensitive), skipping empty values."""
    lowered = {k.lower(): v for k, v in obj.items()}
    for key in keys:
        value = lowered.get(key.lower())
        if value not in (None, "", [], {}):
            return value
    return None
