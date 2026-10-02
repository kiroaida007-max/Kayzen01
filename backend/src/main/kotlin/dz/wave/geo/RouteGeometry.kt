package dz.wave.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

data class PointOnRoute(val position: LatLon, val bearingDeg: Double)

object GeoMath {
    private const val EARTH_RADIUS_NM = 3440.065

    fun distanceNm(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_NM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Linear interpolation is accurate enough on legs of a few hundred nautical miles. */
    fun interpolate(a: LatLon, b: LatLon, fraction: Double): LatLon =
        LatLon(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)
}

/** Polyline of a sea lane with cumulative distances, used to place a ship along its route. */
class RouteGeometry(val points: List<LatLon>) {
    init {
        require(points.size >= 2) { "A route needs at least two points" }
    }

    private val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + GeoMath.distanceNm(points[i - 1], points[i])
    }

    val lengthNm: Double get() = cumulative.last()

    fun pointAt(fraction: Double): PointOnRoute {
        val target = fraction.coerceIn(0.0, 1.0) * lengthNm
        var i = 1
        while (i < points.size - 1 && cumulative[i] < target) i++
        val segStart = cumulative[i - 1]
        val segLen = (cumulative[i] - segStart).takeIf { it > 0 } ?: 1.0
        val local = ((target - segStart) / segLen).coerceIn(0.0, 1.0)
        return PointOnRoute(
            GeoMath.interpolate(points[i - 1], points[i], local),
            GeoMath.bearingDeg(points[i - 1], points[i]),
        )
    }

    fun reversed(): RouteGeometry = RouteGeometry(points.reversed())
}
