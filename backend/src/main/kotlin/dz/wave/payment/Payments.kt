@file:UseSerializers(InstantSerializer::class)

package dz.wave.payment

import dz.wave.booking.BookingService
import dz.wave.common.ConflictException
import dz.wave.common.NotFoundException
import dz.wave.domain.Booking
import dz.wave.domain.BookingStatus
import dz.wave.domain.CurrencyCode
import dz.wave.domain.InstantSerializer
import dz.wave.domain.Lang
import dz.wave.domain.Money
import dz.wave.domain.PaymentMethod
import dz.wave.domain.PaymentRecord
import dz.wave.domain.PaymentStatus
import dz.wave.rules.Messages
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Serializable
data class PaymentInitiation(
    val paymentId: String,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val amount: Money,
    val redirectUrl: String? = null,
    val instructions: String? = null,
    val expiresAt: Instant? = null,
)

data class PaymentContext(
    val booking: Booking,
    val method: PaymentMethod,
    val amount: Money,
    val paymentId: String,
    val returnUrl: String,
    val failUrl: String,
    val lang: Lang,
)

data class ProviderStart(val providerOrderId: String?, val redirectUrl: String?, val instructions: String?)

data class PaymentVerification(val status: PaymentStatus, val providerOrderId: String?, val message: String?)

interface PaymentProvider {
    val id: String
    val methods: Set<PaymentMethod>

    /** Currency the provider charges in for this booking. */
    fun chargeCurrency(booking: Booking, method: PaymentMethod): CurrencyCode

    suspend fun start(ctx: PaymentContext): ProviderStart

    /** Must query the provider: return-URL parameters are never trusted on their own. */
    suspend fun verify(payment: PaymentRecord, params: Map<String, String>): PaymentVerification
}

class PaymentService(
    providers: List<PaymentProvider>,
    private val bookings: BookingService,
    private val publicBaseUrl: String,
    private val appReturnUrl: String,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(PaymentService::class.java)
    private val byMethod: Map<PaymentMethod, PaymentProvider> =
        providers.flatMap { p -> p.methods.map { it to p } }.toMap()

    fun availableMethods(): List<PaymentMethod> = byMethod.keys.sortedBy { it.ordinal }

    suspend fun initiate(booking: Booking, method: PaymentMethod, lang: Lang): PaymentInitiation {
        if (booking.status != BookingStatus.HELD) {
            throw ConflictException(
                "BOOKING_NOT_PAYABLE",
                Messages.text("BOOKING_NOT_PAYABLE", lang, mapOf("status" to booking.status.name)),
            )
        }
        val provider = byMethod[method]
            ?: throw ConflictException("PAYMENT_METHOD_UNAVAILABLE", Messages.text("PAYMENT_METHOD_UNAVAILABLE", lang))
        val currency = provider.chargeCurrency(booking, method)
        val amount = booking.quote.payable[currency] ?: booking.quote.total
        val paymentId = UUID.randomUUID().toString()
        val base = "$publicBaseUrl/api/v1/payments/return?provider=${provider.id}&reference=${booking.reference}&paymentId=$paymentId"
        val start = provider.start(
            PaymentContext(booking, method, amount, paymentId, "$base&outcome=success", "$base&outcome=failure", lang),
        )
        val now = clock.instant()
        val record = PaymentRecord(
            id = paymentId,
            method = method,
            provider = provider.id,
            providerOrderId = start.providerOrderId,
            status = PaymentStatus.PENDING,
            amount = amount,
            createdAt = now,
            updatedAt = now,
            message = start.instructions,
        )
        val updated = bookings.addPayment(booking, record, extendForAgency = method == PaymentMethod.AGENCY)
        log.info("Payment {} started for {} via {} ({})", paymentId, booking.reference, provider.id, amount)
        return PaymentInitiation(paymentId, method, PaymentStatus.PENDING, amount, start.redirectUrl, start.instructions, updated.holdExpiresAt)
    }

    /** Handles the browser return / webhook: verifies with the provider, then confirms the booking. */
    suspend fun complete(reference: String, paymentId: String, params: Map<String, String>): Booking {
        val booking = bookings.findByReference(reference)
            ?: throw NotFoundException("BOOKING_NOT_FOUND", Messages.text("BOOKING_NOT_FOUND", Lang.FR))
        val payment = booking.payments.firstOrNull { it.id == paymentId }
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "Unknown payment")
        if (payment.status == PaymentStatus.APPROVED) return booking
        val provider = byMethod[payment.method] ?: throw ConflictException("PAYMENT_METHOD_UNAVAILABLE", "Provider not configured")
        val verification = provider.verify(payment, params)
        val updatedPayment = payment.copy(
            status = verification.status,
            providerOrderId = verification.providerOrderId ?: payment.providerOrderId,
            message = verification.message,
            updatedAt = clock.instant(),
        )
        return if (verification.status == PaymentStatus.APPROVED) {
            bookings.confirmPayment(booking, updatedPayment)
        } else {
            bookings.addPayment(booking, updatedPayment, extendForAgency = false)
        }
    }

    /** Agency staff confirming cash received at the counter. */
    suspend fun approveManually(reference: String, paymentId: String): Booking {
        val booking = bookings.findByReference(reference)
            ?: throw NotFoundException("BOOKING_NOT_FOUND", Messages.text("BOOKING_NOT_FOUND", Lang.FR))
        val payment = booking.payments.firstOrNull { it.id == paymentId && it.method == PaymentMethod.AGENCY }
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "Unknown agency payment")
        return bookings.confirmPayment(booking, payment.copy(status = PaymentStatus.APPROVED, updatedAt = clock.instant()))
    }

    fun appRedirect(booking: Booking): String =
        "$appReturnUrl?reference=${booking.reference}&status=${URLEncoder.encode(booking.status.name, Charsets.UTF_8)}"
}

/** Development provider: the "bank page" is a link back to our own return endpoint. */
class SandboxPaymentProvider(private val publicBaseUrl: String) : PaymentProvider {
    override val id = "sandbox"
    override val methods = setOf(PaymentMethod.SANDBOX, PaymentMethod.CIB, PaymentMethod.EDAHABIA, PaymentMethod.CARD)

    override fun chargeCurrency(booking: Booking, method: PaymentMethod): CurrencyCode = when (method) {
        PaymentMethod.CIB, PaymentMethod.EDAHABIA -> CurrencyCode.DZD
        else -> booking.quote.currency
    }

    override suspend fun start(ctx: PaymentContext): ProviderStart =
        ProviderStart("SBX-${ctx.paymentId.take(8)}", ctx.returnUrl + "&sandbox=approved", null)

    override suspend fun verify(payment: PaymentRecord, params: Map<String, String>): PaymentVerification =
        if (params["sandbox"] == "approved" && params["outcome"] == "success") {
            PaymentVerification(PaymentStatus.APPROVED, payment.providerOrderId, "Sandbox approval")
        } else {
            PaymentVerification(PaymentStatus.DECLINED, payment.providerOrderId, "Sandbox decline")
        }
}

/** Cash or card payment at a WAVE partner agency; staff confirms through the admin API. */
class AgencyPaymentProvider(private val agencyPhones: List<String>) : PaymentProvider {
    override val id = "agency"
    override val methods = setOf(PaymentMethod.AGENCY)

    override fun chargeCurrency(booking: Booking, method: PaymentMethod) = CurrencyCode.DZD

    override suspend fun start(ctx: PaymentContext): ProviderStart {
        val phones = agencyPhones.joinToString(" / ")
        val amount = ctx.amount.toDecimal().toPlainString()
        val text = when (ctx.lang) {
            Lang.FR -> "Réglez $amount DA en agence sous 24 h avec la référence ${ctx.booking.reference}. Contact : $phones."
            Lang.EN -> "Pay $amount DZD at the agency within 24 h quoting reference ${ctx.booking.reference}. Contact: $phones."
            Lang.AR -> "ادفع $amount دج في الوكالة خلال 24 ساعة مع ذكر المرجع ${ctx.booking.reference}. للتواصل: $phones."
        }
        return ProviderStart(null, null, text)
    }

    override suspend fun verify(payment: PaymentRecord, params: Map<String, String>) =
        PaymentVerification(PaymentStatus.PENDING, null, payment.message)
}
