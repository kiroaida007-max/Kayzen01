package dz.wave.rules

import dz.wave.domain.AccommodationPref
import dz.wave.domain.AccommodationType
import dz.wave.domain.CabinAllocation

/**
 * Finds the cheapest set of whole cabins that gives every berth-occupying passenger a bed.
 * Constraints: per-type availability, and at most one cabin per adult (no cabin of minors only).
 * Groups are at most 9 people, so an exhaustive search is instant.
 */
object CabinAllocator {

    data class Option(val type: AccommodationType, val price: Long, val available: Int)

    data class Result(val cabins: List<CabinAllocation>, val totalPrice: Long)

    fun allowedTypes(pref: AccommodationPref): List<AccommodationType> = when (pref) {
        AccommodationPref.SEAT -> emptyList()
        AccommodationPref.CABIN_ANY -> listOf(
            AccommodationType.CABIN_INT_2, AccommodationType.CABIN_INT_4,
            AccommodationType.CABIN_EXT_2, AccommodationType.CABIN_EXT_4,
        )
        AccommodationPref.CABIN_INTERIOR -> listOf(AccommodationType.CABIN_INT_2, AccommodationType.CABIN_INT_4)
        AccommodationPref.CABIN_EXTERIOR -> listOf(AccommodationType.CABIN_EXT_2, AccommodationType.CABIN_EXT_4)
        AccommodationPref.SUITE -> listOf(AccommodationType.SUITE)
    }

    /**
     * @param berthPassengers passengers needing a bed (infants excluded)
     * @param maxCabins number of adults available to occupy cabins
     * @param mandatory cabins that must be part of the result (pet-friendly, PMR)
     */
    fun allocate(
        berthPassengers: Int,
        maxCabins: Int,
        options: List<Option>,
        mandatory: List<Option> = emptyList(),
    ): Result? {
        if (berthPassengers <= 0 || maxCabins <= 0) return null
        if (mandatory.any { it.available <= 0 }) return null
        val mandatoryBerths = mandatory.sumOf { it.type.berths }
        val mandatoryPrice = mandatory.sumOf { it.price }
        if (mandatory.size > maxCabins) return null
        val remainingPax = (berthPassengers - mandatoryBerths).coerceAtLeast(0)
        val remainingCabins = maxCabins - mandatory.size

        // Mandatory cabins consume availability of their own type too.
        val usable = options.map { opt ->
            opt.copy(available = opt.available - mandatory.count { it.type == opt.type })
        }.filter { it.available > 0 }

        var best: Pair<Long, List<Int>>? = null
        val counts = IntArray(usable.size)

        fun search(index: Int, berths: Int, cabins: Int, price: Long) {
            if (berths >= remainingPax) {
                val candidate = price to counts.toList()
                val current = best
                if (current == null || isBetter(candidate, current, usable)) best = candidate
                return
            }
            if (index == usable.size || cabins >= remainingCabins) return
            val opt = usable[index]
            val needed = (remainingPax - berths + opt.type.berths - 1) / opt.type.berths
            val maxHere = minOf(opt.available, remainingCabins - cabins, needed)
            for (n in maxHere downTo 0) {
                counts[index] = n
                search(index + 1, berths + n * opt.type.berths, cabins + n, price + n * opt.price)
            }
            counts[index] = 0
        }

        if (remainingPax == 0) {
            best = 0L to List(usable.size) { 0 }
        } else {
            search(0, 0, 0, 0L)
        }
        val (price, chosen) = best ?: return null

        val allocation = linkedMapOf<AccommodationType, Int>()
        mandatory.forEach { allocation.merge(it.type, 1, Int::plus) }
        usable.forEachIndexed { i, opt -> if (chosen[i] > 0) allocation.merge(opt.type, chosen[i], Int::plus) }
        return Result(allocation.map { (type, count) -> CabinAllocation(type, count) }, price + mandatoryPrice)
    }

    /** Cheapest first; on equal price prefer fewer empty berths, then fewer cabins. */
    private fun isBetter(a: Pair<Long, List<Int>>, b: Pair<Long, List<Int>>, opts: List<Option>): Boolean {
        if (a.first != b.first) return a.first < b.first
        val bedsA = a.second.indices.sumOf { a.second[it] * opts[it].type.berths }
        val bedsB = b.second.indices.sumOf { b.second[it] * opts[it].type.berths }
        if (bedsA != bedsB) return bedsA < bedsB
        return a.second.sum() < b.second.sum()
    }
}
