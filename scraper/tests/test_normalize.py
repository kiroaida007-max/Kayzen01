from datetime import date
from decimal import Decimal

import pytest

from wave_scraper.normalize import (
    category_from_label,
    parse_amount,
    parse_currency,
    parse_date,
    parse_duration_minutes,
    parse_price,
    parse_time,
    sold_out,
    status_from_text,
    to_port_local,
)


@pytest.mark.parametrize(
    ("text", "amount", "currency"),
    [
        ("1 234,56 €", Decimal("1234.56"), "EUR"),
        ("€1,234.56", Decimal("1234.56"), "EUR"),
        ("21 620 DA", Decimal("21620"), "DZD"),
        ("21.620 DZD", Decimal("21620"), "DZD"),
        ("144 136 د.ج", Decimal("144136"), "DZD"),
        ("from 280.90 €", Decimal("280.90"), "EUR"),
        ("dès 63 €", Decimal("63"), "EUR"),
        ("USD 99", Decimal("99"), "USD"),
    ],
)
def test_prices(text: str, amount: Decimal, currency: str) -> None:
    assert parse_price(text) == (amount, currency)


def test_amounts_without_currency_need_a_default() -> None:
    assert parse_amount("0") is None
    assert parse_price("1.234") is None
    assert parse_price("1.234", "DZD") == (Decimal("1234"), "DZD")
    assert parse_currency("Dakar") is None  # "da" inside a word is not dinars


@pytest.mark.parametrize(
    ("label", "category"),
    [
        ("Butaca", "ADULT_SEAT"),
        ("Passage adulte", "ADULT_SEAT"),
        ("Poltrona", "ADULT_SEAT"),
        ("Cabine intérieure 2 personnes", "CABIN_INT_2"),
        ("Cabina interior cuádruple 4 camas", "CABIN_INT_4"),
        ("Cabine extérieure 4 lits", "CABIN_EXT_4"),
        ("Cabine extérieure", "CABIN_EXT_2"),
        ("Suite", "SUITE"),
        ("Cabine animaux admis", "CABIN_PET"),
        ("Cabine adaptée PMR", "CABIN_PMR"),
        ("Voiture (≤ 5 m)", "VEHICLE_CAR"),
        ("SUV / 4x4", "VEHICLE_CAR_HIGH"),
        ("Véhicule haut (> 1,90 m)", "VEHICLE_CAR_HIGH"),
        ("Moto", "VEHICLE_MOTORCYCLE"),
        ("Camping-car", "VEHICLE_CAMPER"),
        ("Voiture avec remorque", "VEHICLE_CAR_TRAILER"),
        ("Fourgon", "VEHICLE_VAN"),
        ("Assurance annulation", None),
    ],
)
def test_categories(label: str, category: str | None) -> None:
    assert category_from_label(label) == category


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("20:00", (20, 0)),
        ("20h", (20, 0)),
        ("20h30", (20, 30)),
        ("20 h 30", (20, 30)),
        ("8.30 pm", (20, 30)),
        ("12 am", (0, 0)),
    ],
)
def test_times(text: str, expected: tuple[int, int]) -> None:
    assert parse_time(text) == expected


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("14/10/2026", date(2026, 10, 14)),
        ("2026-10-14", date(2026, 10, 14)),
        ("14 oct. 2026", date(2026, 10, 14)),
        ("mié 14 oct", date(2026, 10, 14)),
        ("14.10.26", date(2026, 10, 14)),
        ("October 14, 2026", date(2026, 10, 14)),
        ("1er janvier", date(2027, 1, 1)),
        ("31/02/2026", None),
    ],
)
def test_dates(text: str, expected: date | None) -> None:
    assert parse_date(text, today=date(2026, 10, 2)) == expected


def test_port_local_time_follows_the_port_time_zone() -> None:
    # Spain is UTC+2 in October, Algeria UTC+1 all year: 20:00 Madrid time is 19:00 in Algiers.
    assert to_port_local("2026-10-14T20:00:00+02:00", "Africa/Algiers") == "2026-10-14T19:00"
    assert to_port_local("2026-10-14T20:00:00Z", "Europe/Paris") == "2026-10-14T22:00"
    # Naive values are already the port's wall clock.
    assert to_port_local("2026-10-14 20:00", "Africa/Algiers") == "2026-10-14T20:00"
    assert to_port_local("Mer. 14 oct. 2026 à 20h00", "Africa/Algiers", date(2026, 10, 2)) == "2026-10-14T20:00"
    assert to_port_local("no date here", "Africa/Algiers") is None


def test_durations_and_status() -> None:
    assert parse_duration_minutes("23h30") == 1410
    assert parse_duration_minutes("PT20H") == 1200
    assert parse_duration_minutes("1410 min") == 1410
    assert status_from_text("Annulé") == "CANCELLED"
    assert status_from_text("Retardé de 2h") == "DELAYED"
    assert status_from_text("On time") is None
    assert sold_out("Completo") and sold_out("COMPLET") and not sold_out("Disponible")
