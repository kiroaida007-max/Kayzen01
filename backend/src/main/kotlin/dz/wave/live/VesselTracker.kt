@file:UseSerializers(InstantSerializer::class, ZonedDateTimeSerializer::class)

package dz.wave.live

import dz.wave.catalog.CatalogSnapshot
import dz.wave.domain.InstantSerializer
import dz.wave.domain.Sailing
import dz.wave.domain.ZonedDateTimeSerializer
import dz.wave.geo.GeoMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

@Serializable
enum class PositionSource { AIS, ESTIMATED }

@Serializable
enum class NavStatus { UNDERWAY, IN_PORT }

@Serializable
data class VesselPosition(
    val vessel: String,
    val name: String,
    val operator: String,
    val operatorColor: String,
    val mmsi: String? = null,
    val lat: Double,
    val lon: Double,
    val speedKn: Double,
    val courseDeg: Double,
    val status: NavStatus,
    val source: PositionSource,
    val updatedAt: Instant,
    val sailingId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val departure: ZonedDateTime? = null,
    val eta: ZonedDateTime? = null,
    val progress: Double? = null,
)

/** A raw AIS fix (aisstream.io, or a local receiver forwarded through the ingest API). */
@Serializable
data class AisFix(
    val mmsi: String,
    val lat: Double,
    val lon: Double,
    val speedKn: Double,
    val courseDeg: Double,
    val heading: Double? = null,
    val timestamp: Instant,
)

/**
 * Dead reckoning from the schedule: a ship on a crossing is placed along its sea lane in proportion
 * to elapsed time. Used for every ship without a fresh AIS fix, and flagged `ESTIMATED`.
 */
class VesselTracker {

    fun estimate(snap: CatalogSnapshot, now: Instant): List<VesselPosition> =
        snap.data.vessels
            .filter { snap.operators[it.operator]?.active == true }
            .mapNotNull { vessel ->
                val sailings = snap.sailingsByVessel[vessel.code].orEmpty()
                if (sailings.isEmpty()) return@mapNotNull null
                val operator = snap.operators.getValue(vessel.operator)
                val current = sailings.firstOrNull { !now.isBefore(it.departure.toInstant()) && now.isBefore(it.arrival.toInstant()) }
                if (current != null) {
                    underway(snap, current, now, vessel.code, vessel.name, vessel.mmsi, operator.code, operator.color)
                } else {
                    val last = sailings.lastOrNull { !it.arrival.toInstant().isAfter(now) }
                    val next = sailings.firstOrNull { it.departure.toInstant().isAfter(now) }
                    val portCode = last?.to ?: next?.from ?: return@mapNotNull null
                    val port = snap.ports.getValue(portCode)
                    VesselPosition(
                        vessel = vessel.code, name = vessel.name, operator = operator.code, operatorColor = operator.color,
                        mmsi = vessel.mmsi, lat = port.lat, lon = port.lon, speedKn = 0.0, courseDeg = 0.0,
                        status = NavStatus.IN_PORT, source = PositionSource.ESTIMATED, updatedAt = now,
                        sailingId = next?.id, from = next?.from, to = next?.to, departure = next?.departure, eta = next?.arrival,
                    )
                }
            }

    private fun underway(
        snap: CatalogSnapshot,
        sailing: Sailing,
        now: Instant,
        code: String,
        name: String,
        mmsi: String?,
        operator: String,
        color: String,
    ): VesselPosition {
        val total = Duration.between(sailing.departure, sailing.arrival).toSeconds().toDouble()
        val elapsed = Duration.between(sailing.departure.toInstant(), now).toSeconds().toDouble()
        val fraction = (elapsed / total).coerceIn(0.0, 1.0)
        val geometry = snap.geometry(sailing.routeId)
        val point = geometry.pointAt(fraction)
        val speed = geometry.lengthNm / (total / 3600.0)
        return VesselPosition(
            vessel = code, name = name, operator = operator, operatorColor = color, mmsi = mmsi,
            lat = point.position.lat, lon = point.position.lon, speedKn = round1(speed), courseDeg = round1(point.bearingDeg),
            status = NavStatus.UNDERWAY, source = PositionSource.ESTIMATED, updatedAt = now,
            sailingId = sailing.id, from = sailing.from, to = sailing.to, departure = sailing.departure,
            eta = sailing.arrival, progress = round3(fraction),
        )
    }

    /** Overlays fresh AIS fixes on the estimate; keeps schedule context (route, ETA) from the estimate. */
    fun merge(estimates: List<VesselPosition>, fixes: Map<String, AisFix>, now: Instant, maxAge: Duration): List<VesselPosition> =
        estimates.map { est ->
            val fix = est.mmsi?.let { fixes[it] }
            if (fix == null || Duration.between(fix.timestamp, now) > maxAge) {
                est
            } else {
                val moving = fix.speedKn >= 1.0
                est.copy(
                    lat = fix.lat, lon = fix.lon, speedKn = round1(fix.speedKn), courseDeg = round1(fix.heading ?: fix.courseDeg),
                    status = if (moving) NavStatus.UNDERWAY else est.status, source = PositionSource.AIS, updatedAt = fix.timestamp,
                )
            }
        }

    companion object {
        private fun round1(v: Double) = Math.round(v * 10.0) / 10.0
        private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0

        fun distanceNm(aLat: Double, aLon: Double, bLat: Double, bLon: Double) =
            GeoMath.distanceNm(dz.wave.geo.LatLon(aLat, aLon), dz.wave.geo.LatLon(bLat, bLon))
    }
}
