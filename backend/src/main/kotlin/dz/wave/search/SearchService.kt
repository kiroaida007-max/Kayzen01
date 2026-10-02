package dz.wave.search

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dz.wave.catalog.CatalogSnapshot
import dz.wave.catalog.CatalogStore
import dz.wave.common.AppJson
import dz.wave.common.ValidationException
import dz.wave.currency.CurrencyService
import dz.wave.currency.RateSnapshot
import dz.wave.domain.AvailabilitySummary
import dz.wave.domain.CurrencyCode
import dz.wave.domain.DayPrice
import dz.wave.domain.LegResults
import dz.wave.domain.Money
import dz.wave.domain.OperatorRef
import dz.wave.domain.PortRef
import dz.wave.domain.PriceLine
import dz.wave.domain.PriceSource
import dz.wave.domain.RuleSeverity
import dz.wave.domain.SailingOffer
import dz.wave.domain.SearchRequest
import dz.wave.domain.SearchResponse
import dz.wave.domain.SortOrder
import dz.wave.domain.TariffOffer
import dz.wave.domain.TripType
import dz.wave.domain.VesselRef
import dz.wave.domain.Violation
import dz.wave.pricing.Labels
import dz.wave.rules.Messages
import dz.wave.rules.SearchValidator
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.HexFormat

/**
 * The search engine: validates the request, evaluates every candidate sailing against the rules,
 * prices every open tariff and converts to the traveller's currency. Pure in-memory work on an
 * immutable catalog snapshot, with a short-lived result cache to absorb identical searches.
 */
class SearchService(
    private val catalog: CatalogStore,
    private val evaluator: LegEvaluator,
    private val currency: CurrencyService,
    private val clock: Clock,
    cacheSize: Long = 20_000,
    cacheTtl: Duration = Duration.ofSeconds(60),
) {
    private val cache: Cache<String, SearchResponse> = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .expireAfterWrite(cacheTtl)
        .recordStats()
        .build()

    fun search(req: SearchRequest): SearchResponse {
        val snap = catalog.current()
        val today = LocalDate.now(clock)
        val violations = SearchValidator.validate(req, snap, today)
        val errors = violations.filter { it.severity == RuleSeverity.ERROR }
        if (errors.isNotEmpty()) throw ValidationException(errors)
        val rates = currency.current()
        val key = cacheKey(req, snap.version, rates.version)
        return cache.get(key) { compute(req, snap, rates, it) }
    }

    fun cacheHitRate(): Double = cache.stats().hitRate()

    private val calendarCache: Cache<String, List<DayPrice>> = Caffeine.newBuilder()
        .maximumSize(cacheSize / 4)
        .expireAfterWrite(cacheTtl.multipliedBy(5))
        .build()

    /** Cheapest price per day of a month, for the date picker ("calendrier des prix"). */
    fun calendar(base: SearchRequest, month: YearMonth): List<DayPrice> {
        val snap = catalog.current()
        val today = LocalDate.now(clock)
        val first = month.atDay(1)
        val probe = base.copy(
            tripType = TripType.ONE_WAY,
            returnDate = null,
            departureDate = if (first.isBefore(today)) today else first,
        )
        val errors = SearchValidator.validate(probe, snap, today).filter { it.severity == RuleSeverity.ERROR }
        if (errors.isNotEmpty()) throw ValidationException(errors)
        val rates = currency.current()
        val key = cacheKey(probe, snap.version, rates.version) + month
        return calendarCache.get(key) {
            val legRequest = LegRequest(
                passengers = probe.passengers,
                vehicle = probe.vehicle,
                accommodation = probe.accommodation,
                pets = probe.pets,
                accessibility = probe.accessibility,
                lang = probe.lang,
                roundTrip = false,
            )
            val routes = snap.routesByPair[probe.from to probe.to].orEmpty()
                .filter { probe.operators == null || it.operator in probe.operators }
            (1..month.lengthOfMonth()).map { d ->
                val day = month.atDay(d)
                if (day.isBefore(today)) {
                    DayPrice(day, null, 0)
                } else {
                    val sailings = routes.flatMap { snap.sailings(it.id, day, day) }
                    val prices = sailings.mapNotNull { cheapest(snap, it, legRequest, probe.currency, rates) }
                    DayPrice(day, prices.minByOrNull { it.minor }, sailings.size)
                }
            }
        }
    }

    private fun compute(req: SearchRequest, snap: CatalogSnapshot, rates: RateSnapshot, key: String): SearchResponse {
        val roundTrip = req.tripType == TripType.ROUND_TRIP
        val legRequest = LegRequest(
            passengers = req.passengers,
            vehicle = req.vehicle,
            accommodation = req.accommodation,
            pets = req.pets,
            accessibility = req.accessibility,
            lang = req.lang,
            roundTrip = roundTrip,
        )
        val outbound = leg(snap, req, req.from, req.to, req.departureDate, legRequest, rates)
        val inbound = if (roundTrip && req.returnDate != null) {
            leg(snap, req, req.to, req.from, req.returnDate, legRequest, rates)
        } else {
            null
        }
        val notices = SearchValidator.notices(req).toMutableList()
        notices += scheduleNotices(snap, req, req.from, req.to, req.departureDate, outbound)
        if (inbound != null && req.returnDate != null) {
            notices += scheduleNotices(snap, req, req.to, req.from, req.returnDate, inbound)
        }
        val anyReference = (outbound.offers + inbound?.offers.orEmpty()).any { it.priceSource == PriceSource.REFERENCE }
        if (anyReference) notices += Messages.violation("PRICE_REFERENCE", RuleSeverity.INFO, req.lang)
        return SearchResponse(
            searchId = key.take(16),
            currency = req.currency,
            outbound = outbound,
            inbound = inbound,
            notices = notices.distinctBy { it.code + it.message },
            rates = rates.info(),
            generatedAt = clock.instant(),
        )
    }

    private fun leg(
        snap: CatalogSnapshot,
        req: SearchRequest,
        from: String,
        to: String,
        date: LocalDate,
        legRequest: LegRequest,
        rates: RateSnapshot,
    ): LegResults {
        val routes = snap.routesByPair[from to to].orEmpty()
            .filter { req.operators == null || it.operator in req.operators }
        val today = LocalDate.now(clock)
        val windowStart = maxOf(date.minusDays(req.flexDays.toLong()), today)
        val windowEnd = date.plusDays(req.flexDays.toLong())
        val sailings = routes.flatMap { snap.sailings(it.id, windowStart, windowEnd) }
        val offers = sailings.map { offer(snap, it, legRequest, req.currency, rates) }
        val sorted = sort(offers, req.sort)

        val nearbyStart = maxOf(date.minusDays(3), today)
        val nearby = generateSequence(nearbyStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(date.plusDays(3)) }
            .map { day ->
                val daySailings = routes.flatMap { snap.sailings(it.id, day, day) }
                val prices = daySailings.mapNotNull { cheapest(snap, it, legRequest, req.currency, rates) }
                DayPrice(day, prices.minByOrNull { it.minor }, daySailings.size)
            }
            .toList()
        return LegResults(date, sorted, nearby)
    }

    private fun sort(offers: List<SailingOffer>, order: SortOrder): List<SailingOffer> {
        val byPrice = compareBy<SailingOffer> { it.cheapest?.total?.minor ?: Long.MAX_VALUE }
        val byDeparture = compareBy<SailingOffer> { it.departure.toInstant() }
        val byDuration = compareBy<SailingOffer> { it.durationMin }
        val secondary = when (order) {
            SortOrder.PRICE -> byPrice.then(byDeparture)
            SortOrder.DEPARTURE -> byDeparture.then(byPrice)
            SortOrder.DURATION -> byDuration.then(byPrice)
        }
        return offers.sortedWith(compareBy<SailingOffer> { !it.bookable }.then(secondary))
    }

    private fun cheapest(
        snap: CatalogSnapshot,
        sailing: dz.wave.domain.Sailing,
        legRequest: LegRequest,
        display: CurrencyCode,
        rates: RateSnapshot,
    ): Money? {
        val eval = evaluator.evaluate(snap, sailing, legRequest)
        if (!eval.bookable) return null
        return evaluator.priceTariffs(eval)
            .map { convertLines(it.nativeLines, display, rates).let { lines -> Money.sum(lines.map { l -> l.total }, display) } }
            .minByOrNull { it.minor }
    }

    fun offer(
        snap: CatalogSnapshot,
        sailing: dz.wave.domain.Sailing,
        legRequest: LegRequest,
        display: CurrencyCode,
        rates: RateSnapshot,
    ): SailingOffer {
        val lang = legRequest.lang
        val eval = evaluator.evaluate(snap, sailing, legRequest)
        val priced = if (eval.bookable) evaluator.priceTariffs(eval) else emptyList()
        val tariffOffers = priced.map { p ->
            val lines = convertLines(p.nativeLines, display, rates)
            TariffOffer(
                code = p.tariff.code,
                name = p.tariff.name.get(lang),
                total = Money.sum(lines.map { it.total }, display),
                totalNative = p.nativeTotal,
                refundable = p.tariff.refundable,
                modifiable = p.tariff.modifiable,
                conditions = Labels.policy(p.tariff, lang),
            ) to lines
        }
        val best = tariffOffers.minByOrNull { it.first.total.minor }
        val vessel = eval.vessel
        return SailingOffer(
            sailingId = sailing.id,
            routeId = sailing.routeId,
            operator = OperatorRef(eval.operator.code, eval.operator.name, eval.operator.color),
            vessel = vessel?.let { VesselRef(it.code, it.name, it.amenities, it.verified) },
            from = PortRef(eval.fromPort.code, eval.fromPort.name, eval.fromPort.country),
            to = PortRef(eval.toPort.code, eval.toPort.name, eval.toPort.country),
            departure = sailing.departure,
            arrival = sailing.arrival,
            departureZone = sailing.departure.zone.id,
            arrivalZone = sailing.arrival.zone.id,
            durationMin = sailing.durationMinutes,
            status = sailing.status,
            priceSource = sailing.source,
            lastUpdated = sailing.fetchedAt,
            bookable = eval.bookable && best != null,
            reasons = eval.reasons,
            notes = eval.notes,
            availability = AvailabilitySummary(
                level = eval.availability.level,
                seatsLeft = eval.availability.seats.takeIf { it < 50 },
                cabinsLeft = eval.availability.cabinsLeft.takeIf { it < 20 },
                vehicleSpaceLeftM = eval.availability.laneMeters.takeIf { it < 200 },
            ),
            accommodation = legRequest.accommodation,
            cabins = eval.cabins,
            cheapest = best?.first,
            tariffs = tariffOffers.map { it.first },
            breakdown = best?.second.orEmpty(),
            promotions = priced.mapNotNull { it.promotion?.id }.distinct(),
        )
    }

    fun convertLines(lines: List<PriceLine>, to: CurrencyCode, rates: RateSnapshot): List<PriceLine> =
        lines.map { line ->
            val unit = currency.convert(line.unit, to, rates)
            line.copy(unit = unit, total = unit.times(line.quantity))
        }

    private fun scheduleNotices(
        snap: CatalogSnapshot,
        req: SearchRequest,
        from: String,
        to: String,
        date: LocalDate,
        results: LegResults,
    ): List<Violation> {
        if (results.offers.isNotEmpty()) return emptyList()
        val lang = req.lang
        val routes = snap.routesByPair[from to to].orEmpty()
        val out = mutableListOf<Violation>()
        routes.mapNotNull { snap.operators[it.operator] }.distinct().forEach { op ->
            val until = op.publishedUntil
            if (until != null && date.isAfter(until)) {
                out += Messages.violation(
                    "SCHEDULE_NOT_PUBLISHED", RuleSeverity.INFO, lang,
                    params = mapOf("operator" to op.name, "date" to until.format(DATE_FR)),
                )
            }
        }
        val next = routes.mapNotNull { snap.nextDeparture(it.id, date.plusDays(1)) }.minOrNull()
        if (next != null) {
            out += Messages.violation("NO_SAILING_ON_DATE", RuleSeverity.INFO, lang, params = mapOf("date" to next.format(DATE_FR), "iso" to next.toString()))
        }
        return out
    }

    private fun cacheKey(req: SearchRequest, catalogVersion: Long, ratesVersion: Long): String {
        val bucket = clock.millis() / CACHE_BUCKET_MS
        val canonical = AppJson.encodeToString(SearchRequest.serializer(), req) + "|$catalogVersion|$ratesVersion|$bucket"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return HexFormat.of().formatHex(digest)
    }

    companion object {
        private const val CACHE_BUCKET_MS = 5 * 60 * 1000L
        private val DATE_FR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
