@file:UseSerializers(LocalDateSerializer::class)

package dz.wave.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/** Pricing class of a vehicle, derived from what the traveller declares (type + dimensions). */
@Serializable
enum class VehicleCategory { CAR, CAR_HIGH, MOTORCYCLE, CAMPER, CAR_TRAILER, VAN }

@Serializable
enum class Direction { TO_DZ, FROM_DZ, ANY }

@Serializable
data class Taxes(val perPassenger: Double, val perVehicle: Double)

@Serializable
data class FareTableDef(
    val id: String,
    val routes: List<String>,
    val currency: CurrencyCode? = null,
    val extends: String? = null,
    val scale: Double = 1.0,
    val adultSeat: Double? = null,
    val accommodation: Map<AccommodationType, Double>? = null,
    val vehicles: Map<VehicleCategory, Double>? = null,
    val pet: Double? = null,
    val taxes: Taxes? = null,
)

/** Fully resolved reference fares of one route, in the operator's selling currency. */
data class FareTable(
    val id: String,
    val currency: CurrencyCode,
    val adultSeat: Double,
    val accommodation: Map<AccommodationType, Double>,
    val vehicles: Map<VehicleCategory, Double>,
    val pet: Double,
    val taxes: Taxes,
)

/**
 * Seasonal multiplier. [from]/[to] are either `MM-dd` (every year, may wrap around new year)
 * or `yyyy-MM-dd` (one-off events such as Eid).
 */
@Serializable
data class SeasonRule(
    val name: String,
    val from: String,
    val to: String,
    val direction: Direction,
    val multiplier: Double,
)

@Serializable
data class PromotionConditions(
    val requiresChildAgeRange: List<Int>? = null,
    val requiresVehicle: Boolean = false,
    val requiresCabin: Boolean = false,
    val requiresRoundTrip: Boolean = false,
    val maxVehicleLengthM: Double? = null,
    val maxVehicleHeightM: Double? = null,
    val online: Boolean = false,
)

@Serializable
data class Promotion(
    val id: String,
    val operator: String? = null,
    val title: LocalizedText,
    val description: LocalizedText,
    val discount: Double,
    val bookFrom: LocalDate? = null,
    val bookTo: LocalDate? = null,
    val travelFrom: LocalDate? = null,
    val travelTo: LocalDate? = null,
    val conditions: PromotionConditions = PromotionConditions(),
    /** True when the operator communicates the offer without a precise percentage. */
    val estimated: Boolean = false,
    val badge: String? = null,
    val image: String? = null,
    val source: String? = null,
)

@Serializable
enum class RuleSeverity { ERROR, WARNING, INFO }

@Serializable
data class RegulationEffect(
    val bannedVehicleTypes: List<VehicleType> = emptyList(),
    val newVehicleMaxAgeYears: Int? = null,
)

/** Date-ranged rule imposed by authorities (e.g. summer vehicle restrictions in Algerian ports). */
@Serializable
data class Regulation(
    val id: String,
    val title: LocalizedText,
    val description: LocalizedText,
    val from: String,
    val to: String,
    val years: List<Int>? = null,
    /** Algerian ports where it applies; empty = every Algerian port. */
    val ports: List<String> = emptyList(),
    val direction: Direction,
    val effect: RegulationEffect,
    val severity: RuleSeverity,
    val source: String,
)
