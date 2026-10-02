package dz.wave.api

import dz.wave.AppContainer
import dz.wave.common.ApiError
import dz.wave.common.ConflictException
import dz.wave.common.ValidationException
import dz.wave.domain.CreateBookingRequest
import dz.wave.domain.Lang
import dz.wave.domain.RuleSeverity
import dz.wave.domain.Violation
import dz.wave.infra.IdempotencyStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.accept
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration

private val IDEMPOTENCY_TTL: Duration = Duration.ofHours(24)

fun ApplicationCall.userId(): String? = principal<JWTPrincipal>()?.payload?.subject

private fun ApplicationCall.lang(): Lang = when (request.queryParameters["lang"]) {
    "en" -> Lang.EN
    "ar" -> Lang.AR
    else -> Lang.FR
}

fun Route.bookingRoutes(c: AppContainer) {
    route("/api/v1") {
        rateLimit(Limits429.BOOKING) {
            authenticate("user", optional = true) {
                post("/bookings") {
                    val key = call.request.header("Idempotency-Key")?.trim()
                    if (key != null && key.length !in 8..100) {
                        throw ValidationException(listOf(Violation("IDEMPOTENCY_KEY_INVALID", RuleSeverity.ERROR, "Idempotency-Key invalide.")))
                    }
                    if (key != null) {
                        val previous = c.idempotency.get(key)
                        when {
                            previous == IdempotencyStore.IN_PROGRESS ->
                                throw ConflictException("REQUEST_IN_PROGRESS", "Votre réservation est en cours de création.")
                            previous != null -> {
                                c.bookings.findByReference(previous)?.let { return@post call.respond(HttpStatusCode.OK, it.toView()) }
                            }
                        }
                        if (!c.idempotency.claim(key, IDEMPOTENCY_TTL)) {
                            throw ConflictException("REQUEST_IN_PROGRESS", "Votre réservation est en cours de création.")
                        }
                    }
                    try {
                        val request = call.receive<CreateBookingRequest>()
                        val booking = c.bookings.create(request, call.userId())
                        key?.let { c.idempotency.complete(it, booking.reference, IDEMPOTENCY_TTL) }
                        call.respond(HttpStatusCode.Created, booking.toView())
                    } catch (e: Throwable) {
                        key?.let { c.idempotency.release(it) }
                        throw e
                    }
                }
            }

            post("/bookings/{ref}/payments") {
                val request = call.receive<PaymentRequest>()
                val booking = c.bookings.find(call.parameters["ref"].orEmpty(), request.lastName, request.lang)
                call.respond(c.payments.initiate(booking, request.method, request.lang))
            }

            post("/bookings/{ref}/cancel") {
                val request = call.receive<CancelRequest>()
                val booking = c.bookings.find(call.parameters["ref"].orEmpty(), request.lastName, request.lang)
                call.respond(c.bookings.cancel(booking, request.lang, request.dryRun))
            }
        }

        rateLimit(Limits429.LOOKUP) {
            get("/bookings/{ref}") {
                val lastName = call.request.queryParameters["lastName"].orEmpty()
                call.respond(c.bookings.find(call.parameters["ref"].orEmpty(), lastName, call.lang()).toView())
            }
            get("/bookings/{ref}/ticket") {
                val lastName = call.request.queryParameters["lastName"].orEmpty()
                val booking = c.bookings.find(call.parameters["ref"].orEmpty(), lastName, call.lang())
                val ticket = booking.ticket ?: throw ConflictException("TICKET_NOT_ISSUED", "Le billet sera disponible après le paiement.")
                call.respond(ticket)
            }
        }

        route("/payments") {
            // Browser comes back from the bank / Stripe page. We verify with the provider, never with the query string alone.
            get("/return") {
                val q = call.request.queryParameters
                val booking = c.payments.complete(
                    reference = q["reference"].orEmpty(),
                    paymentId = q["paymentId"].orEmpty(),
                    params = q.entries().associate { it.key to it.value.firstOrNull().orEmpty() },
                )
                if (call.request.accept()?.contains(ContentType.Application.Json.toString()) == true) {
                    call.respond(booking.toView())
                } else {
                    call.respondRedirect(c.payments.appRedirect(booking))
                }
            }

            post("/stripe/webhook") {
                val stripe = c.stripe ?: return@post call.respond(HttpStatusCode.NotFound)
                val payload = call.receiveText()
                val signature = call.request.header("Stripe-Signature").orEmpty()
                if (!stripe.verifyWebhook(payload, signature)) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("BAD_SIGNATURE", "Invalid signature", call.callId))
                }
                val event = dz.wave.common.AppJson.parseToJsonElement(payload).jsonObject
                if (event["type"]?.jsonPrimitive?.content == "checkout.session.completed") {
                    val metadata = event["data"]?.jsonObject?.get("object")?.jsonObject?.get("metadata")?.jsonObject
                    val reference = metadata?.get("reference")?.jsonPrimitive?.content
                    val paymentId = metadata?.get("paymentId")?.jsonPrimitive?.content
                    if (reference != null && paymentId != null) c.payments.complete(reference, paymentId, emptyMap())
                }
                call.respond(HttpStatusCode.OK, mapOf("received" to true))
            }
        }
    }
}
