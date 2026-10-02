package dz.wave.booking

import dz.wave.common.AppJson
import dz.wave.common.ConflictException
import dz.wave.common.NotFoundException
import dz.wave.common.ValidationException
import dz.wave.domain.Booking
import dz.wave.domain.BookingSelection
import dz.wave.domain.BookingStatus
import dz.wave.domain.CancellationResult
import dz.wave.domain.ContactInfo
import dz.wave.domain.CreateBookingRequest
import dz.wave.domain.DocType
import dz.wave.domain.Lang
import dz.wave.domain.Money
import dz.wave.domain.PassengersInput
import dz.wave.domain.PaymentRecord
import dz.wave.domain.PaymentStatus
import dz.wave.domain.PriceLine
import dz.wave.domain.PriceLineKind
import dz.wave.domain.RuleSeverity
import dz.wave.domain.TicketInfo
import dz.wave.domain.Traveller
import dz.wave.domain.VehicleDetails
import dz.wave.domain.Violation
import dz.wave.pricing.CancellationPolicy
import dz.wave.pricing.PricingEngine
import dz.wave.rules.Messages
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.util.Locale
import java.util.UUID

class BookingService(
    private val quotes: QuoteService,
    private val holds: HoldStore,
    private val repository: BookingRepository,
    private val ticketSigner: TicketSigner,
    private val clock: Clock,
    private val holdTtl: Duration = Duration.ofMinutes(20),
    private val agencyHoldTtl: Duration = Duration.ofHours(24),
) {
    private val log = LoggerFactory.getLogger(BookingService::class.java)

    suspend fun create(request: CreateBookingRequest, userId: String?): Booking {
        val lang = request.selection.lang
        val problems = validateTravellers(request)
        if (problems.any { it.severity == RuleSeverity.ERROR }) {
            throw ValidationException(problems.filter { it.severity == RuleSeverity.ERROR })
        }
        // Prices follow the travellers' real ages, which may differ from what was searched.
        val selection = request.selection.copy(passengers = passengersFromTravellers(request))
        val result = quotes.quote(selection)
        val quote = result.quote.copy(notices = result.quote.notices + problems)

        val expected = request.expectedTotal
        if (expected != null && expected != quote.total) {
            throw ConflictException(
                "PRICE_CHANGED",
                Messages.text("PRICE_CHANGED", lang, mapOf("total" to quote.total.toString())),
                buildJsonObject {
                    put("quote", AppJson.encodeToJsonElement(dz.wave.domain.Quote.serializer(), quote))
                    put("total", JsonPrimitive(quote.total.minor))
                },
            )
        }

        val holdId = UUID.randomUUID().toString()
        if (!holds.place(holdId, result.holds, holdTtl)) {
            throw ConflictException("SOLD_OUT_DURING_BOOKING", Messages.text("SOLD_OUT_DURING_BOOKING", lang))
        }
        val now = clock.instant()
        val booking = Booking(
            id = UUID.randomUUID().toString(),
            reference = uniqueReference(),
            status = BookingStatus.HELD,
            userId = userId,
            contact = normaliseContact(request.contact),
            selection = selection,
            travellers = request.travellers.map(::normaliseTraveller),
            vehicleDetails = request.vehicleDetails?.let(::normaliseVehicle),
            quote = quote,
            holdId = holdId,
            holdExpiresAt = now.plus(holdTtl),
            createdAt = now,
            updatedAt = now,
            version = 0,
        )
        try {
            repository.insert(booking)
        } catch (e: Exception) {
            holds.release(holdId)
            throw e
        }
        log.info("Booking {} held ({} legs, {})", booking.reference, quote.legs.size, quote.total)
        return booking
    }

    /** Lookup by reference + any traveller's last name, the classic airline/ferry "manage booking". */
    suspend fun find(reference: String, lastName: String, lang: Lang): Booking {
        val ref = reference.trim().uppercase()
        val booking = if (BookingReferences.isWellFormed(ref)) repository.findByReference(ref) else null
        val name = lastName.trim().uppercase(Locale.ROOT)
        if (booking == null || booking.travellers.none { it.lastName == name }) {
            throw NotFoundException("BOOKING_NOT_FOUND", Messages.text("BOOKING_NOT_FOUND", lang))
        }
        return booking
    }

    suspend fun findByReference(reference: String): Booking? = repository.findByReference(reference.uppercase())

    suspend fun listForUser(userId: String): List<Booking> = repository.findByUser(userId)

    suspend fun listByStatus(status: BookingStatus, limit: Int): List<Booking> = repository.findByStatus(status, limit)

    suspend fun addPayment(booking: Booking, payment: PaymentRecord, extendForAgency: Boolean): Booking {
        val now = clock.instant()
        if (extendForAgency) booking.holdId?.let { holds.extend(it, agencyHoldTtl) }
        val updated = booking.copy(
            payments = booking.payments.filter { it.id != payment.id } + payment,
            holdExpiresAt = if (extendForAgency) now.plus(agencyHoldTtl) else booking.holdExpiresAt,
            updatedAt = now,
            version = booking.version + 1,
        )
        return save(updated)
    }

    /** Called once the payment provider confirmed the money: keep the inventory and issue the e-ticket. */
    suspend fun confirmPayment(booking: Booking, payment: PaymentRecord): Booking {
        if (booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.TICKETED) return booking
        if (booking.status != BookingStatus.HELD) {
            throw ConflictException("BOOKING_NOT_PAYABLE", Messages.text("BOOKING_NOT_PAYABLE", Lang.FR, mapOf("status" to booking.status.name)))
        }
        val now = clock.instant()
        booking.holdId?.let { holds.commit(it) }
        val updated = booking.copy(
            status = BookingStatus.CONFIRMED,
            payments = booking.payments.filter { it.id != payment.id } + payment,
            holdExpiresAt = null,
            ticket = TicketInfo(ticketSigner.sign(booking.reference, now), now),
            updatedAt = now,
            version = booking.version + 1,
        )
        log.info("Booking {} confirmed", booking.reference)
        return save(updated)
    }

    suspend fun markTicketed(reference: String, operatorReferences: Map<String, String>): Booking {
        val booking = repository.findByReference(reference)
            ?: throw NotFoundException("BOOKING_NOT_FOUND", Messages.text("BOOKING_NOT_FOUND", Lang.FR))
        if (booking.status != BookingStatus.CONFIRMED && booking.status != BookingStatus.TICKETED) {
            throw ConflictException("BOOKING_NOT_TICKETABLE", "Booking must be confirmed before ticketing")
        }
        return save(
            booking.copy(
                status = BookingStatus.TICKETED,
                operatorReferences = booking.operatorReferences + operatorReferences,
                updatedAt = clock.instant(),
                version = booking.version + 1,
            ),
        )
    }

    /** Computes (and unless [dryRun] applies) the refund according to each leg's tariff conditions. */
    suspend fun cancel(booking: Booking, lang: Lang, dryRun: Boolean): CancellationResult {
        if (booking.status !in setOf(BookingStatus.HELD, BookingStatus.CONFIRMED, BookingStatus.TICKETED)) {
            throw ConflictException(
                "BOOKING_NOT_CANCELLABLE",
                Messages.text("BOOKING_NOT_CANCELLABLE", lang, mapOf("status" to booking.status.name)),
            )
        }
        val now = clock.instant()
        val currency = booking.quote.currency
        val paid = booking.payments.filter { it.status == PaymentStatus.APPROVED }
        val perLeg = booking.quote.legs.map { leg ->
            val pct = CancellationPolicy.feePercent(leg.tariff, leg.departure, booking.createdAt, now)
            val fee = leg.total.scale(BigDecimal.valueOf(pct)).roundedForSale()
            PricingEngine.line(PriceLineKind.FEE, "${leg.from} → ${leg.to} (${(pct * 100).toInt()} %)", 1, fee)
        }
        val nonRefundable = booking.quote.adjustments.filter { it.kind == PriceLineKind.FEE }.map { it.total }
        val fee = Money.sum(perLeg.map { it.total } + nonRefundable, currency)
        val paidTotal = if (paid.isEmpty()) Money.zero(currency) else booking.quote.total
        val refund = (paidTotal - fee).let { if (it.isNegative) Money.zero(currency) else it }
        val result = CancellationResult(fee = fee, refund = refund, cancelledAt = now, perLeg = perLeg)
        if (!dryRun) {
            booking.holdId?.let { holds.release(it) }
            val refunded = booking.payments.map {
                if (it.status == PaymentStatus.APPROVED && !refund.isZero) it.copy(status = PaymentStatus.REFUND_PENDING, updatedAt = now) else it
            }
            save(
                booking.copy(
                    status = BookingStatus.CANCELLED,
                    cancellation = result,
                    payments = refunded,
                    holdExpiresAt = null,
                    updatedAt = now,
                    version = booking.version + 1,
                ),
            )
            log.info("Booking {} cancelled, refund {}", booking.reference, refund)
        }
        return result
    }

    /** Releases inventory of unpaid bookings whose hold expired. Safe to run on every pod. */
    suspend fun expireHolds(): Int {
        val now = clock.instant()
        var count = 0
        repository.findExpiredHolds(now, 200).forEach { booking ->
            booking.holdId?.let { holds.release(it) }
            val saved = repository.update(
                booking.copy(status = BookingStatus.EXPIRED, holdExpiresAt = null, updatedAt = now, version = booking.version + 1),
            )
            if (saved) count++
        }
        holds.releaseExpired(now)
        return count
    }

    private suspend fun save(booking: Booking): Booking {
        if (!repository.update(booking)) {
            throw ConflictException("CONCURRENT_UPDATE", "The booking was modified concurrently, please retry")
        }
        return booking
    }

    private suspend fun uniqueReference(): String {
        repeat(10) {
            val ref = BookingReferences.next()
            if (repository.findByReference(ref) == null) return ref
        }
        error("Could not allocate a booking reference")
    }

    // ---------------------------------------------------------------- traveller validation

    private fun passengersFromTravellers(request: CreateBookingRequest): PassengersInput {
        val departure = departureDate(request.selection)
        val ages = request.travellers.map { Period.between(it.dateOfBirth, departure).years }
        return PassengersInput(
            adults = ages.count { it in 18..59 },
            seniors = ages.count { it >= 60 },
            childrenAges = ages.filter { it < 18 }.sorted(),
        )
    }

    private fun departureDate(selection: BookingSelection): LocalDate =
        selection.outbound.sailingId.split('-').getOrNull(3)
            ?.let { LocalDate.parse(it, java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }
            ?: LocalDate.now(clock)

    private fun validateTravellers(request: CreateBookingRequest): List<Violation> {
        val lang = request.selection.lang
        val out = mutableListOf<Violation>()
        fun add(code: String, field: String, params: Map<String, String> = emptyMap(), severity: RuleSeverity = RuleSeverity.ERROR) {
            out += Messages.violation(code, severity, lang, field, params)
        }
        if (!request.acceptTerms) add("TERMS_NOT_ACCEPTED", "acceptTerms")
        val expected = request.selection.passengers.total
        if (request.travellers.size != expected) {
            add("TRAVELLER_COUNT_MISMATCH", "travellers", mapOf("count" to expected.toString()))
        }
        val tripStart = departureDate(request.selection)
        val tripEnd = request.selection.inbound?.sailingId?.split('-')?.getOrNull(3)
            ?.let { LocalDate.parse(it, java.time.format.DateTimeFormatter.BASIC_ISO_DATE).plusDays(2) }
            ?: tripStart.plusDays(2)
        val today = LocalDate.now(clock)
        request.travellers.forEachIndexed { i, t ->
            val field = "travellers[$i]"
            val name = "${t.firstName} ${t.lastName}".trim()
            if (!NAME.matches(t.firstName.trim()) || !NAME.matches(t.lastName.trim())) add("NAME_INVALID", field)
            if (t.dateOfBirth.isAfter(today) || t.dateOfBirth.isBefore(today.minusYears(120))) {
                add("DOB_INVALID", "$field.dateOfBirth", mapOf("name" to name))
            }
            if (t.nationality.uppercase() !in ISO_COUNTRIES) add("NATIONALITY_INVALID", "$field.nationality")
            if (t.document.type != DocType.PASSPORT) add("DOC_ID_CARD_NOT_ACCEPTED", "$field.document.type", mapOf("name" to name))
            if (!DOC_NUMBER.matches(t.document.number.uppercase().replace(" ", ""))) {
                add("DOC_NUMBER_INVALID", "$field.document.number", mapOf("name" to name))
            }
            if (t.document.issuingCountry.uppercase() !in ISO_COUNTRIES) add("NATIONALITY_INVALID", "$field.document.issuingCountry")
            if (!t.document.expiry.isAfter(tripEnd)) {
                add("DOC_EXPIRED", "$field.document.expiry", mapOf("name" to name))
            } else if (t.document.expiry.isBefore(tripEnd.plusMonths(6))) {
                add("DOC_EXPIRES_SOON", "$field.document.expiry", mapOf("name" to name), RuleSeverity.WARNING)
            }
        }
        val adultsByDob = request.travellers.count { Period.between(it.dateOfBirth, tripStart).years >= 18 }
        if (request.travellers.isNotEmpty() && adultsByDob == 0) add("PAX_ADULT_REQUIRED", "travellers")
        if (!EMAIL.matches(request.contact.email.trim()) || request.contact.email.length > 254) add("EMAIL_INVALID", "contact.email")
        if (normalisePhone(request.contact.phone) == null) add("PHONE_INVALID", "contact.phone")
        if (request.selection.vehicle != null) {
            val v = request.vehicleDetails
            if (v == null || v.make.isBlank() || v.model.isBlank()) {
                add("VEHICLE_DETAILS_REQUIRED", "vehicleDetails")
            } else if (!PLATE.matches(v.plate.uppercase().trim())) {
                add("PLATE_INVALID", "vehicleDetails.plate")
            }
        }
        return out
    }

    private fun normaliseTraveller(t: Traveller) = t.copy(
        firstName = t.firstName.trim().uppercase(Locale.ROOT),
        lastName = t.lastName.trim().uppercase(Locale.ROOT),
        nationality = t.nationality.uppercase(),
        document = t.document.copy(
            number = t.document.number.uppercase().replace(" ", ""),
            issuingCountry = t.document.issuingCountry.uppercase(),
        ),
    )

    private fun normaliseVehicle(v: VehicleDetails) = v.copy(
        plate = v.plate.trim().uppercase(),
        make = v.make.trim(),
        model = v.model.trim(),
        registrationCountry = v.registrationCountry.uppercase(),
    )

    private fun normaliseContact(c: ContactInfo) = ContactInfo(
        email = c.email.trim().lowercase(),
        phone = normalisePhone(c.phone) ?: c.phone,
    )

    companion object {
        private val NAME = Regex("^(?=.*\\p{L})[\\p{L}' -]{1,50}$")
        private val DOC_NUMBER = Regex("^[A-Z0-9]{5,20}$")
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$")
        private val PLATE = Regex("^[A-Z0-9][A-Z0-9 -]{1,14}$")
        private val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toSet()

        /** Algerian mobiles (05/06/07…) become +213; anything else must already be international. */
        fun normalisePhone(raw: String): String? {
            val compact = raw.replace(Regex("[\\s.()-]"), "")
            return when {
                Regex("^0[567]\\d{8}$").matches(compact) -> "+213" + compact.drop(1)
                Regex("^\\+\\d{8,15}$").matches(compact) -> compact
                Regex("^00\\d{8,15}$").matches(compact) -> "+" + compact.drop(2)
                else -> null
            }
        }

        fun lineTotal(lines: List<PriceLine>) = lines.fold(0L) { acc, l -> acc + l.total.minor }
    }
}
