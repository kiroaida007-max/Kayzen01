@file:UseSerializers(LocalDateSerializer::class, InstantSerializer::class, ZonedDateTimeSerializer::class)

package dz.wave.api

import dz.wave.catalog.CatalogSnapshot
import dz.wave.currency.CurrencyService
import dz.wave.domain.AccommodationType
import dz.wave.domain.AgeBands
import dz.wave.domain.Amenity
import dz.wave.domain.BaggageAllowance
import dz.wave.domain.Booking
import dz.wave.domain.BookingStatus
import dz.wave.domain.CancellationResult
import dz.wave.domain.ContactInfo
import dz.wave.domain.CurrencyCode
import dz.wave.domain.DocType
import dz.wave.domain.InstantSerializer
import dz.wave.domain.Lang
import dz.wave.domain.LegQuote
import dz.wave.domain.LocalDateSerializer
import dz.wave.domain.LocalizedText
import dz.wave.domain.Money
import dz.wave.domain.PaymentMethod
import dz.wave.domain.PaymentRecord
import dz.wave.domain.PriceLine
import dz.wave.domain.RateInfo
import dz.wave.domain.Sex
import dz.wave.domain.TariffCode
import dz.wave.domain.TicketInfo
import dz.wave.domain.VehicleDetails
import dz.wave.domain.VesselCapacity
import dz.wave.domain.Violation
import dz.wave.domain.ZonedDateTimeSerializer
import dz.wave.pricing.Labels
import dz.wave.rules.SearchValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

@Serializable
data class PortDto(
    val code: String,
    val shortCode: String,
    val name: LocalizedText,
    val country: String,
    val lat: Double,
    val lon: Double,
    val zone: String,
    val terminal: String?,
    val destinations: List<String>,
)

@Serializable
data class TariffDto(val code: TariffCode, val name: LocalizedText, val refundable: Boolean, val modifiable: Boolean, val conditions: LocalizedText)

@Serializable
data class OperatorDto(
    val code: String,
    val name: String,
    val color: String,
    val website: String,
    val country: String,
    val active: Boolean,
    val publishedUntil: LocalDate?,
    val markets: LocalizedText,
    val description: LocalizedText,
    val ageBands: AgeBands,
    val baggage: BaggageAllowance,
    val tariffs: List<TariffDto>,
    val routes: Int,
    val vessels: List<String>,
)

@Serializable
data class VesselDto(
    val code: String,
    val name: String,
    val operator: String,
    val imo: String?,
    val mmsi: String?,
    val built: Int?,
    val lengthM: Double?,
    val speedKn: Double,
    val capacity: VesselCapacity,
    val accommodations: List<AccommodationType>,
    val petFriendly: Boolean,
    val amenities: Set<Amenity>,
    val verified: Boolean,
)

@Serializable
data class RouteSummary(
    val id: String,
    val operator: String,
    val from: String,
    val to: String,
    val typicalDurationMin: Long?,
    val departuresPerWeek: Double,
    val nextDeparture: ZonedDateTime?,
    val fromPrice: Money,
    val fromPriceDzd: Money,
    val popular: Boolean,
    val polyline: List<List<Double>>,
)

@Serializable
data class PopularRoute(
    val from: String,
    val to: String,
    val image: String,
    val operators: List<String>,
    val minDurationMin: Long?,
    val maxDurationMin: Long?,
    val fromPriceDzd: Money?,
)

@Serializable
data class Limits(
    val maxPassengers: Int = SearchValidator.MAX_PASSENGERS,
    val maxPets: Int = SearchValidator.MAX_PETS,
    val maxFlexDays: Int = SearchValidator.MAX_FLEX_DAYS,
    val maxDaysAhead: Long = SearchValidator.MAX_DAYS_AHEAD,
    val maxVehicleHeightM: Double = SearchValidator.MAX_VEHICLE_HEIGHT_M,
    val maxVehicleLengthM: Double = SearchValidator.MAX_VEHICLE_LENGTH_M,
)

@Serializable
data class RegulationDto(val id: String, val title: LocalizedText, val description: LocalizedText, val from: String, val to: String, val source: String)

@Serializable
data class MetaResponse(
    val version: String,
    val ports: List<PortDto>,
    val operators: List<OperatorDto>,
    val vessels: List<VesselDto>,
    val routes: List<RouteSummary>,
    val popular: List<PopularRoute>,
    val rates: RateInfo,
    val limits: Limits,
    val paymentMethods: List<PaymentMethod>,
    val regulations: List<RegulationDto>,
    val generatedAt: Instant,
)

@Serializable
data class TravellerView(
    val firstName: String,
    val lastName: String,
    val sex: Sex,
    val dateOfBirth: LocalDate,
    val nationality: String,
    val documentType: DocType,
    val documentNumber: String,
    val documentExpiry: LocalDate,
)

@Serializable
data class BookingView(
    val reference: String,
    val status: BookingStatus,
    val contact: ContactInfo,
    val legs: List<LegQuote>,
    val adjustments: List<PriceLine>,
    val total: Money,
    val currency: CurrencyCode,
    val payable: Map<CurrencyCode, Money>,
    val travellers: List<TravellerView>,
    val vehicle: VehicleDetails?,
    val holdExpiresAt: Instant?,
    val createdAt: Instant,
    val payments: List<PaymentRecord>,
    val ticket: TicketInfo?,
    val cancellation: CancellationResult?,
    val notices: List<Violation>,
    val operatorReferences: Map<String, String>,
)

@Serializable
data class PaymentRequest(val method: PaymentMethod, val lastName: String, val lang: Lang = Lang.FR)

@Serializable
data class CancelRequest(val lastName: String, val dryRun: Boolean = true, val lang: Lang = Lang.FR)

@Serializable
data class TicketedRequest(val operatorReferences: Map<String, String>)

@Serializable
data class VerifyTicketRequest(val payload: String)

@Serializable
data class VerifyTicketResponse(val valid: Boolean, val reference: String?, val status: BookingStatus?, val passengers: Int?)

@Serializable
data class HealthResponse(val status: String, val catalogVersion: Long, val sailings: Int, val liveSailings: Int)

/** Masks document numbers: support staff and screenshots never expose a full passport number. */
fun Booking.toView(): BookingView = BookingView(
    reference = reference,
    status = status,
    contact = contact,
    legs = quote.legs,
    adjustments = quote.adjustments,
    total = quote.total,
    currency = quote.currency,
    payable = quote.payable,
    travellers = travellers.map {
        TravellerView(
            firstName = it.firstName,
            lastName = it.lastName,
            sex = it.sex,
            dateOfBirth = it.dateOfBirth,
            nationality = it.nationality,
            documentType = it.document.type,
            documentNumber = "•••• " + it.document.number.takeLast(3),
            documentExpiry = it.document.expiry,
        )
    },
    vehicle = vehicleDetails,
    holdExpiresAt = holdExpiresAt,
    createdAt = createdAt,
    payments = payments,
    ticket = ticket,
    cancellation = cancellation,
    notices = quote.notices,
    operatorReferences = operatorReferences,
)

object MetaBuilder {
    fun build(
        snap: CatalogSnapshot,
        currency: CurrencyService,
        popular: List<dz.wave.content.PopularRouteDef>,
        paymentMethods: List<PaymentMethod>,
        today: LocalDate,
        now: Instant,
    ): MetaResponse {
        val rates = currency.current()
        val routes = snap.data.routes.filter { snap.operators[it.operator]?.active == true }.map { route ->
            val upcoming = snap.sailings(route.id, today, today.plusWeeks(8))
            val table = snap.fareTables.getValue(route.id)
            val from = Money.of(table.adultSeat + table.taxes.perPassenger, table.currency).roundedForSale()
            val durations = upcoming.map { it.durationMinutes }.sorted()
            RouteSummary(
                id = route.id,
                operator = route.operator,
                from = route.from,
                to = route.to,
                typicalDurationMin = durations.getOrNull(durations.size / 2),
                departuresPerWeek = Math.round(upcoming.size / 8.0 * 10) / 10.0,
                nextDeparture = upcoming.firstOrNull { it.departure.toInstant().isAfter(now) }?.departure,
                fromPrice = from,
                fromPriceDzd = currency.convert(from, CurrencyCode.DZD, rates),
                popular = route.popular,
                polyline = snap.geometry(route.id).points.map { listOf(it.lat, it.lon) },
            )
        }
        val popularRoutes = popular.map { p ->
            val matching = routes.filter { it.from == p.from && it.to == p.to }
            PopularRoute(
                from = p.from,
                to = p.to,
                image = p.image,
                // The featured company comes first (the one shown on the home card).
                operators = matching.map { it.operator }.distinct().sortedBy { if (it == p.operator) 0 else 1 },
                minDurationMin = matching.mapNotNull { it.typicalDurationMin }.minOrNull(),
                maxDurationMin = matching.mapNotNull { it.typicalDurationMin }.maxOrNull(),
                fromPriceDzd = matching.map { it.fromPriceDzd }.minByOrNull { it.minor },
            )
        }
        return MetaResponse(
            version = "${snap.version}.${rates.version}",
            ports = snap.data.ports.map { p ->
                PortDto(p.code, p.shortCode, p.name, p.country, p.lat, p.lon, p.zone, p.terminal, snap.destinationsFrom(p.code).sorted())
            },
            operators = snap.data.operators.map { op ->
                OperatorDto(
                    code = op.code,
                    name = op.name,
                    color = op.color,
                    website = op.website,
                    country = op.country,
                    active = op.active,
                    publishedUntil = op.publishedUntil,
                    markets = op.markets,
                    description = op.description,
                    ageBands = op.rules.ageBands,
                    baggage = op.rules.baggage,
                    tariffs = op.rules.tariffs.map { t ->
                        TariffDto(
                            t.code, t.name, t.refundable, t.modifiable,
                            LocalizedText(Labels.policy(t, Lang.FR), Labels.policy(t, Lang.EN), Labels.policy(t, Lang.AR)),
                        )
                    },
                    routes = routes.count { it.operator == op.code },
                    vessels = snap.data.vessels.filter { it.operator == op.code }.map { it.code },
                )
            },
            vessels = snap.data.vessels.map { v ->
                VesselDto(
                    v.code, v.name, v.operator, v.imo, v.mmsi, v.built, v.lengthM, v.speedKn, v.capacity,
                    v.accommodations.filterValues { it > 0 }.keys.toList(),
                    v.petKennels > 0 || (v.accommodations[AccommodationType.CABIN_PET] ?: 0) > 0,
                    v.amenities, v.verified,
                )
            },
            routes = routes,
            popular = popularRoutes,
            rates = rates.info(),
            limits = Limits(),
            paymentMethods = paymentMethods,
            regulations = snap.data.regulations.map { RegulationDto(it.id, it.title, it.description, it.from, it.to, it.source) },
            generatedAt = now,
        )
    }
}
