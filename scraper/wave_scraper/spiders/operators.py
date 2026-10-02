"""One spider per company; the behaviour lives in OperatorSpider and the URLs in sources.yaml."""

from __future__ import annotations

from .base import OperatorSpider


class AlgerieFerriesSpider(OperatorSpider):
    name = "algerie_ferries"
    operator = "AF"


class CorsicaLineaSpider(OperatorSpider):
    name = "corsica_linea"
    operator = "CL"


class BaleariaSpider(OperatorSpider):
    name = "balearia"
    operator = "BAL"


class GnvSpider(OperatorSpider):
    name = "gnv"
    operator = "GNV"


class NourisElbahrSpider(OperatorSpider):
    name = "nouris_elbahr"
    operator = "NE"


class ArmasTrasmedSpider(OperatorSpider):
    name = "armas_trasmed"
    operator = "ATM"


OPERATOR_SPIDERS: dict[str, type[OperatorSpider]] = {
    cls.operator: cls
    for cls in (
        AlgerieFerriesSpider,
        CorsicaLineaSpider,
        BaleariaSpider,
        GnvSpider,
        NourisElbahrSpider,
        ArmasTrasmedSpider,
    )
}
