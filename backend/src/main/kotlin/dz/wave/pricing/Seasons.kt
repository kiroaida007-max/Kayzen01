package dz.wave.pricing

import dz.wave.domain.Direction
import dz.wave.domain.Port
import dz.wave.domain.SeasonRule
import java.time.LocalDate
import java.time.MonthDay

object DateRanges {
    /** [from]/[to] are `MM-dd` (yearly, may wrap over new year) or `yyyy-MM-dd` (one-off). */
    fun contains(from: String, to: String, date: LocalDate, years: List<Int>? = null): Boolean {
        if (years != null && date.year !in years) return false
        if (from.length == 10 && to.length == 10) {
            val f = LocalDate.parse(from)
            val t = LocalDate.parse(to)
            return !date.isBefore(f) && !date.isAfter(t)
        }
        val f = MonthDay.parse("--$from")
        val t = MonthDay.parse("--$to")
        val md = MonthDay.from(date)
        return if (!f.isAfter(t)) {
            !md.isBefore(f) && !md.isAfter(t)
        } else {
            !md.isBefore(f) || !md.isAfter(t)
        }
    }
}

fun directionOf(from: Port, to: Port): Direction = when {
    to.isAlgerian && !from.isAlgerian -> Direction.TO_DZ
    from.isAlgerian && !to.isAlgerian -> Direction.FROM_DZ
    else -> Direction.ANY
}

/**
 * Seasonal demand curve. Diaspora traffic is directional: Europe → Algeria peaks in early
 * summer, Algeria → Europe at the end of August, so the multiplier depends on the direction.
 */
class SeasonCalendar(private val rules: List<SeasonRule>) {
    fun multiplier(date: LocalDate, direction: Direction): Double =
        rules.asSequence()
            .filter { it.direction == Direction.ANY || it.direction == direction }
            .filter { DateRanges.contains(it.from, it.to, date) }
            .maxOfOrNull { it.multiplier } ?: 1.0

    fun seasonName(date: LocalDate, direction: Direction): String? =
        rules.asSequence()
            .filter { it.direction == Direction.ANY || it.direction == direction }
            .filter { DateRanges.contains(it.from, it.to, date) }
            .maxByOrNull { it.multiplier }?.name
}
