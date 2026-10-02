package dz.wave.payment

import dz.wave.domain.Booking
import dz.wave.domain.CurrencyCode
import dz.wave.domain.Lang
import dz.wave.domain.PaymentMethod
import dz.wave.domain.PaymentRecord
import dz.wave.domain.PaymentStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

data class SatimConfig(
    val baseUrl: String,
    val userName: String,
    val password: String,
    val terminalId: String,
)

/**
 * SATIM e-payment gateway (CIB and EDAHABIA cards, amounts in dinars).
 * Flow: `register.do` returns the bank's payment page URL; after the redirect back we call
 * `confirmOrder.do` and only accept `OrderStatus = 2` with `actionCode = 0`.
 */
class SatimProvider(
    private val config: SatimConfig,
    private val http: HttpClient,
) : PaymentProvider {
    private val log = LoggerFactory.getLogger(SatimProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override val id = "satim"
    override val methods = setOf(PaymentMethod.CIB, PaymentMethod.EDAHABIA)

    override fun chargeCurrency(booking: Booking, method: PaymentMethod) = CurrencyCode.DZD

    override suspend fun start(ctx: PaymentContext): ProviderStart {
        require(ctx.amount.currency == CurrencyCode.DZD) { "SATIM only charges dinars" }
        require(ctx.amount.minor >= MIN_AMOUNT_MINOR) { "Amount below SATIM minimum" }
        val orderNumber = (ctx.booking.reference + ctx.paymentId.filter(Char::isDigit).take(4)).take(20)
        val jsonParams = buildJsonObject {
            put("force_terminal_id", config.terminalId)
            put("udf1", ctx.booking.reference)
        }
        val response = http.submitForm(
            url = "${config.baseUrl}/register.do",
            formParameters = Parameters.build {
                append("userName", config.userName)
                append("password", config.password)
                append("orderNumber", orderNumber)
                append("amount", ctx.amount.minor.toString())
                append("currency", CurrencyCode.DZD.isoNumeric)
                append("returnUrl", ctx.returnUrl)
                append("failUrl", ctx.failUrl)
                append("language", language(ctx.lang))
                append("jsonParams", jsonParams.toString())
            },
        )
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val errorCode = body.string("errorCode") ?: body.string("ErrorCode") ?: "0"
        val orderId = body.string("orderId")
        val formUrl = body.string("formUrl")
        if (errorCode != "0" || orderId == null || formUrl == null) {
            log.warn("SATIM register failed: code={} message={}", errorCode, body.string("errorMessage"))
            error("SATIM register failed ($errorCode)")
        }
        return ProviderStart(orderId, formUrl, null)
    }

    override suspend fun verify(payment: PaymentRecord, params: Map<String, String>): PaymentVerification {
        val orderId = payment.providerOrderId ?: params["orderId"]
            ?: return PaymentVerification(PaymentStatus.DECLINED, null, "Missing orderId")
        val response = http.submitForm(
            url = "${config.baseUrl}/confirmOrder.do",
            formParameters = Parameters.build {
                append("userName", config.userName)
                append("password", config.password)
                append("orderId", orderId)
                append("language", "fr")
            },
        )
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val errorCode = body.string("ErrorCode") ?: body.string("errorCode") ?: "-1"
        val orderStatus = body["OrderStatus"]?.jsonPrimitive?.intOrNull
        val actionCode = body["actionCode"]?.jsonPrimitive?.intOrNull
        val amount = body["Amount"]?.jsonPrimitive?.int?.toLong()
        val approved = errorCode == "0" && orderStatus == 2 && actionCode == 0 && amount == payment.amount.minor
        val message = body.string("actionCodeDescription") ?: body.string("ErrorMessage")
        return PaymentVerification(if (approved) PaymentStatus.APPROVED else PaymentStatus.DECLINED, orderId, message)
    }

    private fun language(lang: Lang) = when (lang) {
        Lang.FR -> "fr"
        Lang.EN -> "en"
        Lang.AR -> "ar"
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it != "null" }

    companion object {
        /** SATIM refuses orders under 50 DZD. */
        const val MIN_AMOUNT_MINOR = 50_00L
    }
}
