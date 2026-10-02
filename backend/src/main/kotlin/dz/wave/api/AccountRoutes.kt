package dz.wave.api

import dz.wave.AppContainer
import dz.wave.auth.LoginRequest
import dz.wave.auth.RefreshRequest
import dz.wave.auth.RegisterRequest
import dz.wave.auth.Role
import dz.wave.auth.toPublic
import dz.wave.common.ForbiddenException
import dz.wave.common.UnauthorizedException
import dz.wave.domain.BookingStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private fun ApplicationCall.requireRole(vararg roles: Role) {
    val role = principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
        ?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        ?: throw UnauthorizedException()
    if (role !in roles) throw ForbiddenException("Accès réservé.")
}

fun Route.accountRoutes(c: AppContainer) {
    route("/api/v1") {
        rateLimit(Limits429.AUTH) {
            route("/auth") {
                post("/register") { call.respond(HttpStatusCode.Created, c.auth.register(call.receive<RegisterRequest>())) }
                post("/login") { call.respond(c.auth.login(call.receive<LoginRequest>())) }
                post("/refresh") { call.respond(c.auth.refresh(call.receive<RefreshRequest>().refreshToken)) }
                post("/logout") {
                    c.auth.logout(call.receive<RefreshRequest>().refreshToken)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        rateLimit(Limits429.API) {
            authenticate("user") {
                get("/me") {
                    val user = call.userId()?.let { c.auth.user(it) } ?: throw UnauthorizedException()
                    call.respond(user.toPublic())
                }
                get("/me/bookings") {
                    val id = call.userId() ?: throw UnauthorizedException()
                    call.respond(c.bookings.listForUser(id).map { it.toView() })
                }

                // Port staff scan the QR code; the signature proves WAVE issued it.
                post("/tickets/verify") {
                    call.requireRole(Role.STAFF, Role.ADMIN)
                    val payload = call.receive<VerifyTicketRequest>().payload
                    val reference = c.ticketSigner.verify(payload)
                    val booking = reference?.let { c.bookings.findByReference(it) }
                    call.respond(
                        VerifyTicketResponse(
                            valid = booking != null && booking.status in setOf(BookingStatus.CONFIRMED, BookingStatus.TICKETED),
                            reference = booking?.reference,
                            status = booking?.status,
                            passengers = booking?.travellers?.size,
                        ),
                    )
                }

                route("/admin") {
                    get("/bookings") {
                        call.requireRole(Role.ADMIN, Role.STAFF)
                        val status = call.request.queryParameters["status"]?.let { BookingStatus.valueOf(it) } ?: BookingStatus.CONFIRMED
                        call.respond(c.bookings.listByStatus(status, 200).map { it.toView() })
                    }
                    post("/bookings/{ref}/ticketed") {
                        call.requireRole(Role.ADMIN, Role.STAFF)
                        val body = call.receive<TicketedRequest>()
                        call.respond(c.bookings.markTicketed(call.parameters["ref"].orEmpty().uppercase(), body.operatorReferences).toView())
                    }
                    post("/bookings/{ref}/payments/{paymentId}/approve") {
                        call.requireRole(Role.ADMIN, Role.STAFF)
                        val booking = c.payments.approveManually(call.parameters["ref"].orEmpty().uppercase(), call.parameters["paymentId"].orEmpty())
                        call.respond(booking.toView())
                    }
                }
            }
        }
    }
}
