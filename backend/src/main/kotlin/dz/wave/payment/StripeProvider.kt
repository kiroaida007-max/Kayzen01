package dz.wave.payment

import dz.wave.domain.Booking
import dz.wave.domain.CurrencyCode
import dz.wave.domain.PaymentMethod
import dz.wave.domain.PaymentRecord
import dz.wave.domain.PaymentStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** International cards (EUR/USD) through Stripe Checkout, for travellers paying from abroad. */
class StripeProvider(
    private val secretKey: String,
    private val webhookSecret: String?,
    private val http: HttpClient,
    private val clock: Clock,
    private val apiBase: String = "https://api.stripe.com/v1",
) : PaymentProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override val id = "stripe"
    override val methods = setOf(PaymentMethod.CARD)

    override fun chargeCurrency(booking: Booking, method: PaymentMethod): CurrencyCode =
        if (booking.quote.currency == CurrencyCode.USD) CurrencyCode.USD else CurrencyCode.EUR

    override suspend fun start(ctx: PaymentContext): ProviderStart {
        val response = http.submitForm(
            url = "$apiBase/checkout/sessions",
            formParameters = Parameters.build {
                append("mode", "payment")
                append("success_url", ctx.returnUrl + "&session_id={CHECKOUT_SESSION_ID}")
                append("cancel_url", ctx.failUrl)
                append("client_reference_id", ctx.booking.reference)
                append("customer_email", ctx.booking.contact.email)
                append("line_items[0][quantity]", "1")
                append("line_items[0][price_data][currency]", ctx.amount.currency.name.lowercase())
                append("line_items[0][price_data][unit_amount]", ctx.amount.minor.toString())
                append("line_items[0][price_data][product_data][name]", "WAVE ferry booking ${ctx.booking.reference}")
                append("metadata[paymentId]", ctx.paymentId)
                append("metadata[reference]", ctx.booking.reference)
            },
        ) {
            header(HttpHeaders.Authorization, "Bearer $secretKey")
            header("Idempotency-Key", ctx.paymentId)
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sessionId = body["id"]?.jsonPrimitive?.content ?: error("Stripe session creation failed")
        return ProviderStart(sessionId, body["url"]?.jsonPrimitive?.content, null)
    }

    override suspend fun verify(payment: PaymentRecord, params: Map<String, String>): PaymentVerification {
        val sessionId = payment.providerOrderId ?: return PaymentVerification(PaymentStatus.DECLINED, null, "No session")
        val response = http.get("$apiBase/checkout/sessions/$sessionId") {
            header(HttpHeaders.Authorization, "Bearer $secretKey")
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val paid = body["payment_status"]?.jsonPrimitive?.content == "paid"
        val amount = body["amount_total"]?.jsonPrimitive?.content?.toLongOrNull()
        val ok = paid && amount == payment.amount.minor
        return PaymentVerification(if (ok) PaymentStatus.APPROVED else PaymentStatus.DECLINED, sessionId, null)
    }

    /** Validates the `Stripe-Signature` header (`t=...,v1=...`) with a 5-minute replay window. */
    fun verifyWebhook(payload: String, signatureHeader: String): Boolean {
        val secret = webhookSecret ?: return false
        val parts = signatureHeader.split(',').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }
        val timestamp = parts.firstOrNull { it[0] == "t" }?.get(1)?.toLongOrNull() ?: return false
        if (Duration.between(Instant.ofEpochSecond(timestamp), clock.instant()).abs() > Duration.ofMinutes(5)) return false
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        val expected = mac.doFinal("$timestamp.$payload".toByteArray()).joinToString("") { "%02x".format(it) }
        return parts.filter { it[0] == "v1" }.any { MessageDigest.isEqual(it[1].toByteArray(), expected.toByteArray()) }
    }
}
