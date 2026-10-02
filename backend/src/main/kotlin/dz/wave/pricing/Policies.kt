package dz.wave.pricing

import dz.wave.domain.AccommodationPref
import dz.wave.domain.PassengersInput
import dz.wave.domain.Promotion
import dz.wave.domain.TariffCode
import dz.wave.domain.TariffDef
import dz.wave.domain.VehicleInput
import dz.wave.rules.VehicleClassifier
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Applies a tariff's penalty tiers exactly as written in the operator's conditions. */
object CancellationPolicy {
    fun feePercent(tariff: TariffDef, departure: ZonedDateTime, bookedAt: Instant, now: Instant): Double {
        if (!now.isBefore(departure.toInstant())) return 1.0
        if (!tariff.refundable) return 1.0
        val hoursBefore = Duration.between(now, departure.toInstant()).toHours()
        val freeWindow = tariff.freeCancellationHoursAfterPurchase
        if (freeWindow != null && Duration.between(bookedAt, now).toHours() < freeWindow && hoursBefore > 2) return 0.0
        val tier = tariff.cancellation
            .sortedByDescending { it.minHoursBefore }
            .firstOrNull { hoursBefore >= it.minHoursBefore }
        return tier?.feePercent ?: 1.0
    }
}

/** Promo fares are only sold early and while the crossing is not too full. */
object TariffAvailability {
    fun isOpen(tariff: TariffDef, departureDate: LocalDate, today: LocalDate, loadFactor: Double): Boolean {
        val daysOut = java.time.temporal.ChronoUnit.DAYS.between(today, departureDate)
        if (tariff.minDaysBeforeDeparture != null && daysOut < tariff.minDaysBeforeDeparture) return false
        if (tariff.maxLoadFactor != null && loadFactor > tariff.maxLoadFactor) return false
        return true
    }
}

data class PromotionContext(
    val operator: String,
    val departureDate: LocalDate,
    val today: LocalDate,
    val passengers: PassengersInput,
    val vehicle: VehicleInput?,
    val accommodation: AccommodationPref,
    val roundTrip: Boolean,
    val tariff: TariffCode,
)

/** Picks the single best applicable promotion (offers are never stacked, nor applied to promo fares). */
class PromotionEngine(private val promotions: List<Promotion>) {

    fun best(ctx: PromotionContext): Promotion? =
        promotions.filter { applies(it, ctx) }.maxByOrNull { it.discount }

    fun active(today: LocalDate): List<Promotion> =
        promotions.filter { p -> (p.bookFrom == null || !today.isBefore(p.bookFrom)) && (p.bookTo == null || !today.isAfter(p.bookTo)) }

    private fun applies(p: Promotion, ctx: PromotionContext): Boolean {
        if (ctx.tariff == TariffCode.PROMO) return false
        if (p.operator != null && p.operator != ctx.operator) return false
        if (p.bookFrom != null && ctx.today.isBefore(p.bookFrom)) return false
        if (p.bookTo != null && ctx.today.isAfter(p.bookTo)) return false
        if (p.travelFrom != null && ctx.departureDate.isBefore(p.travelFrom)) return false
        if (p.travelTo != null && ctx.departureDate.isAfter(p.travelTo)) return false
        val c = p.conditions
        if (c.requiresRoundTrip && !ctx.roundTrip) return false
        if (c.requiresCabin && ctx.accommodation == AccommodationPref.SEAT) return false
        if (c.requiresVehicle && ctx.vehicle == null) return false
        if (c.requiresChildAgeRange != null) {
            val (min, max) = c.requiresChildAgeRange[0] to c.requiresChildAgeRange[1]
            if (ctx.passengers.childrenAges.none { it in min..max }) return false
        }
        ctx.vehicle?.let { v ->
            if (c.maxVehicleLengthM != null && VehicleClassifier.totalLength(v) > c.maxVehicleLengthM) return false
            if (c.maxVehicleHeightM != null && VehicleClassifier.effectiveHeight(v) > c.maxVehicleHeightM) return false
        }
        return true
    }
}
