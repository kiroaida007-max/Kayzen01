package dz.wave.booking

import dz.wave.catalog.CatalogStore
import dz.wave.common.ValidationException
import dz.wave.currency.CurrencyService
import dz.wave.currency.RateSnapshot
import dz.wave.domain.BookingSelection
import dz.wave.domain.CurrencyCode
import dz.wave.domain.LegQuote
import dz.wave.domain.LegSelection
import dz.wave.domain.Money
import dz.wave.domain.PriceLine
import dz.wave.domain.PriceLineKind
import dz.wave.domain.Quote
import dz.wave.domain.RuleSeverity
import dz.wave.domain.SearchRequest
import dz.wave.domain.TripType
import dz.wave.domain.Violation
import dz.wave.pricing.Labels
import dz.wave.pricing.PricingEngine
import dz.wave.pricing.TariffAvailability
import dz.wave.rules.Messages
import dz.wave.rules.SearchValidator
import dz.wave.search.LegEvaluation
import dz.wave.search.LegEvaluator
import dz.wave.search.LegRequest
import dz.wave.search.PricedTariff
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

/** A quote plus what must be held in inventory to honour it. */
data class QuoteResult(
    val quote: Quote,
    val holds: List<HoldRequest>,
    val payable: Map<CurrencyCode, Money>,
)

/**
 * Re-prices a traveller's selection from scratch on the server. Prices shown by clients are never
 * trusted: the booking is created only with what this service computes.
 */
class QuoteService(
    private val catalog: CatalogStore,
    private val evaluator: LegEvaluator,
    private val currency: CurrencyService,
    private val clock: Clock,
    private val serviceFeeDzd: Long = 0,
) {
    fun quote(selection: BookingSelection): QuoteResult {
        val snap = catalog.current()
        val lang = selection.lang
        val rates = currency.current()
        val outboundSailing = snap.sailingsById[selection.outbound.sailingId]
            ?: throw ValidationException(listOf(Messages.violation("SAILING_NOT_FOUND", RuleSeverity.ERROR, lang, "outbound")))
        val inboundSailing = selection.inbound?.let {
            snap.sailingsById[it.sailingId]
                ?: throw ValidationException(listOf(Messages.violation("SAILING_NOT_FOUND", RuleSeverity.ERROR, lang, "inbound")))
        }

        val request = SearchRequest(
            tripType = if (inboundSailing != null) TripType.ROUND_TRIP else TripType.ONE_WAY,
            from = outboundSailing.from,
            to = outboundSailing.to,
            departureDate = outboundSailing.departureDate,
            returnDate = inboundSailing?.departureDate,
            passengers = selection.passengers,
            vehicle = selection.vehicle,
            accommodation = selection.outbound.accommodation,
            pets = selection.pets,
            accessibility = selection.accessibility,
            currency = selection.currency,
            lang = lang,
        )
        val problems = SearchValidator.validate(request, snap, LocalDate.now(clock))
            .filter { it.severity == RuleSeverity.ERROR }.toMutableList()
        if (inboundSailing != null) {
            if (inboundSailing.from != outboundSailing.to || inboundSailing.to != outboundSailing.from) {
                problems += Messages.violation("LEG_ROUTE_MISMATCH", RuleSeverity.ERROR, lang, "inbound")
            }
            if (!inboundSailing.departure.toInstant().isAfter(outboundSailing.arrival.toInstant().plusSeconds(3600))) {
                problems += Messages.violation("LEG_ORDER_INVALID", RuleSeverity.ERROR, lang, "inbound")
            }
        }
        if (problems.isNotEmpty()) throw ValidationException(problems)

        val legRequest = { sel: LegSelection ->
            LegRequest(
                passengers = selection.passengers,
                vehicle = selection.vehicle,
                accommodation = sel.accommodation,
                pets = selection.pets,
                accessibility = selection.accessibility,
                lang = lang,
                roundTrip = inboundSailing != null,
            )
        }
        val legs = buildList {
            add(priceLeg(snap.let { evaluator.evaluate(it, outboundSailing, legRequest(selection.outbound)) }, selection.outbound, "outbound"))
            if (inboundSailing != null && selection.inbound != null) {
                add(priceLeg(evaluator.evaluate(snap, inboundSailing, legRequest(selection.inbound)), selection.inbound, "inbound"))
            }
        }

        val display = selection.currency
        val legQuotes = legs.map { (eval, priced) -> toLegQuote(eval, priced, display, rates) }
        val adjustmentsNative = adjustments(legs, lang)
        val adjustments = adjustmentsNative.map { convertLine(it, display, rates) }
        val total = Money.sum(legQuotes.map { it.total } + adjustments.map { it.total }, display)

        val payable = CurrencyCode.entries.associateWith { c ->
            val legTotals = legs.map { (_, priced) -> Money.sum(priced.nativeLines.map { convertLine(it, c, rates).total }, c) }
            Money.sum(legTotals + adjustmentsNative.map { convertLine(it, c, rates).total }, c)
        }

        val notices = SearchValidator.notices(request) + legs.flatMap { it.first.notes }.distinctBy { it.code }
        val quote = Quote(
            legs = legQuotes,
            adjustments = adjustments,
            total = total,
            currency = display,
            rates = rates.info(),
            promotions = legs.mapNotNull { it.second.promotion?.id }.distinct(),
            notices = notices,
            createdAt = clock.instant(),
            payable = payable,
        )
        val holds = legs.map { (eval, _) -> HoldRequest(eval.sailing.id, eval.need, eval.limits.toResourceLimits()) }
        return QuoteResult(quote, holds, payable)
    }

    private fun priceLeg(eval: LegEvaluation, sel: LegSelection, field: String): Pair<LegEvaluation, PricedTariff> {
        val lang = eval.request.lang
        if (!eval.bookable) throw ValidationException(eval.reasons.map { it.copy(field = field) })
        val tariff = eval.operator.rules.tariffs.firstOrNull { it.code == sel.tariff }
        val open = tariff != null &&
            TariffAvailability.isOpen(tariff, eval.sailing.departureDate, LocalDate.now(clock), eval.availability.loadFactor)
        if (tariff == null || !open) {
            throw ValidationException(listOf(Messages.violation("TARIFF_UNAVAILABLE", RuleSeverity.ERROR, lang, "$field.tariff")))
        }
        return eval to evaluator.price(eval, tariff)
    }

    private fun adjustments(legs: List<Pair<LegEvaluation, PricedTariff>>, lang: dz.wave.domain.Lang): List<PriceLine> {
        val out = mutableListOf<PriceLine>()
        if (legs.size == 2 && legs[0].first.operator.code == legs[1].first.operator.code) {
            val rate = legs[0].first.operator.rules.roundTripDiscount
            if (rate > 0) {
                legs.forEach { (eval, priced) ->
                    val currency = eval.fareTable.currency
                    val base = PricingEngine.discountableBase(priced.nativeLines, currency)
                    val discount = base.scale(BigDecimal.valueOf(rate)).roundedForSale()
                    if (!discount.isZero) out += PricingEngine.line(PriceLineKind.DISCOUNT, Labels.roundTrip(lang), 1, -discount)
                }
            }
        }
        if (serviceFeeDzd > 0) {
            out += PricingEngine.line(PriceLineKind.FEE, Labels.serviceFee(lang), 1, Money(serviceFeeDzd * 100, CurrencyCode.DZD))
        }
        return out
    }

    private fun toLegQuote(eval: LegEvaluation, priced: PricedTariff, display: CurrencyCode, rates: RateSnapshot): LegQuote {
        val lines = priced.nativeLines.map { convertLine(it, display, rates) }
        return LegQuote(
            sailingId = eval.sailing.id,
            routeId = eval.route.id,
            operator = eval.operator.code,
            vessel = eval.vessel?.code,
            from = eval.fromPort.code,
            to = eval.toPort.code,
            departure = eval.sailing.departure,
            arrival = eval.sailing.arrival,
            tariff = priced.tariff,
            accommodation = eval.request.accommodation,
            cabins = eval.cabins,
            lines = lines,
            nativeTotal = priced.nativeTotal,
            total = Money.sum(lines.map { it.total }, display),
            priceSource = eval.sailing.source,
        )
    }

    private fun convertLine(line: PriceLine, to: CurrencyCode, rates: RateSnapshot): PriceLine {
        val unit = currency.convert(line.unit, to, rates)
        return line.copy(unit = unit, total = unit.times(line.quantity))
    }

    companion object {
        fun warnings(violations: List<Violation>) = violations.filter { it.severity != RuleSeverity.ERROR }
    }
}
