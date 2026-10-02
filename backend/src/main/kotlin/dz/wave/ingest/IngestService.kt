@file:UseSerializers(InstantSerializer::class)

package dz.wave.ingest

import dz.wave.catalog.CatalogStore
import dz.wave.catalog.ScheduleGenerator
import dz.wave.common.AppJson
import dz.wave.currency.CurrencyService
import dz.wave.domain.AccommodationType
import dz.wave.domain.CurrencyCode
import dz.wave.domain.InstantSerializer
import dz.wave.domain.LiveAvailability
import dz.wave.domain.LivePrices
import dz.wave.domain.Money
import dz.wave.domain.PriceSource
import dz.wave.domain.Sailing
import dz.wave.domain.SailingStatus
import dz.wave.domain.VehicleCategory
import dz.wave.infra.Database
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.Normalizer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class IngestPrice(val category: String, val amount: Double, val currency: CurrencyCode)

/** One crossing as observed by a scraper or an operator API, in the ports' local times. */
@Serializable
data class IngestSailing(
    val operator: String,
    val from: String,
    val to: String,
    val departureLocal: String,
    val arrivalLocal: String? = null,
    val durationMin: Int? = null,
    val vessel: String? = null,
    val status: SailingStatus = SailingStatus.SCHEDULED,
    val delayMin: Int = 0,
    val prices: List<IngestPrice> = emptyList(),
    val availability: LiveAvailability? = null,
    val sourceUrl: String? = null,
)

@Serializable
data class IngestBatch(val source: String, val fetchedAt: Instant, val sailings: List<IngestSailing>)

@Serializable
data class IngestResult(val accepted: Int, val rejected: List<String>, val catalogVersion: Long)

@Serializable
data class IngestRates(val rates: Map<CurrencyCode, String>, val asOf: Instant, val source: String)

/** Where LIVE sailings are persisted so every pod (and restarts) see the same overlay. */
interface OverlayRepository {
    suspend fun upsert(sailings: List<Sailing>)
    suspend fun loadFrom(date: LocalDate): List<Sailing>
}

class InMemoryOverlayRepository : OverlayRepository {
    private val rows = ConcurrentHashMap<String, Sailing>()
    override suspend fun upsert(sailings: List<Sailing>) = sailings.forEach { rows[it.id] = it }
    override suspend fun loadFrom(date: LocalDate) = rows.values.filter { !it.departureDate.isBefore(date) }
}

class PostgresOverlayRepository(private val db: Database) : OverlayRepository {
    override suspend fun upsert(sailings: List<Sailing>) = db.tx { conn ->
        conn.prepareStatement(
            """
            INSERT INTO sailing_overrides (id, operator, route_id, departure, data, fetched_at, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, now())
            ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data, fetched_at = EXCLUDED.fetched_at, updated_at = now()
            """.trimIndent(),
        ).use { st ->
            sailings.forEach { s ->
                st.setString(1, s.id)
                st.setString(2, s.operator)
                st.setString(3, s.routeId)
                st.setTimestamp(4, Timestamp.from(s.departure.toInstant()))
                st.setString(5, AppJson.encodeToString(Sailing.serializer(), s))
                st.setTimestamp(6, s.fetchedAt?.let(Timestamp::from))
                st.addBatch()
            }
            st.executeBatch()
        }
        Unit
    }

    override suspend fun loadFrom(date: LocalDate): List<Sailing> = db.read { conn ->
        conn.prepareStatement("SELECT data FROM sailing_overrides WHERE departure >= ?").use { st ->
            st.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()))
            st.executeQuery().use { rs ->
                generateSequence { if (rs.next()) AppJson.decodeFromString(Sailing.serializer(), rs.getString(1)) else null }.toList()
            }
        }
    }
}

/**
 * Validates and normalises observations from the scraping pipeline, then overlays them on the
 * reference schedule. Anything implausible is rejected with a reason instead of polluting search.
 */
class IngestService(
    private val catalog: CatalogStore,
    private val overlay: OverlayRepository,
    private val currency: CurrencyService,
    private val clock: Clock,
    private val onCatalogChanged: suspend (Long) -> Unit = {},
) {
    private val log = LoggerFactory.getLogger(IngestService::class.java)

    suspend fun ingest(batch: IngestBatch): IngestResult {
        val snap = catalog.current()
        val accepted = mutableListOf<Sailing>()
        val rejected = mutableListOf<String>()
        val today = LocalDate.now(clock)
        batch.sailings.forEachIndexed { index, s ->
            runCatching { normalise(s, batch, today) }
                .onSuccess { accepted += it }
                .onFailure { rejected += "#$index ${s.operator} ${s.from}->${s.to} ${s.departureLocal}: ${it.message}" }
        }
        if (accepted.isNotEmpty()) {
            overlay.upsert(accepted)
            catalog.upsertLive(accepted)
            onCatalogChanged(catalog.current().version)
        }
        log.info("Ingest from {}: {} accepted, {} rejected", batch.source, accepted.size, rejected.size)
        return IngestResult(accepted.size, rejected.take(50), catalog.current().version.takeIf { accepted.isNotEmpty() } ?: snap.version)
    }

    suspend fun reloadOverlay() {
        catalog.replaceOverlay(overlay.loadFrom(LocalDate.now(clock).minusDays(2)))
    }

    fun ingestRates(rates: IngestRates) {
        currency.update(rates.rates.mapValues { BigDecimal(it.value) }, rates.asOf, rates.source)
    }

    private fun normalise(s: IngestSailing, batch: IngestBatch, today: LocalDate): Sailing {
        val snap = catalog.current()
        val operator = snap.operators[s.operator] ?: error("unknown operator")
        require(operator.active) { "operator inactive" }
        val fromPort = snap.ports[s.from] ?: error("unknown port ${s.from}")
        val toPort = snap.ports[s.to] ?: error("unknown port ${s.to}")
        val route = snap.data.routes.firstOrNull { it.operator == operator.code && it.from == s.from && it.to == s.to }
            ?: error("no such route for operator")
        val departure = LocalDateTime.parse(s.departureLocal).atZone(fromPort.zoneId)
        require(!departure.toLocalDate().isBefore(today.minusDays(2))) { "departure too old" }
        require(!departure.toLocalDate().isAfter(today.plusDays(CatalogStore.DEFAULT_HORIZON_DAYS))) { "departure too far" }
        val arrival = when {
            s.arrivalLocal != null -> LocalDateTime.parse(s.arrivalLocal).atZone(toPort.zoneId)
            s.durationMin != null -> departure.plusMinutes(s.durationMin.toLong()).withZoneSameInstant(toPort.zoneId)
            else -> error("missing arrival or duration")
        }
        val duration = Duration.between(departure, arrival)
        require(duration >= Duration.ofHours(2) && duration <= Duration.ofHours(72)) { "implausible duration $duration" }
        require(s.delayMin in 0..(48 * 60)) { "implausible delay" }

        val table = snap.fareTables.getValue(route.id)
        val livePrices = s.prices.takeIf { it.isNotEmpty() }?.let { prices ->
            fun native(p: IngestPrice): Double {
                require(p.amount > 0 && p.amount.isFinite()) { "bad price ${p.amount}" }
                val converted = currency.convert(Money.of(p.amount, p.currency), table.currency)
                val eur = currency.convert(converted, CurrencyCode.EUR)
                require(eur.toDecimal() < BigDecimal(20_000)) { "price too high" }
                return converted.toDecimal().toDouble()
            }
            LivePrices(
                currency = table.currency,
                adultSeat = prices.firstOrNull { it.category == "ADULT_SEAT" }?.let(::native),
                accommodation = prices.mapNotNull { p ->
                    runCatching { AccommodationType.valueOf(p.category) }.getOrNull()?.let { it to native(p) }
                }.toMap(),
                vehicles = prices.mapNotNull { p ->
                    p.category.removePrefix("VEHICLE_").takeIf { p.category.startsWith("VEHICLE_") }
                        ?.let { runCatching { VehicleCategory.valueOf(it) }.getOrNull() }?.let { it to native(p) }
                }.toMap(),
            )
        }
        return Sailing(
            id = ScheduleGenerator.sailingId(route.id, departure),
            routeId = route.id,
            operator = operator.code,
            vessel = s.vessel?.let { matchVessel(operator.code, it) },
            from = s.from,
            to = s.to,
            departure = departure,
            arrival = arrival,
            status = s.status,
            delayMin = s.delayMin,
            source = PriceSource.LIVE,
            fetchedAt = batch.fetchedAt,
            livePrices = livePrices,
            liveAvailability = s.availability,
        )
    }

    /** Scrapers see names like "GNV Fantastic" or "BADJI MOKHTAR 3"; match them to catalog codes. */
    private fun matchVessel(operator: String, raw: String): String? {
        val wanted = normaliseName(raw)
        return catalog.catalogData.vessels.filter { it.operator == operator }.firstOrNull { v ->
            val name = normaliseName(v.name)
            name == wanted || wanted.endsWith(name) || name.endsWith(wanted) || v.code.equals(raw, ignoreCase = true)
        }?.code
    }

    private fun normaliseName(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .uppercase()
            .replace(Regex("^(GNV|M/?V|M/?F|MS)\\s+"), "")
            .replace(Regex("\\b3\\b"), "III")
            .replace(Regex("\\b2\\b"), "II")
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
