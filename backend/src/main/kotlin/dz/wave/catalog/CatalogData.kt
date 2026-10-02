package dz.wave.catalog

import dz.wave.domain.FareTable
import dz.wave.domain.FareTableDef
import dz.wave.domain.Operator
import dz.wave.domain.Port
import dz.wave.domain.Promotion
import dz.wave.domain.Regulation
import dz.wave.domain.Rotation
import dz.wave.domain.Route
import dz.wave.domain.SeaLane
import dz.wave.domain.SeasonRule
import dz.wave.domain.Vessel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Static reference data shipped with the service (see docs/research/ferry-booking-algeria.md). */
data class CatalogData(
    val ports: List<Port>,
    val operators: List<Operator>,
    val vessels: List<Vessel>,
    val lanes: List<SeaLane>,
    val routes: List<Route>,
    val rotations: List<Rotation>,
    val fareTables: List<FareTableDef>,
    val seasons: List<SeasonRule>,
    val regulations: List<Regulation>,
    val promotions: List<Promotion>,
) {
    fun validate(): CatalogData {
        val portCodes = ports.map { it.code }.toSet()
        val operatorCodes = operators.map { it.code }.toSet()
        val vesselCodes = vessels.map { it.code }.toSet()
        val laneIds = lanes.map { it.id }.toSet()
        val routeIds = routes.map { it.id }.toSet()
        val problems = mutableListOf<String>()

        lanes.forEach { lane ->
            if (lane.a !in portCodes || lane.b !in portCodes) problems += "lane ${lane.id}: unknown port"
            if (lane.waypoints.size < 2 || lane.waypoints.any { it.size != 2 }) problems += "lane ${lane.id}: bad waypoints"
        }
        routes.forEach { r ->
            if (r.from !in portCodes || r.to !in portCodes) problems += "route ${r.id}: unknown port"
            if (r.operator !in operatorCodes) problems += "route ${r.id}: unknown operator"
            if (r.lane !in laneIds) problems += "route ${r.id}: unknown lane ${r.lane}"
            val lane = lanes.firstOrNull { it.id == r.lane }
            if (lane != null && setOf(lane.a, lane.b) != setOf(r.from, r.to)) problems += "route ${r.id}: lane ports mismatch"
        }
        vessels.forEach { v -> if (v.operator !in operatorCodes) problems += "vessel ${v.code}: unknown operator" }
        rotations.forEach { rot ->
            if (rot.vessel != null && rot.vessel !in vesselCodes) problems += "rotation ${rot.id}: unknown vessel"
            rot.legs.forEach { leg ->
                if (leg.route !in routeIds) problems += "rotation ${rot.id}: unknown route ${leg.route}"
                if (leg.durationMin <= 0) problems += "rotation ${rot.id}: bad duration"
            }
        }
        val tabled = fareTables.flatMap { it.routes }.toSet()
        routes.filter { it.id !in tabled }.forEach { problems += "route ${it.id}: no fare table" }
        require(problems.isEmpty()) { "Invalid catalog:\n" + problems.joinToString("\n") }
        return this
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        fun fromResources(base: String = "catalog"): CatalogData = CatalogData(
            ports = read("$base/ports.json", Port.serializer()),
            operators = read("$base/operators.json", Operator.serializer()),
            vessels = read("$base/vessels.json", Vessel.serializer()),
            lanes = read("$base/lanes.json", SeaLane.serializer()),
            routes = read("$base/routes.json", Route.serializer()),
            rotations = read("$base/rotations.json", Rotation.serializer()),
            fareTables = read("$base/fares.json", FareTableDef.serializer()),
            seasons = read("$base/seasons.json", SeasonRule.serializer()),
            regulations = read("$base/regulations.json", Regulation.serializer()),
            promotions = read("$base/promotions.json", Promotion.serializer()),
        ).validate()

        private fun <T> read(path: String, item: KSerializer<T>): List<T> {
            val text = CatalogData::class.java.classLoader.getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Missing catalog resource $path")
            return json.decodeFromString(ListSerializer(item), text)
        }
    }
}

/** Resolves `extends`/`scale` chains into one concrete [FareTable] per route id. */
object FareTables {
    fun resolve(defs: List<FareTableDef>): Map<String, FareTable> {
        val byId = defs.associateBy { it.id }
        val cache = HashMap<String, FareTable>()

        fun build(id: String, seen: Set<String>): FareTable {
            cache[id]?.let { return it }
            require(id !in seen) { "Cyclic fare table inheritance at $id" }
            val def = byId[id] ?: error("Unknown fare table $id")
            val parent = def.extends?.let { build(it, seen + id) }
            val s = def.scale
            val table = FareTable(
                id = def.id,
                currency = def.currency ?: parent?.currency ?: error("Fare table $id has no currency"),
                adultSeat = def.adultSeat ?: parent?.adultSeat?.times(s) ?: error("Fare table $id has no adultSeat"),
                accommodation = def.accommodation ?: parent?.accommodation?.mapValues { it.value * s } ?: emptyMap(),
                vehicles = def.vehicles ?: parent?.vehicles?.mapValues { it.value * s } ?: emptyMap(),
                pet = def.pet ?: parent?.pet?.times(s) ?: 0.0,
                taxes = def.taxes ?: parent?.taxes ?: error("Fare table $id has no taxes"),
            )
            cache[id] = table
            return table
        }

        val result = HashMap<String, FareTable>()
        defs.forEach { def ->
            val table = build(def.id, emptySet())
            def.routes.forEach { routeId ->
                require(routeId !in result) { "Route $routeId has two fare tables" }
                result[routeId] = table
            }
        }
        return result
    }
}
