"""Turns the many ways operators print prices, dates and fare names into the backend's vocabulary."""

from __future__ import annotations

import re
import unicodedata
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

from .catalog import fold

# ------------------------------------------------------------------ prices

_CURRENCY_MARKERS: list[tuple[str, str]] = [
    ("€", "EUR"),
    ("eur", "EUR"),
    ("euro", "EUR"),
    ("$", "USD"),
    ("usd", "USD"),
    ("dzd", "DZD"),
    ("da", "DZD"),
    ("د.ج", "DZD"),
    ("دج", "DZD"),
    ("dinar", "DZD"),
]


def parse_amount(text: str) -> Decimal | None:
    """'1 234,56' → 1234.56, '1,234.56' → 1234.56, '21.620' → 21620 (dot as thousands in DZD/ES),
    '185' → 185. Returns None when no number is present."""
    match = re.search(r"\d[\d\s\u00a0\u202f.,']*", text)
    if not match:
        return None
    raw = re.sub(r"[\s\u00a0\u202f']", "", match.group(0)).rstrip(".,")
    if "," in raw and "." in raw:
        # The right-most separator is the decimal one.
        raw = raw.replace(".", "").replace(",", ".") if raw.rfind(",") > raw.rfind(".") else raw.replace(",", "")
    elif "," in raw:
        head, _, tail = raw.rpartition(",")
        raw = raw.replace(",", ".") if len(tail) in (1, 2) else raw.replace(",", "")
    elif raw.count(".") >= 1:
        head, _, tail = raw.rpartition(".")
        if len(tail) == 3 and raw.count(".") >= 1 and len(head.replace(".", "")) >= 1:
            raw = raw.replace(".", "")  # "21.620" is twenty-one thousand, not 21.62
    try:
        value = Decimal(raw)
    except InvalidOperation:
        return None
    return value if value > 0 else None


def parse_currency(text: str, default: str | None = None) -> str | None:
    lowered = text.casefold()
    for marker, code in _CURRENCY_MARKERS:
        if marker in ("da", "eur", "usd", "dzd"):
            if re.search(rf"(?<![a-z]){marker}(?![a-z])", lowered):
                return code
        elif marker in lowered:
            return code
    return default


def parse_price(text: str, default_currency: str | None = None) -> tuple[Decimal, str] | None:
    amount = parse_amount(text)
    currency = parse_currency(text, default_currency)
    if amount is None or currency is None:
        return None
    return amount, currency


# ------------------------------------------------------------------ fare categories

# Order matters: the first pattern found in the folded label wins.
_CATEGORY_RULES: list[tuple[str, str]] = [
    (r"\b(pmr|accessible|adaptee|adaptada|disab)", "CABIN_PMR"),
    (r"\b(animal|animaux|pet|mascota)\w*\b.*\bcabin|\bcabin\w*\b.*\b(animal|animaux|pet|mascota)", "CABIN_PET"),
    (r"\bsuite\b", "SUITE"),
    (r"\b(camping car|camper|autocaravana|motorhome|camping)\b", "VEHICLE_CAMPER"),
    (r"\b(remorque|caravane|trailer|caravan|remolque)\b", "VEHICLE_CAR_TRAILER"),
    (r"\b(fourgon|utilitaire|van|furgoneta)\b", "VEHICLE_VAN"),
    (r"\b(moto|motorcycle|motocicleta|scooter)\b", "VEHICLE_MOTORCYCLE"),
    (r"\b(4x4|suv|monospace|minivan|todoterreno|monovolumen)\b", "VEHICLE_CAR_HIGH"),
    (
        r"\b(haut|high|alto)\b.*\b(voiture|vehicule|car|coche|auto)\b|\b(voiture|vehicule|car|coche|auto)\b.*\b(haut|high|alto)\b",
        "VEHICLE_CAR_HIGH",
    ),
    (r"\b(voiture|vehicule|car|coche|auto|automobile|vehicle)\b", "VEHICLE_CAR"),
    (
        r"\b(cabine|cabin|cabina|camarote)\w*\b.*\b(exterieur\w*|exterior|outside|hublot|vue mer|sea view)\b.*\b4\b",
        "CABIN_EXT_4",
    ),
    (
        r"\b(cabine|cabin|cabina|camarote)\w*\b.*\b(exterieur\w*|exterior|outside|hublot|vue mer|sea view)\b",
        "CABIN_EXT_2",
    ),
    (r"\b(cabine|cabin|cabina|camarote)\w*\b.*\b4\b", "CABIN_INT_4"),
    (r"\b(cabine|cabin|cabina|camarote)\w*\b", "CABIN_INT_2"),
    (
        r"\b(fauteuil|seat|butaca|poltrona|siege|pont|deck|passage|passager|passenger|pasajero|adulte|adult|adulto)\b",
        "ADULT_SEAT",
    ),
]


def category_from_label(label: str) -> str | None:
    """'Cabine extérieure 4 lits' → CABIN_EXT_4, 'Butaca' → ADULT_SEAT, 'Voiture (≤ 5 m)' → VEHICLE_CAR."""
    folded = fold(label)
    for pattern, category in _CATEGORY_RULES:
        if re.search(pattern, folded):
            return category
    return None


# ------------------------------------------------------------------ status


def status_from_text(text: str | None) -> str | None:
    if not text:
        return None
    folded = fold(text)
    if re.search(r"\b(annul\w*|cancel\w*|cancelad\w*|supprim\w*)\b", folded):
        return "CANCELLED"
    if re.search(r"\b(retard\w*|delay\w*|retras\w*|ritard\w*)\b", folded):
        return "DELAYED"
    if re.search(r"\b(embarquement|boarding|embarque)\b", folded):
        return "BOARDING"
    if re.search(r"\b(parti|departed|salido|partito)\b", folded):
        return "DEPARTED"
    if re.search(r"\b(arrive|arrived|llegado|arrivato)\b", folded):
        return "ARRIVED"
    return None


_SOLD_OUT = re.compile(r"\b(complet|complete|completo|completa|sold out|agotado|esaurito|full|plus de places?)\b")


def sold_out(text: str | None) -> bool:
    return bool(text) and bool(_SOLD_OUT.search(fold(text or "")))


# ------------------------------------------------------------------ dates & times

_MONTHS = {
    # French, English, Spanish, Italian (folded, 3+ letter prefixes are matched).
    "janv": 1,
    "jan": 1,
    "ene": 1,
    "genn": 1,
    "gen": 1,
    "fevr": 2,
    "fev": 2,
    "feb": 2,
    "febr": 2,
    "mars": 3,
    "mar": 3,
    "marz": 3,
    "avr": 4,
    "apr": 4,
    "abr": 4,
    "mai": 5,
    "may": 5,
    "mag": 5,
    "juin": 6,
    "jun": 6,
    "giu": 6,
    "juil": 7,
    "jul": 7,
    "lug": 7,
    "aout": 8,
    "aug": 8,
    "ago": 8,
    "sept": 9,
    "sep": 9,
    "set": 9,
    "oct": 10,
    "ott": 10,
    "nov": 11,
    "dec": 12,
    "dic": 12,
}


def _month(token: str) -> int | None:
    token = fold(token)
    for length in (5, 4, 3):
        if token[:length] in _MONTHS:
            return _MONTHS[token[:length]]
    return None


def _lower(text: str) -> str:
    """Lower case without accents but, unlike fold(), keeping ':' and '.' that times rely on."""
    decomposed = unicodedata.normalize("NFKD", text)
    return "".join(c for c in decomposed if not unicodedata.combining(c)).casefold()


def parse_time(text: str) -> tuple[int, int] | None:
    """'20:00', '20h', '20h30', '8.30 pm', '20 h 30'."""
    folded = re.sub(r"\s+h\s*", "h", _lower(text))
    m = re.search(r"\b(\d{1,2})\s*(?:[:h.]\s*(\d{2})?)\s*(am|pm)?\b", folded)
    if not m:
        m = re.search(r"\b(\d{1,2})\s*(am|pm)\b", folded)
        if not m:
            return None
        hour, minute, suffix = int(m.group(1)), 0, m.group(2)
    else:
        hour, minute, suffix = int(m.group(1)), int(m.group(2) or 0), m.group(3)
    if suffix == "pm" and hour < 12:
        hour += 12
    if suffix == "am" and hour == 12:
        hour = 0
    if hour > 23 or minute > 59:
        return None
    return hour, minute


def parse_date(text: str, today: date | None = None) -> date | None:
    """'14/10/2026', '2026-10-14', '14 oct. 2026', 'mié 14 oct', '14.10.26'. Year-less dates are
    placed in the next 12 months."""
    today = today or date.today()
    folded = fold(text)
    m = re.search(r"\b(\d{4})-(\d{1,2})-(\d{1,2})\b", text)
    if m:
        return _safe_date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
    m = re.search(r"\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b", text)
    if m:
        year = int(m.group(3))
        year = year + 2000 if year < 100 else year
        return _safe_date(year, int(m.group(2)), int(m.group(1)))
    m = re.search(r"\b(\d{1,2})(?:er)?\s+([a-z]{3,})\.?(?:\s+(\d{4}))?\b", folded)
    if m and _month(m.group(2)):
        month = _month(m.group(2))
        assert month is not None
        if m.group(3):
            return _safe_date(int(m.group(3)), month, int(m.group(1)))
        candidate = _safe_date(today.year, month, int(m.group(1)))
        if candidate and candidate < today - timedelta(days=2):
            candidate = _safe_date(today.year + 1, month, int(m.group(1)))
        return candidate
    m = re.search(r"\b([a-z]{3,})\s+(\d{1,2}),?\s+(\d{4})\b", folded)  # "October 14, 2026"
    if m and _month(m.group(1)):
        month = _month(m.group(1))
        assert month is not None
        return _safe_date(int(m.group(3)), month, int(m.group(2)))
    return None


def _safe_date(year: int, month: int, day: int) -> date | None:
    try:
        return date(year, month, day)
    except ValueError:
        return None


def to_port_local(value: str | datetime, zone: str, today: date | None = None) -> str | None:
    """Any timestamp → the port's wall clock as 'yyyy-MM-ddTHH:mm' (the backend contract).

    Offsets are honoured ('2026-10-14T20:00:00+02:00' at Algiers → '2026-10-14T19:00'); naive
    values are taken as already local, which is how operators print their timetables."""
    if isinstance(value, datetime):
        moment = value
    else:
        text = value.strip()
        iso = re.match(r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$", text)
        if iso:
            try:
                moment = datetime.fromisoformat(text.replace("Z", "+00:00").replace(" ", "T"))
            except ValueError:
                return None
        else:
            day = parse_date(text, today)
            clock = parse_time(re.sub(r"\b\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}\b|\b\d{4}-\d{2}-\d{2}\b", " ", text))
            if day is None or clock is None:
                return None
            moment = datetime(day.year, day.month, day.day, clock[0], clock[1])
    if moment.tzinfo is not None:
        moment = moment.astimezone(ZoneInfo(zone)).replace(tzinfo=None)
    return moment.strftime("%Y-%m-%dT%H:%M")


def parse_duration_minutes(text: str) -> int | None:
    """'20h', '23h30', '23 h 30 min', '1410 min', 'PT23H30M'."""
    m = re.fullmatch(r"P(?:T)?(?:(\d+)H)?(?:(\d+)M)?", text.strip().upper())
    if m and (m.group(1) or m.group(2)):
        return int(m.group(1) or 0) * 60 + int(m.group(2) or 0)
    folded = fold(text)
    m = re.search(r"\b(\d{1,2})\s*h(?:eures?|ours?|oras?)?\s*(\d{1,2})?", folded)
    if m:
        return int(m.group(1)) * 60 + int(m.group(2) or 0)
    m = re.search(r"\b(\d{2,4})\s*min", folded)
    return int(m.group(1)) if m else None
