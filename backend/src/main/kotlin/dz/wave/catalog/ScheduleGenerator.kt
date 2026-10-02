package dz.wave.catalog

import dz.wave.domain.PriceSource
import dz.wave.domain.Rotation
import dz.wave.domain.RotationLeg
import dz.wave.domain.Sailing
import dz.wave.domain.Season
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Expands vessel rotations (weekly cycles published by operators) into concrete sailings.
 * Generated sailings are REFERENCE data; the ingestion pipeline replaces them with LIVE ones.
 */
class ScheduleGenerator(private val data: CatalogData) {

    private val ports = data.ports.associateBy { it.code }
    private val routes = data.routes.associateBy { it.id }
    private val operators = data.operators.associateBy { it.code }

    fun generate(from: LocalDate, to: LocalDate): List<Sailing> {
        require(!to.isBefore(from)) { "Empty generation window" }
        val out = ArrayList<Sailing>()
        for (rotation in data.rotations) {
            val operator = operators.getValue(rotation.operator)
            if (!operator.active) continue
            val lastDate = operator.publishedUntil?.takeIf { it.isBefore(to) } ?: to
            // Cycles that started up to two weeks earlier can still have legs inside the window.
            var cycleStart = from.minusDays(14).with(TemporalAdjusters.nextOrSame(rotation.startDay.dayOfWeek))
            while (!cycleStart.isAfter(lastDate)) {
                if (seasonMatches(rotation.season, cycleStart)) {
                    for (leg in rotation.legs) {
                        val sailing = buildLeg(rotation, leg, cycleStart) ?: continue
                        val date = sailing.departureDate
                        if (!date.isBefore(from) && !date.isAfter(lastDate)) out += sailing
                    }
                }
                cycleStart = cycleStart.plusWeeks(1)
            }
        }
        out.sortBy { it.departure.toInstant() }
        return out
    }

    private fun buildLeg(rotation: Rotation, leg: RotationLeg, cycleStart: LocalDate): Sailing? {
        if (leg.months != null && cycleStart.monthValue !in leg.months) return null
        if (leg.weeksOfMonth != null && weekOfMonth(cycleStart) !in leg.weeksOfMonth) return null
        if (leg.weekParity != null && weekParity(cycleStart) != leg.weekParity) return null
        val departureDate = cycleStart.plusDays(leg.dayOffset.toLong())
        if (leg.validFrom != null && departureDate.isBefore(leg.validFrom)) return null
        if (leg.validTo != null && departureDate.isAfter(leg.validTo)) return null

        val route = routes.getValue(leg.route)
        val fromPort = ports.getValue(route.from)
        val toPort = ports.getValue(route.to)
        val departure = ZonedDateTime.of(departureDate, LocalTime.parse(leg.departure), fromPort.zoneId)
        val arrival = departure.plusMinutes(leg.durationMin.toLong()).withZoneSameInstant(toPort.zoneId)
        return Sailing(
            id = sailingId(route.id, departure),
            routeId = route.id,
            operator = route.operator,
            vessel = rotation.vessel,
            from = route.from,
            to = route.to,
            departure = departure,
            arrival = arrival,
            source = PriceSource.REFERENCE,
        )
    }

    companion object {
        private val ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        private val SUMMER_START = MonthDay.of(6, 15)
        private val SUMMER_END = MonthDay.of(9, 15)
        private val EPOCH_MONDAY = LocalDate.of(1970, 1, 5)

        fun sailingId(routeId: String, departure: ZonedDateTime): String =
            "$routeId-${departure.toLocalDateTime().format(ID_FORMAT)}"

        fun isSummer(date: LocalDate): Boolean {
            val md = MonthDay.from(date)
            return !md.isBefore(SUMMER_START) && !md.isAfter(SUMMER_END)
        }

        fun seasonMatches(season: Season, date: LocalDate): Boolean = when (season) {
            Season.ALL -> true
            Season.SUMMER -> isSummer(date)
            Season.WINTER -> !isSummer(date)
        }

        /** 1 for the first occurrence of that weekday in its month, 2 for the second, ... */
        fun weekOfMonth(date: LocalDate): Int = (date.dayOfMonth - 1) / 7 + 1

        /** Parity of whole weeks since a fixed Monday; unlike ISO weeks it never repeats at year end. */
        fun weekParity(date: LocalDate): Int =
            Math.floorMod(ChronoUnit.WEEKS.between(EPOCH_MONDAY, date), 2L).toInt()
    }
}
