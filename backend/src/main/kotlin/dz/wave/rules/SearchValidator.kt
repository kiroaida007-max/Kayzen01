package dz.wave.rules

import dz.wave.catalog.CatalogSnapshot
import dz.wave.domain.Lang
import dz.wave.domain.Port
import dz.wave.domain.Regulation
import dz.wave.domain.RuleSeverity
import dz.wave.domain.Sailing
import dz.wave.domain.SearchRequest
import dz.wave.domain.TripType
import dz.wave.domain.VehicleInput
import dz.wave.domain.Violation
import dz.wave.pricing.DateRanges
import dz.wave.pricing.directionOf
import java.time.LocalDate

/** Checks a search request before any sailing is looked at. */
object SearchValidator {
    const val MAX_PASSENGERS = 9
    const val MAX_DAYS_AHEAD = 400L
    const val MAX_PETS = 4
    const val MAX_FLEX_DAYS = 3
    const val INFANT_LAP_AGE = 2
    const val MAX_VEHICLE_HEIGHT_M = 4.5
    const val MAX_VEHICLE_LENGTH_M = 18.0

    fun validate(req: SearchRequest, snap: CatalogSnapshot, today: LocalDate): List<Violation> {
        val lang = req.lang
        val out = mutableListOf<Violation>()
        fun error(code: String, field: String? = null, params: Map<String, String> = emptyMap()) {
            out += Messages.violation(code, RuleSeverity.ERROR, lang, field, params)
        }

        val from = snap.ports[req.from]
        val to = snap.ports[req.to]
        if (from == null) error("PORT_UNKNOWN", "from", mapOf("port" to req.from))
        if (to == null) error("PORT_UNKNOWN", "to", mapOf("port" to req.to))
        if (req.from == req.to) error("ROUTE_SAME_PORT", "to")
        if (from != null && to != null && from.code != to.code) {
            if (snap.routesByPair[from.code to to.code].isNullOrEmpty()) {
                error("NO_ROUTE", "to", mapOf("from" to from.name.get(lang), "to" to to.name.get(lang)))
            } else if (req.tripType == TripType.ROUND_TRIP && snap.routesByPair[to.code to from.code].isNullOrEmpty()) {
                error("NO_ROUTE", "returnDate", mapOf("from" to to.name.get(lang), "to" to from.name.get(lang)))
            }
        }

        if (req.departureDate.isBefore(today)) error("DATE_IN_PAST", "departureDate")
        if (req.departureDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            error("DATE_TOO_FAR", "departureDate", mapOf("days" to MAX_DAYS_AHEAD.toString()))
        }
        if (req.tripType == TripType.ROUND_TRIP) {
            val ret = req.returnDate
            if (ret == null) {
                error("RETURN_REQUIRED", "returnDate")
            } else {
                if (ret.isBefore(req.departureDate)) error("RETURN_BEFORE_DEPARTURE", "returnDate")
                if (ret.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
                    error("DATE_TOO_FAR", "returnDate", mapOf("days" to MAX_DAYS_AHEAD.toString()))
                }
            }
        }

        val pax = req.passengers
        if (pax.adults < 0 || pax.seniors < 0) {
            error("PAX_INVALID", "passengers")
        } else {
            if (pax.grownUps < 1) error("PAX_ADULT_REQUIRED", "passengers")
            if (pax.total > MAX_PASSENGERS) error("PAX_MAX", "passengers", mapOf("max" to MAX_PASSENGERS.toString()))
            if (pax.childrenAges.any { it !in 0..17 }) error("CHILD_AGE_INVALID", "passengers.childrenAges")
            if (pax.childrenAges.count { it in 0 until INFANT_LAP_AGE } > pax.grownUps) {
                error("INFANTS_EXCEED_ADULTS", "passengers.childrenAges")
            }
        }

        req.vehicle?.let { validateVehicle(it, lang)?.let(out::add) }

        if (req.pets.any { it.count < 1 }) error("PET_COUNT_INVALID", "pets")
        if (req.pets.sumOf { it.count } > MAX_PETS) error("PETS_MAX", "pets", mapOf("max" to MAX_PETS.toString()))
        if (req.flexDays !in 0..MAX_FLEX_DAYS) error("FLEX_DAYS_INVALID", "flexDays")
        req.operators?.filter { it !in snap.operators }?.forEach {
            error("OPERATOR_UNKNOWN", "operators", mapOf("operator" to it))
        }
        return out
    }

    fun validateVehicle(v: VehicleInput, lang: Lang): Violation? {
        val badLength = v.lengthM != null && v.lengthM !in 1.0..20.0
        val badHeight = v.heightM != null && v.heightM !in 0.5..5.0
        val badTrailer = v.trailerLengthM != null && v.trailerLengthM !in 0.5..12.0
        if (badLength || badHeight || badTrailer) {
            return Messages.violation("VEHICLE_DIMENSIONS_INVALID", RuleSeverity.ERROR, lang, "vehicle")
        }
        if (VehicleClassifier.effectiveHeight(v) > MAX_VEHICLE_HEIGHT_M) {
            return Messages.violation(
                "VEHICLE_TOO_HIGH", RuleSeverity.ERROR, lang, "vehicle.heightM",
                mapOf("max" to MAX_VEHICLE_HEIGHT_M.toString()),
            )
        }
        if (VehicleClassifier.totalLength(v) > MAX_VEHICLE_LENGTH_M) {
            return Messages.violation(
                "VEHICLE_TOO_LONG", RuleSeverity.ERROR, lang, "vehicle.lengthM",
                mapOf("max" to MAX_VEHICLE_LENGTH_M.toString()),
            )
        }
        return null
    }

    /** Informational reminders that apply to the whole trip (documents, check-in, pets...). */
    fun notices(req: SearchRequest): List<Violation> {
        val lang = req.lang
        val out = mutableListOf(
            Messages.violation("DOC_PASSPORT", RuleSeverity.INFO, lang),
            Messages.violation("CHECKIN", RuleSeverity.INFO, lang),
        )
        if (req.passengers.childrenAges.isNotEmpty()) out += Messages.violation("MINOR_AUTHORIZATION", RuleSeverity.INFO, lang)
        if (req.vehicle != null) out += Messages.violation("VEHICLE_DOCS", RuleSeverity.INFO, lang)
        if (req.pets.isNotEmpty()) out += Messages.violation("PET_DOCS", RuleSeverity.INFO, lang)
        if (req.accessibility.any) out += Messages.violation("PMR_NOTICE", RuleSeverity.INFO, lang)
        return out
    }
}

/** Evaluates authorities' date-ranged rules (e.g. summer vehicle restrictions) on one sailing. */
object RegulationChecker {
    fun check(
        regulations: List<Regulation>,
        sailing: Sailing,
        fromPort: Port,
        toPort: Port,
        vehicle: VehicleInput?,
        lang: Lang,
    ): List<Violation> {
        if (vehicle == null) return emptyList()
        val direction = directionOf(fromPort, toPort)
        val algerianPort = when {
            toPort.isAlgerian -> toPort.code
            fromPort.isAlgerian -> fromPort.code
            else -> return emptyList()
        }
        val date = sailing.departureDate
        val out = mutableListOf<Violation>()
        for (reg in regulations) {
            if (!DateRanges.contains(reg.from, reg.to, date, reg.years)) continue
            if (reg.direction != dz.wave.domain.Direction.ANY && reg.direction != direction) continue
            if (reg.ports.isNotEmpty() && algerianPort !in reg.ports) continue
            val effect = reg.effect
            val banned = vehicle.type in effect.bannedVehicleTypes
            val tooNew = effect.newVehicleMaxAgeYears?.let { maxAge ->
                vehicle.registrationYear?.let { date.year - it < maxAge } ?: false
            } ?: false
            if (banned || tooNew) {
                out += Violation(
                    code = "REGULATION_${reg.id}",
                    severity = reg.severity,
                    message = reg.title.get(lang) + " — " + reg.description.get(lang),
                    field = "vehicle",
                    params = mapOf("source" to reg.source),
                )
            }
        }
        return out
    }
}
