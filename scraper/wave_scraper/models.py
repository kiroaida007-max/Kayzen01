"""Payloads of the backend ingestion API (`/internal/v1/ingest/*`), validated before they leave."""

from __future__ import annotations

import math
import re
from datetime import UTC, datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_serializer, field_validator, model_validator

Currency = Literal["DZD", "EUR", "USD"]
Status = Literal["SCHEDULED", "DELAYED", "CANCELLED", "BOARDING", "DEPARTED", "ARRIVED"]
Level = Literal["HIGH", "MEDIUM", "LOW", "SOLD_OUT", "UNKNOWN"]

ACCOMMODATIONS = {"SEAT", "CABIN_INT_2", "CABIN_INT_4", "CABIN_EXT_2", "CABIN_EXT_4", "SUITE", "CABIN_PET", "CABIN_PMR"}
VEHICLES = {"CAR", "CAR_HIGH", "MOTORCYCLE", "CAMPER", "CAR_TRAILER", "VAN"}
CATEGORIES = {"ADULT_SEAT", *ACCOMMODATIONS, *(f"VEHICLE_{v}" for v in VEHICLES)}

_LOCAL = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$")


class Price(BaseModel):
    model_config = ConfigDict(frozen=True)

    category: str
    amount: float
    currency: Currency

    @field_validator("category")
    @classmethod
    def _known_category(cls, value: str) -> str:
        if value not in CATEGORIES:
            raise ValueError(f"unknown price category {value}")
        return value

    @field_validator("amount")
    @classmethod
    def _positive(cls, value: float) -> float:
        if not math.isfinite(value) or value <= 0:
            raise ValueError("price must be positive")
        return round(value, 2)


class Availability(BaseModel):
    seats: int | None = Field(default=None, ge=0)
    cabins: dict[str, int] = Field(default_factory=dict)
    laneMeters: int | None = Field(default=None, ge=0)
    level: Level = "UNKNOWN"


class Sailing(BaseModel):
    """One crossing as published by the operator, with times on the ports' own clocks."""

    model_config = ConfigDict(populate_by_name=True)

    operator: str
    origin: str = Field(alias="from")
    destination: str = Field(alias="to")
    departureLocal: str
    arrivalLocal: str | None = None
    durationMin: int | None = Field(default=None, ge=120, le=72 * 60)
    vessel: str | None = None
    status: Status = "SCHEDULED"
    delayMin: int = Field(default=0, ge=0, le=48 * 60)
    prices: list[Price] = Field(default_factory=list)
    availability: Availability | None = None
    sourceUrl: str | None = None

    @field_validator("departureLocal", "arrivalLocal")
    @classmethod
    def _local_format(cls, value: str | None) -> str | None:
        if value is not None and not _LOCAL.match(value):
            raise ValueError("expected yyyy-MM-ddTHH:mm in port local time")
        return value

    @model_validator(mode="after")
    def _arrival_known(self) -> Sailing:
        if self.arrivalLocal is None and self.durationMin is None:
            raise ValueError("arrival time or duration required")
        if self.origin == self.destination:
            raise ValueError("origin and destination are the same port")
        return self

    @property
    def key(self) -> tuple[str, str, str, str]:
        return self.operator, self.origin, self.destination, self.departureLocal

    def merged_with(self, other: Sailing) -> Sailing:
        """Combines two observations of the same crossing (e.g. seat page + cabin page)."""
        prices = {p.category: p for p in self.prices}
        prices.update({p.category: p for p in other.prices})
        data = self.model_dump()
        data.update({k: v for k, v in other.model_dump().items() if v not in (None, [], {}, "SCHEDULED", 0)})
        data["prices"] = list(prices.values())
        return Sailing.model_validate(data)


class Batch(BaseModel):
    source: str
    fetchedAt: datetime
    sailings: list[Sailing]

    @field_serializer("fetchedAt")
    def _instant(self, value: datetime) -> str:
        return value.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")

    def payload(self) -> dict[str, Any]:
        return self.model_dump(mode="json", by_alias=True, exclude_none=True)


class Rates(BaseModel):
    """Dinars per unit, as published by the Banque d'Algérie."""

    rates: dict[Literal["EUR", "USD"], str]
    asOf: datetime
    source: str

    @field_validator("rates")
    @classmethod
    def _plausible(cls, value: dict[str, str]) -> dict[str, str]:
        bounds = {"EUR": (100.0, 400.0), "USD": (80.0, 400.0)}
        for code, rate in value.items():
            low, high = bounds[code]
            if not low <= float(rate) <= high:
                raise ValueError(f"implausible {code} rate {rate}")
        return value

    @field_serializer("asOf")
    def _instant(self, value: datetime) -> str:
        return value.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


class AisFix(BaseModel):
    mmsi: str
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)
    speedKn: float = Field(ge=0, le=60)
    courseDeg: float = Field(ge=0, lt=360)
    heading: float | None = Field(default=None, ge=0, lt=360)
    timestamp: datetime

    @field_serializer("timestamp")
    def _instant(self, value: datetime) -> str:
        return value.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
