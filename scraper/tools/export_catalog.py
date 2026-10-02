"""Builds wave_scraper/data/catalog.json from the backend catalog, so the scraper normalises
ports, routes, operators and ships with exactly the codes the API accepts.

    python tools/export_catalog.py ../backend/src/main/resources/catalog
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

# Spellings found on operator sites (FR / EN / ES / IT / AR) besides the catalog names.
PORT_ALIASES: dict[str, list[str]] = {
    "DZALG": ["Alger", "Algiers", "Argel", "Algeri", "Algier", "El Djazair", "الجزائر"],
    "DZORN": ["Oran", "Orán", "Wahran", "وهران"],
    "DZBJA": ["Béjaïa", "Bejaia", "Bejaïa", "Bougie", "بجاية"],
    "DZSKI": ["Skikda", "سكيكدة"],
    "DZAAE": ["Annaba", "Bône", "عنابة"],
    "DZMOS": ["Mostaganem", "مستغانم"],
    "DZGHZ": ["Ghazaouet", "Ghazawet", "الغزوات"],
    "FRMRS": ["Marseille", "Marsella", "Marsiglia", "Marseilles", "مرسيليا"],
    "FRSET": ["Sète", "Sete", "سات"],
    "ESBCN": ["Barcelone", "Barcelona", "برشلونة"],
    "ESVLC": ["Valence", "Valencia", "València", "فالنسيا"],
    "ESALC": ["Alicante", "Alacant", "أليكانتي"],
    "ESLEI": ["Almería", "Almeria", "ألميريا"],
    "ITCVV": ["Civitavecchia", "Rome Civitavecchia", "Roma Civitavecchia", "تشيفيتافيكيا"],
}

OPERATOR_ALIASES: dict[str, list[str]] = {
    "AF": ["Algérie Ferries", "Algerie Ferries", "ENTMV", "Algeria Ferries"],
    "CL": ["Corsica Linea", "Corsica linea", "CMN"],
    "GNV": ["GNV", "Grandi Navi Veloci"],
    "BAL": ["Baleària", "Balearia"],
    "NE": ["Nouris Elbahr", "Nouris Elbahr Ferries", "Naouris", "Nouris"],
    "ATM": ["Armas Trasmed", "Trasmed", "Armas Trasmediterránea", "Naviera Armas"],
    "MMC": ["Madar Maritime", "Madar"],
}


def main(source: Path, target: Path) -> None:
    ports = json.loads((source / "ports.json").read_text("utf-8"))
    operators = json.loads((source / "operators.json").read_text("utf-8"))
    routes = json.loads((source / "routes.json").read_text("utf-8"))
    vessels = json.loads((source / "vessels.json").read_text("utf-8"))
    catalog = {
        "ports": [
            {
                "code": p["code"],
                "zone": p["zone"],
                "country": p["country"],
                "names": sorted({*p["name"].values(), *PORT_ALIASES.get(p["code"], [])}),
            }
            for p in ports
        ],
        "operators": [
            {
                "code": o["code"],
                "name": o["name"],
                "active": o.get("active", True),
                "names": sorted({o["name"], *OPERATOR_ALIASES.get(o["code"], [])}),
            }
            for o in operators
        ],
        "routes": [{"id": r["id"], "operator": r["operator"], "from": r["from"], "to": r["to"]} for r in routes],
        "vessels": [
            {"code": v["code"], "name": v["name"], "operator": v["operator"], "mmsi": v.get("mmsi")} for v in vessels
        ],
    }
    target.write_text(json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", "utf-8")
    counts = {key: len(catalog[key]) for key in ("ports", "routes", "vessels")}
    print(f"wrote {target}: {counts}")


if __name__ == "__main__":
    src = Path(sys.argv[1] if len(sys.argv) > 1 else "../backend/src/main/resources/catalog")
    main(src, Path(__file__).resolve().parents[1] / "wave_scraper" / "data" / "catalog.json")
