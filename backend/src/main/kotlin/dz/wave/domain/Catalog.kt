@file:UseSerializers(LocalDateSerializer::class, ZonedDateTimeSerializer::class, InstantSerializer::class)

package dz.wave.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Serializable
enum class Lang {
    @SerialName("fr") FR,
    @SerialName("en") EN,
    @SerialName("ar") AR,
}

@Serializable
data class LocalizedText(val fr: String, val en: String, val ar: String) {
    fun get(lang: Lang): String = when (lang) {
        Lang.FR -> fr
        Lang.EN -> en
        Lang.AR -> ar
    }
}

@Serializable
data class Port(
    val code: String,
    val shortCode: String,
    val name: LocalizedText,
    val country: String,
    val lat: Double,
    val lon: Double,
    val zone: String,
    val terminal: String? = null,
) {
    val zoneId: ZoneId get() = ZoneId.of(zone)
    val isAlgerian: Boolean get() = country == "DZ"
}

@Serializable
data class AgeBands(
    /** Highest age (inclusive) that still counts as an infant (free, no berth). */
    val infantMaxAge: Int,
    /** Highest age (inclusive) that still gets the child fare. */
    val childMaxAge: Int,
    val youthMaxAge: Int? = null,
    val seniorMinAge: Int? = null,
)

@Serializable
data class CategoryDiscounts(
    val infant: Double = 1.0,
    val child: Double = 0.0,
    val youth: Double = 0.0,
    val senior: Double = 0.0,
    val pmr: Double = 0.0,
)

@Serializable
enum class TariffCode { PROMO, STANDARD, FLEX }

/** Fee applied when cancelling at least [minHoursBefore] hours before departure. */
@Serializable
data class PenaltyTier(val minHoursBefore: Int, val feePercent: Double)

@Serializable
data class TariffDef(
    val code: TariffCode,
    val name: LocalizedText,
    val multiplier: Double,
    val refundable: Boolean,
    val modifiable: Boolean,
    val cancellation: List<PenaltyTier> = emptyList(),
    val freeCancellationHoursAfterPurchase: Int? = null,
    /** Promo fares close this many days before departure. */
    val minDaysBeforeDeparture: Int? = null,
    /** Promo fares disappear once the sailing is fuller than this. */
    val maxLoadFactor: Double? = null,
    val modificationFee: Double? = null,
)

@Serializable
data class BaggageAllowance(val seatKg: Int, val cabinKg: Int)

@Serializable
data class OperatorRules(
    val ageBands: AgeBands,
    val discounts: CategoryDiscounts,
    val tariffs: List<TariffDef>,
    val baggage: BaggageAllowance,
    val minAgeToTravelAlone: Int = 18,
    val minAgeAloneWithAuthorization: Int? = null,
    val roundTripDiscount: Double = 0.0,
    val infantsPayTaxes: Boolean = false,
    val onboardSurchargePercent: Double? = null,
    val maxPassengersPerBooking: Int = 9,
    val bookingCutoffHours: Int = 3,
    val maxPetsPerBooking: Int = 2,
)

@Serializable
data class Operator(
    val code: String,
    val name: String,
    val legalName: String? = null,
    val country: String,
    val website: String,
    val color: String,
    val active: Boolean,
    val publishedUntil: LocalDate? = null,
    val markets: LocalizedText,
    val description: LocalizedText,
    val rules: OperatorRules,
)

@Serializable
enum class AccommodationType(val berths: Int, val isCabin: Boolean) {
    SEAT(1, false),
    CABIN_INT_2(2, true),
    CABIN_INT_4(4, true),
    CABIN_EXT_2(2, true),
    CABIN_EXT_4(4, true),
    SUITE(2, true),
    CABIN_PET(4, true),
    CABIN_PMR(2, true),
}

@Serializable
enum class Amenity {
    RESTAURANT, CAFETERIA, SHOP, PRAYER_ROOM, WIFI, KIDS_AREA, PMR_ACCESS, INFIRMARY,
    PET_AREA, CINEMA, POOL, HALAL_FOOD, LOUNGE, ELEVATOR, CUSTOMS_ON_BOARD,
}

@Serializable
data class VesselCapacity(val passengers: Int, val vehicles: Int, val laneMeters: Int)

@Serializable
data class Vessel(
    val code: String,
    val name: String,
    val operator: String,
    val imo: String? = null,
    val mmsi: String? = null,
    val built: Int? = null,
    val lengthM: Double? = null,
    val speedKn: Double,
    val capacity: VesselCapacity,
    val accommodations: Map<AccommodationType, Int>,
    val petKennels: Int = 0,
    val maxVehicleHeightM: Double = 4.0,
    val maxVehicleLengthM: Double = 12.0,
    val amenities: Set<Amenity> = emptySet(),
    /** False when the identifiers or the assignment to Algerian lines could not be verified. */
    val verified: Boolean = true,
)

@Serializable
data class SeaLane(val id: String, val a: String, val b: String, val waypoints: List<List<Double>>)

@Serializable
data class Route(
    val id: String,
    val operator: String,
    val from: String,
    val to: String,
    val lane: String,
    val popular: Boolean = false,
)

@Serializable
enum class Season { SUMMER, WINTER, ALL }

@Serializable
enum class Weekday(val dayOfWeek: DayOfWeek) {
    MON(DayOfWeek.MONDAY), TUE(DayOfWeek.TUESDAY), WED(DayOfWeek.WEDNESDAY), THU(DayOfWeek.THURSDAY),
    FRI(DayOfWeek.FRIDAY), SAT(DayOfWeek.SATURDAY), SUN(DayOfWeek.SUNDAY),
}

/**
 * One crossing inside a vessel rotation. Conditions (weeks of month, parity) are evaluated
 * on the rotation's cycle start date so that an outbound leg and its return always stay paired.
 */
@Serializable
data class RotationLeg(
    val route: String,
    val dayOffset: Int,
    val departure: String,
    val durationMin: Int,
    val weeksOfMonth: List<Int>? = null,
    val months: List<Int>? = null,
    val weekParity: Int? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
)

@Serializable
data class Rotation(
    val id: String,
    val operator: String,
    /** Null means the operator has not announced the ship ("navire à confirmer"). */
    val vessel: String? = null,
    val season: Season,
    val startDay: Weekday,
    val legs: List<RotationLeg>,
)

@Serializable
enum class SailingStatus { SCHEDULED, DELAYED, CANCELLED, BOARDING, DEPARTED, ARRIVED }

@Serializable
enum class PriceSource { LIVE, REFERENCE }

/** Prices observed on the operator's channel by the ingestion pipeline. */
@Serializable
data class LivePrices(
    val currency: CurrencyCode,
    val adultSeat: Double? = null,
    val accommodation: Map<AccommodationType, Double> = emptyMap(),
    val vehicles: Map<VehicleCategory, Double> = emptyMap(),
)

@Serializable
enum class AvailabilityLevel { HIGH, MEDIUM, LOW, SOLD_OUT, UNKNOWN }

@Serializable
data class LiveAvailability(
    val seats: Int? = null,
    val cabins: Map<AccommodationType, Int> = emptyMap(),
    val laneMeters: Int? = null,
    val level: AvailabilityLevel = AvailabilityLevel.UNKNOWN,
)

@Serializable
data class Sailing(
    val id: String,
    val routeId: String,
    val operator: String,
    val vessel: String?,
    val from: String,
    val to: String,
    val departure: ZonedDateTime,
    val arrival: ZonedDateTime,
    val status: SailingStatus = SailingStatus.SCHEDULED,
    val delayMin: Int = 0,
    val source: PriceSource = PriceSource.REFERENCE,
    val fetchedAt: Instant? = null,
    val livePrices: LivePrices? = null,
    val liveAvailability: LiveAvailability? = null,
) {
    val durationMinutes: Long get() = java.time.Duration.between(departure, arrival).toMinutes()
    val departureDate: LocalDate get() = departure.toLocalDate()
}
