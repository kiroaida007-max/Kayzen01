@file:UseSerializers(ZonedDateTimeSerializer::class)

package dz.wave.api

import dz.wave.AppContainer
import dz.wave.common.AppJson
import dz.wave.common.NotFoundException
import dz.wave.domain.AccommodationPref
import dz.wave.domain.CurrencyCode
import dz.wave.domain.Lang
import dz.wave.domain.PassengersInput
import dz.wave.domain.SearchRequest
import dz.wave.domain.VehicleInput
import dz.wave.domain.VehicleType
import dz.wave.domain.ZonedDateTimeSerializer
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class CheckInWindow(val opens: ZonedDateTime, val closes: ZonedDateTime)

@Serializable
data class SailingDetails(
    val id: String,
    val routeId: String,
    val operator: OperatorDto,
    val vessel: VesselDto?,
    val from: PortDto,
    val to: PortDto,
    val departure: ZonedDateTime,
    val arrival: ZonedDateTime,
    val durationMin: Long,
    val status: dz.wave.domain.SailingStatus,
    val priceSource: dz.wave.domain.PriceSource,
    val checkInFoot: CheckInWindow,
    val checkInVehicle: CheckInWindow,
    val polyline: List<List<Double>>,
    val regulations: List<RegulationDto>,
)

private class MetaCache(val key: String, val body: MetaResponse)

fun Route.healthRoutes(c: AppContainer) {
    get("/health/live") { call.respondText("ok") }
    get("/health/ready") {
        val snap = c.catalog.current()
        if (c.healthy()) {
            call.respond(HealthResponse("ready", snap.version, snap.sailingCount, c.catalog.overlaySize()))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("degraded", snap.version, snap.sailingCount, c.catalog.overlaySize()))
        }
    }
    get("/metrics") {
        if (call.request.local.localPort != c.config.managementPort) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondText(c.metrics.scrape())
        }
    }
}

fun Route.publicRoutes(c: AppContainer) {
    val metaCache = AtomicReference<MetaCache?>(null)

    fun meta(): MetaResponse {
        val snap = c.catalog.current()
        val today = LocalDate.now(c.clock)
        val key = "${snap.version}.${c.currency.current().version}.$today"
        metaCache.get()?.takeIf { it.key == key }?.let { return it.body }
        val body = MetaBuilder.build(snap, c.currency, c.content.popular, c.payments.availableMethods(), today, c.clock.instant())
        metaCache.set(MetaCache(key, body))
        return body
    }

    route("/api/v1") {
        rateLimit(Limits429.API) {
            get("/meta") {
                val body = meta()
                val etag = "\"${body.version}\""
                call.response.header(HttpHeaders.ETag, etag)
                call.response.header(HttpHeaders.CacheControl, "public, max-age=60")
                if (call.request.header(HttpHeaders.IfNoneMatch) == etag) {
                    call.respond(HttpStatusCode.NotModified)
                } else {
                    call.respond(body)
                }
            }
            get("/ports") { call.respond(meta().ports) }
            get("/operators") { call.respond(meta().operators) }
            get("/operators/{code}") {
                val code = call.parameters["code"]?.uppercase()
                call.respond(meta().operators.firstOrNull { it.code == code } ?: throw NotFoundException("OPERATOR_NOT_FOUND", "Compagnie inconnue."))
            }
            get("/vessels") { call.respond(meta().vessels) }
            get("/routes") { call.respond(meta().routes) }
            get("/routes/destinations") {
                val from = call.request.queryParameters["from"]?.uppercase()
                val meta = meta()
                val port = meta.ports.firstOrNull { it.code == from } ?: throw NotFoundException("PORT_UNKNOWN", "Port inconnu.")
                call.respond(meta.ports.filter { it.code in port.destinations })
            }
            get("/currency/rates") { call.respond(c.currency.current().info()) }
            get("/live/vessels") {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=5")
                call.respond(c.live.snapshot())
            }
            get("/content/guides") {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
                call.respond(c.content.guides)
            }
            get("/content/deals") { call.respond(c.content.deals()) }
            get("/sailings/{id}") { call.respond(sailingDetails(c, call.parameters["id"].orEmpty(), ::meta)) }
        }

        rateLimit(Limits429.SEARCH) {
            post("/search") {
                val request = call.receive<SearchRequest>()
                call.respond(c.search.search(request))
            }
            get("/search/calendar") {
                val month = YearMonth.parse(call.request.queryParameters["month"] ?: YearMonth.now(c.clock).toString())
                call.respond(c.search.calendar(calendarRequest(call), month))
            }
            post("/quote") {
                val selection = call.receive<dz.wave.domain.BookingSelection>()
                call.respond(c.quotes.quote(selection).quote)
            }
        }
    }
}

private fun calendarRequest(call: ApplicationCall): SearchRequest {
    val q = call.request.queryParameters
    val vehicle = q["vehicle"]?.takeIf { it.isNotBlank() && it != "NONE" }?.let { VehicleInput(VehicleType.valueOf(it)) }
    return SearchRequest(
        from = q["from"].orEmpty().uppercase(),
        to = q["to"].orEmpty().uppercase(),
        departureDate = LocalDate.now(),
        passengers = PassengersInput(
            adults = q["adults"]?.toInt() ?: 1,
            seniors = q["seniors"]?.toInt() ?: 0,
            childrenAges = q["children"]?.split(',')?.filter { it.isNotBlank() }?.map { it.trim().toInt() }.orEmpty(),
        ),
        vehicle = vehicle,
        accommodation = q["accommodation"]?.let { AccommodationPref.valueOf(it) } ?: AccommodationPref.SEAT,
        currency = q["currency"]?.let { CurrencyCode.valueOf(it) } ?: CurrencyCode.DZD,
        lang = when (q["lang"]) {
            "en" -> Lang.EN
            "ar" -> Lang.AR
            else -> Lang.FR
        },
    )
}

private fun sailingDetails(c: AppContainer, id: String, meta: () -> MetaResponse): SailingDetails {
    val snap = c.catalog.current()
    val sailing = snap.sailingsById[id] ?: throw NotFoundException("SAILING_NOT_FOUND", "Traversée introuvable.")
    val m = meta()
    val dep = sailing.departure
    return SailingDetails(
        id = sailing.id,
        routeId = sailing.routeId,
        operator = m.operators.first { it.code == sailing.operator },
        vessel = sailing.vessel?.let { code -> m.vessels.firstOrNull { it.code == code } },
        from = m.ports.first { it.code == sailing.from },
        to = m.ports.first { it.code == sailing.to },
        departure = dep,
        arrival = sailing.arrival,
        durationMin = sailing.durationMinutes,
        status = sailing.status,
        priceSource = sailing.source,
        // Reference times published for Marseille–Alger: foot 4 h→1 h, vehicles 7 h→1 h 30 before departure.
        checkInFoot = CheckInWindow(dep.minusHours(4), dep.minusHours(1)),
        checkInVehicle = CheckInWindow(dep.minusHours(7), dep.minusMinutes(90)),
        polyline = snap.geometry(sailing.routeId).points.map { listOf(it.lat, it.lon) },
        regulations = m.regulations,
    )
}

fun Route.liveSocket(c: AppContainer) {
    webSocket("/ws/live") {
        val serializer = ListSerializer(dz.wave.live.VesselPosition.serializer())
        c.live.flow.collect { positions ->
            send(Frame.Text(AppJson.encodeToString(serializer, positions)))
        }
    }
}
