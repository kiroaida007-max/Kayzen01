package dz.wave.live

import dz.wave.catalog.CatalogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps the latest position of every ship and broadcasts changes. Each WebSocket subscriber collects
 * the shared flow independently; slow clients only drop old frames, they never block the ticker.
 */
class LiveHub(
    private val catalog: CatalogStore,
    private val tracker: VesselTracker,
    private val clock: Clock,
    private val tick: Duration = Duration.ofSeconds(5),
    private val aisMaxAge: Duration = Duration.ofMinutes(20),
) {
    private val fixes = ConcurrentHashMap<String, AisFix>()
    private val latest = AtomicReference<List<VesselPosition>>(emptyList())
    private val updates = MutableSharedFlow<List<VesselPosition>>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val flow: SharedFlow<List<VesselPosition>> get() = updates.asSharedFlow()

    fun snapshot(): List<VesselPosition> = latest.get().ifEmpty { recompute() }

    fun accept(fix: AisFix) {
        val previous = fixes[fix.mmsi]
        if (previous == null || !fix.timestamp.isBefore(previous.timestamp)) fixes[fix.mmsi] = fix
    }

    fun recompute(): List<VesselPosition> {
        val now = clock.instant()
        val positions = tracker.merge(tracker.estimate(catalog.current(), now), fixes, now, aisMaxAge)
        latest.set(positions)
        return positions
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            updates.tryEmit(recompute())
            delay(tick.toMillis())
        }
    }
}
