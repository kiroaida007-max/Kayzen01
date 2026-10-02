package dz.wave.pricing

import dz.wave.domain.AccommodationType
import dz.wave.domain.AvailabilityLevel
import dz.wave.domain.Direction
import dz.wave.domain.PriceSource
import dz.wave.domain.Sailing
import dz.wave.domain.Vessel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.pow

/** What WAVE itself has already held or sold on a sailing (on top of the operator's own sales). */
data class Consumption(
    val seats: Int = 0,
    val cabins: Map<AccommodationType, Int> = emptyMap(),
    val laneMeters: Int = 0,
    val kennels: Int = 0,
) {
    companion object {
        val NONE = Consumption()
    }
}

data class AvailabilityView(
    val seats: Int,
    val cabins: Map<AccommodationType, Int>,
    val laneMeters: Int,
    val kennels: Int,
    val loadFactor: Double,
    val seatCapacity: Int,
) {
    val cabinsLeft: Int get() = cabins.values.sum()

    val level: AvailabilityLevel
        get() {
            if (seatCapacity <= 0) return AvailabilityLevel.UNKNOWN
            val ratio = seats.toDouble() / seatCapacity
            return when {
                seats <= 0 -> AvailabilityLevel.SOLD_OUT
                ratio > 0.40 -> AvailabilityLevel.HIGH
                ratio > 0.15 -> AvailabilityLevel.MEDIUM
                else -> AvailabilityLevel.LOW
            }
        }
}

/**
 * Availability and yield for REFERENCE sailings. Without a live feed we model how full a
 * crossing is from its season and how close it is (bookings accumulate as departure nears),
 * plus a stable per-sailing jitter. LIVE sailings use the operator's figures instead.
 */
class InventoryModel(private val clock: Clock) {

    fun loadFactor(sailing: Sailing, seasonMultiplier: Double): Double {
        val today = LocalDate.now(clock)
        val daysOut = ChronoUnit.DAYS.between(today, sailing.departureDate).coerceAtLeast(0)
        val progress = 1.0 / (1.0 + daysOut / 25.0)
        val seasonal = (0.40 + (seasonMultiplier - 1.0)).coerceIn(0.40, 0.88)
        val jitter = ((sailing.id.hashCode() and 0x7fffffff) % 1000) / 1000.0 * 0.16 - 0.08
        return (seasonal * (0.35 + 0.65 * progress) + jitter).coerceIn(0.02, 0.97)
    }

    fun yieldMultiplier(loadFactor: Double): Double = 1.0 + 0.4 * loadFactor.pow(3)

    fun view(
        sailing: Sailing,
        vessel: Vessel,
        seasonMultiplier: Double,
        direction: Direction,
        consumed: Consumption,
    ): AvailabilityView {
        val live = sailing.liveAvailability.takeIf { sailing.source == PriceSource.LIVE }
        val lf = loadFactor(sailing, seasonMultiplier)
        val seatCapacity = vessel.accommodations[AccommodationType.SEAT] ?: 0
        val vehicleFactor = if (direction == Direction.TO_DZ && seasonMultiplier > 1.2) 1.05 else 0.9

        val modelSeats = floor(seatCapacity * (1 - lf * 0.9)).toInt()
        val modelCabins = vessel.accommodations
            .filterKeys { it.isCabin }
            .mapValues { (_, cap) -> floor(cap * (1 - (lf * 1.1).coerceAtMost(0.99))).toInt() }
        val modelLane = floor(vessel.capacity.laneMeters * (1 - (lf * vehicleFactor).coerceAtMost(0.99))).toInt()
        val modelKennels = floor(vessel.petKennels * (1 - lf * 0.8)).toInt()

        val seats = live?.seats ?: modelSeats
        val cabins = if (live != null && live.cabins.isNotEmpty()) live.cabins else modelCabins
        val lane = live?.laneMeters ?: modelLane

        return AvailabilityView(
            seats = (seats - consumed.seats).coerceAtLeast(0),
            cabins = cabins.mapValues { (type, n) -> (n - (consumed.cabins[type] ?: 0)).coerceAtLeast(0) },
            laneMeters = (lane - consumed.laneMeters).coerceAtLeast(0),
            kennels = (modelKennels - consumed.kennels).coerceAtLeast(0),
            loadFactor = lf,
            seatCapacity = seatCapacity,
        )
    }

    /** Limits before WAVE's own consumption, used by the hold store's atomic check. */
    fun limits(sailing: Sailing, vessel: Vessel, seasonMultiplier: Double, direction: Direction): AvailabilityView =
        view(sailing, vessel, seasonMultiplier, direction, Consumption.NONE)
}
