"""Reference codes shared with the backend (ports, operators, routes, ships)."""

from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from functools import cache
from importlib import resources
from typing import Any


def fold(text: str) -> str:
    """Accent-, case- and punctuation-insensitive form used for every name lookup."""
    decomposed = unicodedata.normalize("NFKD", text)
    stripped = "".join(c for c in decomposed if not unicodedata.combining(c))
    return re.sub(r"[^\w]+", " ", stripped.casefold()).strip()


@dataclass(frozen=True)
class Port:
    code: str
    zone: str
    country: str
    names: tuple[str, ...]


@dataclass(frozen=True)
class Route:
    id: str
    operator: str
    origin: str
    destination: str


@dataclass(frozen=True)
class Vessel:
    code: str
    name: str
    operator: str
    mmsi: str | None


class Catalog:
    def __init__(self, raw: dict[str, Any]) -> None:
        self.ports = {p["code"]: Port(p["code"], p["zone"], p["country"], tuple(p["names"])) for p in raw["ports"]}
        self.operators: dict[str, dict[str, Any]] = {o["code"]: o for o in raw["operators"]}
        self.routes = [Route(r["id"], r["operator"], r["from"], r["to"]) for r in raw["routes"]]
        self.vessels = [Vessel(v["code"], v["name"], v["operator"], v.get("mmsi")) for v in raw["vessels"]]
        self._port_names: list[tuple[str, str]] = sorted(
            ((fold(name), p.code) for p in self.ports.values() for name in (*p.names, p.code)),
            key=lambda item: -len(item[0]),
        )
        self._operator_names = {fold(n): code for code, o in self.operators.items() for n in (*o["names"], code)}

    def port_code(self, text: str | None) -> str | None:
        """Exact match first ("Alger", "DZALG"), then the longest port name contained in the text
        ("Port de Marseille (Cap Janet)")."""
        if not text:
            return None
        folded = fold(text)
        for name, code in self._port_names:
            if folded == name:
                return code
        padded = f" {folded} "
        for name, code in self._port_names:
            if len(name) >= 4 and f" {name} " in padded:
                return code
        return None

    def ports_in(self, text: str) -> list[str]:
        """Ports named in a URL slug or title, in order of appearance ("bateau-valence-mostaganem")."""
        folded = f" {fold(text.replace('-', ' ').replace('_', ' ').replace('/', ' '))} "
        found: list[tuple[int, str]] = []
        for name, code in self._port_names:
            if len(name) < 4:
                continue
            index = folded.find(f" {name} ")
            if index >= 0 and code not in (c for _, c in found):
                found.append((index, code))
        return [code for _, code in sorted(found)]

    def operator_code(self, text: str | None) -> str | None:
        if not text:
            return None
        folded = fold(text)
        if folded in self._operator_names:
            return self._operator_names[folded]
        for name, code in sorted(self._operator_names.items(), key=lambda item: -len(item[0])):
            if len(name) >= 3 and f" {name} " in f" {folded} ":
                return code
        return None

    def route(self, operator: str, origin: str, destination: str) -> Route | None:
        return next(
            (r for r in self.routes if r.operator == operator and r.origin == origin and r.destination == destination),
            None,
        )

    def routes_of(self, operator: str) -> list[Route]:
        return [r for r in self.routes if r.operator == operator]

    def vessel_by_mmsi(self, mmsi: str) -> Vessel | None:
        return next((v for v in self.vessels if v.mmsi == mmsi), None)

    @property
    def tracked_mmsis(self) -> set[str]:
        return {v.mmsi for v in self.vessels if v.mmsi}


@cache
def catalog() -> Catalog:
    data = resources.files("wave_scraper").joinpath("data/catalog.json").read_text("utf-8")
    return Catalog(json.loads(data))
