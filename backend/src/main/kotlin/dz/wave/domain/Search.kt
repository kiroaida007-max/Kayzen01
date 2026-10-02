@file:UseSerializers(LocalDateSerializer::class, ZonedDateTimeSerializer::class, InstantSerializer::class)

package dz.wave.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Adults are 18–59, seniors 60+, and every minor is declared with an exact age (0–17). */
@Serializable
data class PassengersInput(
    val adults: Int = 1,
    val seniors: Int = 0,
    val childrenAges: List<Int> = emptyList(),
) {
    val grownUps: Int get() = adults + seniors
    val total: Int get() = adults + seniors + childrenAges.size

    /** Ages used for fare classification: adults are assumed 30, seniors 65. */
    fun ages(): List<Int> = List(adults) { 30 } + List(seniors) { 65 } + childrenAges
}

/** What the traveller says they drive; [VehicleCategory] is derived from it per operator. */
@Serializable
enum class VehicleType { CAR, SUV, MOTORCYCLE, CAMPER, VAN }

@Serializable
data class VehicleInput(
    val type: VehicleType,
    val lengthM: Double? = null,
    val heightM: Double? = null,
    val withTrailer: Boolean = false,
    val trailerLengthM: Double? = null,
    val roofBox: Boolean = false,
    val registrationYear: Int? = null,
)

@Serializable
enum class PetType { DOG, CAT, OTHER }

@Serializable
enum class PetPlacement { KENNEL, CABIN, ANY }

@Serializable
data class PetInput(val type: PetType, val count: Int = 1, val placement: PetPlacement = PetPlacement.ANY)

@Serializable
data class AccessibilityInput(
    val wheelchair: Boolean = false,
    val reducedMobility: Boolean = false,
    val assistance: Boolean = false,
) {
    val any: Boolean get() = wheelchair || reducedMobility || assistance
}

@Serializable
enum class AccommodationPref { SEAT, CABIN_ANY, CABIN_INTERIOR, CABIN_EXTERIOR, SUITE }

@Serializable
enum class TripType { ONE_WAY, ROUND_TRIP }

@Serializable
enum class SortOrder { PRICE, DEPARTURE, DURATION }

@Serializable
data class SearchRequest(
    val tripType: TripType = TripType.ONE_WAY,
    val from: String,
    val to: String,
    val departureDate: LocalDate,
    val returnDate: LocalDate? = null,
    val passengers: PassengersInput = PassengersInput(),
    val vehicle: VehicleInput? = null,
    val accommodation: AccommodationPref = AccommodationPref.SEAT,
    val pets: List<PetInput> = emptyList(),
    val accessibility: AccessibilityInput = AccessibilityInput(),
    val currency: CurrencyCode = CurrencyCode.DZD,
    val flexDays: Int = 0,
    val operators: List<String>? = null,
    val sort: SortOrder = SortOrder.PRICE,
    val lang: Lang = Lang.FR,
)

@Serializable
data class Violation(
    val code: String,
    val severity: RuleSeverity,
    val message: String,
    val field: String? = null,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class PortRef(val code: String, val name: LocalizedText, val country: String)

@Serializable
data class OperatorRef(val code: String, val name: String, val color: String)

@Serializable
data class VesselRef(
    val code: String,
    val name: String,
    val amenities: Set<Amenity>,
    val verified: Boolean,
)

@Serializable
enum class PriceLineKind { PASSAGE, ACCOMMODATION, VEHICLE, PET, TAX, DISCOUNT, FEE }

@Serializable
data class PriceLine(
    val kind: PriceLineKind,
    val label: String,
    val quantity: Int,
    val unit: Money,
    val total: Money,
)

@Serializable
data class CabinAllocation(val type: AccommodationType, val count: Int)

@Serializable
data class TariffOffer(
    val code: TariffCode,
    val name: String,
    val total: Money,
    val totalNative: Money,
    val refundable: Boolean,
    val modifiable: Boolean,
    val conditions: String,
)

@Serializable
data class AvailabilitySummary(
    val level: AvailabilityLevel,
    val seatsLeft: Int? = null,
    val cabinsLeft: Int? = null,
    val vehicleSpaceLeftM: Int? = null,
)

@Serializable
data class SailingOffer(
    val sailingId: String,
    val routeId: String,
    val operator: OperatorRef,
    val vessel: VesselRef?,
    val from: PortRef,
    val to: PortRef,
    val departure: ZonedDateTime,
    val arrival: ZonedDateTime,
    val departureZone: String,
    val arrivalZone: String,
    val durationMin: Long,
    val status: SailingStatus,
    val priceSource: PriceSource,
    val lastUpdated: Instant?,
    val bookable: Boolean,
    val reasons: List<Violation>,
    val notes: List<Violation>,
    val availability: AvailabilitySummary,
    val accommodation: AccommodationPref,
    val cabins: List<CabinAllocation>,
    val cheapest: TariffOffer?,
    val tariffs: List<TariffOffer>,
    val breakdown: List<PriceLine>,
    val promotions: List<String>,
)

@Serializable
data class DayPrice(val date: LocalDate, val minPrice: Money?, val sailings: Int)

@Serializable
data class LegResults(val date: LocalDate, val offers: List<SailingOffer>, val nearbyDays: List<DayPrice>)

@Serializable
data class RateInfo(
    val base: CurrencyCode,
    /** Dinars per one unit of each currency, as decimal strings. */
    val dzdPerUnit: Map<CurrencyCode, String>,
    val asOf: Instant,
    val source: String,
)

@Serializable
data class SearchResponse(
    val searchId: String,
    val currency: CurrencyCode,
    val outbound: LegResults,
    val inbound: LegResults?,
    val notices: List<Violation>,
    val rates: RateInfo,
    val generatedAt: Instant,
)
