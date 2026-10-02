from __future__ import annotations

import pytest
from conftest import context, fixture_json, fixture_text

from wave_scraper import firecrawl
from wave_scraper.catalog import Catalog
from wave_scraper.extract import Rejected, build_sailing, extract_page, from_json_payload
from wave_scraper.models import Sailing

BAL_URL = "https://www.balearia.com/fr/routes-horaires/bateau-valence-mostaganem"


def build_all(
    operator: str, records: list, url: str, cat: Catalog, currency: str = "EUR"
) -> tuple[list[Sailing], list[str]]:
    ctx = context(operator, url, currency)
    ok, rejected = [], []
    for record in records:
        try:
            ok.append(build_sailing(record, ctx, cat))
        except Rejected as e:
            rejected.append(str(e))
    return ok, rejected


def prices(s: Sailing) -> dict[str, tuple[float, str]]:
    return {p.category: (p.amount, p.currency) for p in s.prices}


def test_embedded_next_data(cat: Catalog) -> None:
    sailings, rejected = build_all("BAL", extract_page(fixture_text("balearia_next.html"), BAL_URL), BAL_URL, cat)
    assert rejected == []
    first, second = sailings
    assert (first.origin, first.destination) == ("ESVLC", "DZMOS")
    # 23:00 in Valencia (UTC+2) → arrival 13:00 on the Algerian clock (UTC+1), next day.
    assert first.departureLocal == "2026-10-14T23:00"
    assert first.arrivalLocal == "2026-10-15T13:00"
    assert first.vessel == "Visborg"
    assert prices(first) == {"ADULT_SEAT": (130.0, "EUR"), "CABIN_INT_2": (245.5, "EUR"), "VEHICLE_CAR": (165.0, "EUR")}
    assert second.availability is not None and second.availability.level == "SOLD_OUT"


def test_html_timetable(cat: Catalog) -> None:
    url = "https://www.algerieferries.dz/programme"
    sailings, rejected = build_all("AF", extract_page(fixture_text("af_timetable.html"), url), url, cat, currency="DZD")
    by_route = {(s.origin, s.destination): s for s in sailings}
    marseille_alger = by_route[("FRMRS", "DZALG")]
    assert marseille_alger.departureLocal == "2026-10-14T13:00"
    assert marseille_alger.arrivalLocal == "2026-10-15T08:00", "arrival printed as a time only is on the next day"
    assert prices(marseille_alger) == {"ADULT_SEAT": (146.0, "EUR")}
    assert prices(by_route[("DZALG", "FRMRS")]) == {"ADULT_SEAT": (21620.0, "DZD")}
    assert by_route[("DZORN", "ESALC")].status == "CANCELLED"
    assert sorted(rejected) == ["past_departure", "unknown_port"]  # Marseille–Tunis and a September crossing


def test_json_ld_boat_trip(cat: Catalog) -> None:
    url = "https://www.corsicalinea.com/traversees/marseille-alger"
    (sailing,), rejected = build_all("CL", extract_page(fixture_text("cl_jsonld.html"), url), url, cat)
    assert rejected == []
    assert (sailing.origin, sailing.destination) == ("FRMRS", "DZALG")
    assert sailing.departureLocal == "2026-10-21T20:00"
    assert sailing.arrivalLocal == "2026-10-22T20:00"
    assert prices(sailing) == {
        "ADULT_SEAT": (146.0, "EUR"),
        "CABIN_INT_2": (650.0, "EUR"),
        "CABIN_EXT_2": (750.0, "EUR"),
    }


def test_xhr_payload_and_operator_check(cat: Catalog) -> None:
    url = "https://www.gnv.it/fr/traghetti/sete-bejaia"
    sailings, rejected = build_all("GNV", from_json_payload(fixture_json("gnv_xhr.json"), url), url, cat)
    assert rejected == ["operator_mismatch"]  # a Grimaldi crossing in the same payload is not GNV's
    (sailing,) = sailings
    assert (sailing.origin, sailing.destination, sailing.vessel) == ("FRSET", "DZBJA", "GNV Fantastic")
    assert sailing.arrivalLocal == "2026-10-21T20:00"
    assert prices(sailing)["VEHICLE_CAR"] == (189.0, "EUR")


def test_firecrawl_response(cat: Catalog) -> None:
    url = "https://www.nouriselbahrferries.com/horaires"
    records = firecrawl.records_from_response(fixture_json("firecrawl_v2.json"), url)
    sailings, rejected = build_all("NE", records, url, cat, currency="DZD")
    assert rejected == []
    outbound, inbound = sailings
    assert (outbound.origin, outbound.destination, outbound.departureLocal) == ("ESALC", "DZALG", "2026-10-15T20:00")
    assert prices(outbound) == {"ADULT_SEAT": (168.23, "EUR"), "VEHICLE_CAR": (210.0, "EUR")}
    assert prices(inbound) == {"ADULT_SEAT": (25235.0, "DZD")}


def test_firecrawl_v1_shape_and_failures() -> None:
    v1 = {
        "success": True,
        "data": {"extract": {"sailings": [{"from": "Oran", "to": "Valence", "departure": "2026-10-30 22:00"}]}},
    }
    assert len(firecrawl.records_from_response(v1, "u")) == 1
    assert firecrawl.records_from_response({"success": False}, "u") == []
    assert firecrawl.records_from_response({"success": True, "data": {"json": "not json"}}, "u") == []


def test_hostile_payloads_are_bounded() -> None:
    deep: dict = {}
    node = deep
    for _ in range(200):
        node["child"] = {}
        node = node["child"]
    assert from_json_payload(deep) == []


@pytest.mark.parametrize("bad", ["2026-10-14T25:00", "yesterday", ""])
def test_unparseable_departures_are_rejected(cat: Catalog, bad: str) -> None:
    from wave_scraper.extract import RawRecord

    record = RawRecord(origin="Valence", destination="Mostaganem", departure=bad or None, duration="14h")
    with pytest.raises(Rejected):
        build_sailing(record, context("BAL", BAL_URL), cat)
