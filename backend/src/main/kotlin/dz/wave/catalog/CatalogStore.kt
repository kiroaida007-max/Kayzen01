package dz.wave.catalog

import dz.wave.domain.Sailing
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Holds the current [CatalogSnapshot]: reference sailings generated from rotations, overlaid with
 * LIVE sailings from the ingestion pipeline. Readers never lock; writers rebuild and swap.
 */
class CatalogStore(
    private val data: CatalogData,
    private val clock: Clock,
    private val horizonDays: Long = DEFAULT_HORIZON_DAYS,
) {
    private val log = LoggerFactory.getLogger(CatalogStore::class.java)
    private val fareTables = FareTables.resolve(data.fareTables)
    private val generator = ScheduleGenerator(data)
    private val overlay = ConcurrentHashMap<String, Sailing>()
    private val versions = AtomicLong(0)
    private val ref = AtomicReference<CatalogSnapshot>()
    private val listeners = CopyOnWriteArrayList<(CatalogSnapshot) -> Unit>()

    @Volatile private var referenceSailings: List<Sailing> = emptyList()

    @Volatile private var generatedFor: LocalDate? = null

    init {
        rebuild(force = true)
    }

    val catalogData: CatalogData get() = data

    fun current(): CatalogSnapshot {
        if (generatedFor != LocalDate.now(clock)) rebuild()
        return ref.get()
    }

    fun onChange(listener: (CatalogSnapshot) -> Unit) {
        listeners += listener
    }

    @Synchronized
    fun rebuild(force: Boolean = false) {
        val today = LocalDate.now(clock)
        if (force || generatedFor != today) {
            referenceSailings = generator.generate(today.minusDays(2), today.plusDays(horizonDays))
            generatedFor = today
        }
        publish()
    }

    /** Adds or replaces LIVE sailings; returns how many were applied. */
    fun upsertLive(sailings: Collection<Sailing>): Int {
        if (sailings.isEmpty()) return 0
        sailings.forEach { overlay[it.id] = it }
        publish()
        return sailings.size
    }

    /** Replaces the whole overlay, e.g. after another pod announced a new catalog version. */
    fun replaceOverlay(sailings: Collection<Sailing>) {
        overlay.clear()
        sailings.forEach { overlay[it.id] = it }
        publish()
    }

    fun overlaySize(): Int = overlay.size

    @Synchronized
    private fun publish() {
        val merged = merge(referenceSailings, overlay.values)
        val snapshot = CatalogSnapshot(versions.incrementAndGet(), data, fareTables, merged, clock.instant())
        ref.set(snapshot)
        log.info("Catalog v{}: {} sailings ({} live)", snapshot.version, snapshot.sailingCount, overlay.size)
        listeners.forEach { listener ->
            runCatching { listener(snapshot) }.onFailure { log.warn("Catalog listener failed", it) }
        }
    }

    companion object {
        const val DEFAULT_HORIZON_DAYS = 400L

        /** A live sailing replaces the reference crossing of the same route within ±3 h. */
        private const val MATCH_WINDOW_MINUTES = 180L

        fun merge(reference: List<Sailing>, live: Collection<Sailing>): List<Sailing> {
            if (live.isEmpty()) return reference
            val liveByRoute = live.groupBy { it.routeId }
            val kept = reference.filter { ref ->
                liveByRoute[ref.routeId]?.none { l ->
                    abs(Duration.between(l.departure, ref.departure).toMinutes()) <= MATCH_WINDOW_MINUTES
                } ?: true
            }
            return kept + live
        }
    }
}
