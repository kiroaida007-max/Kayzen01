@file:UseSerializers(LocalDateSerializer::class, ZonedDateTimeSerializer::class, InstantSerializer::class)

package dz.wave.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

@Serializable
data class LegSelection(
    val sailingId: String,
    val accommodation: AccommodationPref = AccommodationPref.SEAT,
    val tariff: TariffCode = TariffCode.STANDARD,
)

@Serializable
data class BookingSelection(
    val outbound: LegSelection,
    val inbound: LegSelection? = null,
    val passengers: PassengersInput,
    val vehicle: VehicleInput? = null,
    val pets: List<PetInput> = emptyList(),
    val accessibility: AccessibilityInput = AccessibilityInput(),
    val currency: CurrencyCode = CurrencyCode.DZD,
    val lang: Lang = Lang.FR,
)

@Serializable
enum class Sex { F, M }

@Serializable
enum class DocType { PASSPORT, ID_CARD, RESIDENCE_PERMIT }

@Serializable
data class TravelDocument(
    val type: DocType,
    val number: String,
    val expiry: LocalDate,
    val issuingCountry: String,
)

@Serializable
data class Traveller(
    val firstName: String,
    val lastName: String,
    val sex: Sex,
    val dateOfBirth: LocalDate,
    val nationality: String,
    val document: TravelDocument,
)

@Serializable
data class VehicleDetails(
    val plate: String,
    val make: String,
    val model: String,
    val registrationCountry: String,
)

@Serializable
data class ContactInfo(val email: String, val phone: String)

@Serializable
data class CreateBookingRequest(
    val selection: BookingSelection,
    val travellers: List<Traveller>,
    val vehicleDetails: VehicleDetails? = null,
    val contact: ContactInfo,
    val acceptTerms: Boolean = false,
    /** When present and different from the fresh price, the API answers 409 PRICE_CHANGED. */
    val expectedTotal: Money? = null,
)

@Serializable
data class LegQuote(
    val sailingId: String,
    val routeId: String,
    val operator: String,
    val vessel: String?,
    val from: String,
    val to: String,
    val departure: ZonedDateTime,
    val arrival: ZonedDateTime,
    val tariff: TariffDef,
    val accommodation: AccommodationPref,
    val cabins: List<CabinAllocation>,
    val lines: List<PriceLine>,
    val nativeTotal: Money,
    val total: Money,
    val priceSource: PriceSource,
)

@Serializable
data class Quote(
    val legs: List<LegQuote>,
    val adjustments: List<PriceLine>,
    val total: Money,
    val currency: CurrencyCode,
    val rates: RateInfo,
    val promotions: List<String>,
    val notices: List<Violation>,
    val createdAt: Instant,
    /** Total converted into every currency a payment method may charge (DZD for CIB/Edahabia). */
    val payable: Map<CurrencyCode, Money> = emptyMap(),
)

@Serializable
enum class BookingStatus { HELD, CONFIRMED, TICKETED, CANCELLED, EXPIRED, REFUNDED }

@Serializable
enum class PaymentMethod { CIB, EDAHABIA, CARD, AGENCY, SANDBOX }

@Serializable
enum class PaymentStatus { PENDING, APPROVED, DECLINED, CANCELLED, REFUND_PENDING, REFUNDED }

@Serializable
data class PaymentRecord(
    val id: String,
    val method: PaymentMethod,
    val provider: String,
    val providerOrderId: String? = null,
    val status: PaymentStatus,
    val amount: Money,
    val createdAt: Instant,
    val updatedAt: Instant,
    val message: String? = null,
)

@Serializable
data class TicketInfo(val qrPayload: String, val issuedAt: Instant)

@Serializable
data class CancellationResult(
    val fee: Money,
    val refund: Money,
    val cancelledAt: Instant,
    val perLeg: List<PriceLine>,
)

@Serializable
data class Booking(
    val id: String,
    val reference: String,
    val status: BookingStatus,
    val userId: String? = null,
    val contact: ContactInfo,
    val selection: BookingSelection,
    val travellers: List<Traveller>,
    val vehicleDetails: VehicleDetails? = null,
    val quote: Quote,
    val holdId: String? = null,
    val holdExpiresAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int = 0,
    val payments: List<PaymentRecord> = emptyList(),
    val ticket: TicketInfo? = null,
    val operatorReferences: Map<String, String> = emptyMap(),
    val cancellation: CancellationResult? = null,
)
