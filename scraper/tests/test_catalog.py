from wave_scraper.catalog import Catalog


def test_port_names_in_every_language(cat: Catalog) -> None:
    assert cat.port_code("Alger") == "DZALG"
    assert cat.port_code("ALGIERS") == "DZALG"
    assert cat.port_code("Argel") == "DZALG"
    assert cat.port_code("وهران") == "DZORN"
    assert cat.port_code("Port de Marseille (Cap Janet)") == "FRMRS"
    assert cat.port_code("FRSET Sète") == "FRSET"
    assert cat.port_code("Paris") is None


def test_routes_from_url_slugs(cat: Catalog) -> None:
    assert cat.ports_in("https://www.balearia.com/fr/routes-horaires/bateau-valence-mostaganem") == ["ESVLC", "DZMOS"]
    assert cat.ports_in("/en/routes-timetables/ferry-barcelona-algiers") == ["ESBCN", "DZALG"]


def test_operator_names(cat: Catalog) -> None:
    assert cat.operator_code("Baleària Eurolíneas Marítimas") == "BAL"
    assert cat.operator_code("GNV - Grandi Navi Veloci") == "GNV"
    assert cat.operator_code("Nouris Elbahr Ferries") == "NE"
    assert cat.operator_code("Grimaldi Lines") is None


def test_fleet_mmsis_match_the_backend(cat: Catalog) -> None:
    assert "605016420" in cat.tracked_mmsis  # Badji Mokhtar III
    vessel = cat.vessel_by_mmsi("605016420")
    assert vessel is not None and vessel.operator == "AF"
