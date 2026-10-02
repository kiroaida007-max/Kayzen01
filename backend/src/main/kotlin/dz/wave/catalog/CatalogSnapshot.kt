package dz.wave.catalog

import dz.wave.domain.FareTable
import dz.wave.domain.Operator
import dz.wave.domain.Port
import dz.wave.domain.Route
import dz.wave.domain.Sailing
import dz.wave.domain.Vessel
import dz.wave.geo.LatLon
import dz.wave.geo.RouteGeometry
import java.time.Instant
import java.time.LocalDate
import java.util.NavigableMap
import java.util.TreeMap

/**
 * Immutable, fully indexed view of the catalog. Every search runs against one snapshot,
 * entirely in memory, which is what lets a single pod answer thousands of searches per second.
 */
class CatalogSnapshot(
    val version: Long,
    val data: CatalogData,
    val fareTables: Map<String, FareTable>,
    sailings: Collection<Sailing>,
    val builtAt: Instant,
) {
    val ports: Map<String, Port> = data.ports.associateBy { it.code }
    val operators: Map<String, Operator> = data.operators.associateBy { it.code }
    val vessels: Map<String, Vessel> = data.vessels.associateBy { it.code }
    val routes: Map<String, Route> = data.routes.associateBy { it.id }

    val routesByPair: Map<Pair<String, String>, List<Route>> = data.routes
        .filter { operators[it.operator]?.active == true }
        .groupBy { it.from to it.to }

    val sailingsById: Map<String, Sailing> = sailings.associateBy { it.id }

    private val sailingsByRouteDate: Map<String, NavigableMap<LocalDate, List<Sailing>>> =
        sailings.groupBy { it.routeId }.mapValues { (_, list) ->
            TreeMap<LocalDate, List<Sailing>>().apply {
                list.groupBy { it.departureDate }.forEach { (d, s) -> put(d, s.sortedBy { it.departure.toInstant() }) }
            }
        }

    val sailingsByVessel: Map<String, List<Sailing>> = sailings
        .filter { it.vessel != null }
        .groupBy { it.vessel!! }
        .mapValues { (_, list) -> list.sortedBy { it.departure.toInstant() } }

    val sailingCount: Int = sailingsById.size

    private val geometries: Map<String, RouteGeometry> = data.routes.associate { route ->
        val lane = data.lanes.first { it.id == route.lane }
        val forward = RouteGeometry(lane.waypoints.map { LatLon(it[0], it[1]) })
        route.id to if (lane.a == route.from) forward else forward.reversed()
    }

    fun geometry(routeId: String): RouteGeometry = geometries.getValue(routeId)

    fun sailings(routeId: String, from: LocalDate, to: LocalDate): List<Sailing> =
        sailingsByRouteDate[routeId]?.subMap(from, true, to, true)?.values?.flatten().orEmpty()

    fun destinationsFrom(portCode: String): List<String> =
        routesByPair.keys.filter { it.first == portCode }.map { it.second }.distinct()

    /** Next published departure date of a route, used for "pas de départ ce jour" hints. */
    fun nextDeparture(routeId: String, after: LocalDate): LocalDate? =
        sailingsByRouteDate[routeId]?.ceilingKey(after)
}
