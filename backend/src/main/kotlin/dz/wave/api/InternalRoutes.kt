package dz.wave.api

import dz.wave.AppContainer
import dz.wave.common.AppJson
import dz.wave.common.UnauthorizedException
import dz.wave.ingest.IngestBatch
import dz.wave.ingest.IngestRates
import dz.wave.live.AisFix
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

@Serializable
data class Accepted(val accepted: Int)

/**
 * Machine-to-machine endpoints for the scraping pipeline and AIS receivers. Every request is signed:
 * `X-Wave-Signature = hex(HMAC-SHA256(secret, "<timestamp>.<body>"))`, valid for 5 minutes.
 */
suspend fun ApplicationCall.verifiedBody(c: AppContainer): String {
    val keyId = request.header("X-Wave-Key")
    val timestamp = request.header("X-Wave-Timestamp")?.toLongOrNull()
    val signature = request.header("X-Wave-Signature")?.lowercase()
    val body = receiveText()
    if (keyId != c.config.ingestKeyId || timestamp == null || signature == null) throw UnauthorizedException("Missing signature")
    if (abs(Instant.now(c.clock).epochSecond - timestamp) > 300) throw UnauthorizedException("Stale request")
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(c.config.ingestSecret.toByteArray(), "HmacSHA256")) }
    val expected = mac.doFinal("$timestamp.$body".toByteArray()).joinToString("") { "%02x".format(it) }
    if (!MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())) throw UnauthorizedException("Bad signature")
    return body
}

fun Route.internalRoutes(c: AppContainer) {
    route("/internal/v1/ingest") {
        rateLimit(Limits429.INGEST) {
            post("/sailings") {
                val batch = AppJson.decodeFromString(IngestBatch.serializer(), call.verifiedBody(c))
                call.respond(c.ingest.ingest(batch))
            }
            post("/rates") {
                val rates = AppJson.decodeFromString(IngestRates.serializer(), call.verifiedBody(c))
                c.ingest.ingestRates(rates)
                call.respond(c.currency.current().info())
            }
            post("/positions") {
                val fixes = AppJson.decodeFromString(ListSerializer(AisFix.serializer()), call.verifiedBody(c))
                fixes.forEach(c::publishFix)
                call.respond(Accepted(fixes.size))
            }
        }
    }
}
