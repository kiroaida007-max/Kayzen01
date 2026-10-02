package dz.wave.api

import dz.wave.AppContainer
import dz.wave.common.ApiError
import dz.wave.common.AppJson
import dz.wave.common.ConflictException
import dz.wave.common.ForbiddenException
import dz.wave.common.NotFoundException
import dz.wave.common.ServiceUnavailableException
import dz.wave.common.UnauthorizedException
import dz.wave.common.ValidationException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.ktor.websocket.WebSocketDeflateExtension
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object Limits429 {
    val API = RateLimitName("api")
    val SEARCH = RateLimitName("search")
    val BOOKING = RateLimitName("booking")
    val LOOKUP = RateLimitName("lookup")
    val AUTH = RateLimitName("auth")
    val INGEST = RateLimitName("ingest")
}

private val log = LoggerFactory.getLogger("dz.wave.api")

fun ApplicationCall.clientIp(): String = request.origin.remoteHost

private val CLIENT_ID = Regex("^[A-Za-z0-9-]{16,64}$")

/**
 * Algerian mobile carriers put thousands of subscribers behind one carrier-grade NAT address, so
 * per-IP buckets would throttle legitimate users. Buckets are per IP *and* app install
 * (`X-Wave-Client`, a random id the app generates); the edge load balancer keeps a hard per-IP
 * ceiling, which bounds what rotating ids can gain.
 */
fun ApplicationCall.rateLimitKey(): String {
    val ip = clientIp()
    val client = request.headers["X-Wave-Client"]?.takeIf { CLIENT_ID.matches(it) } ?: return ip
    return "$ip|$client"
}

fun Application.waveModule(c: AppContainer) {
    val config = c.config

    if (config.trustedProxyHops > 0) {
        install(XForwardedHeaders) {
            // nginx / the cloud LB append the client address; trust only our own hops.
            skipLastProxies(config.trustedProxyHops - 1)
        }
    }

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { id -> id.length in 8..64 && id.all { it.isLetterOrDigit() || it == '-' } }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        // Dedicated logger so production can lower access logs (ACCESS_LOG_LEVEL) without hiding errors.
        logger = LoggerFactory.getLogger("wave.access")
        level = Level.INFO
        callIdMdc("requestId")
        filter { call -> !call.request.path().startsWith("/health") && call.request.path() != "/metrics" }
    }

    install(DefaultHeaders) {
        header(HttpHeaders.Server, "wave")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        if (config.isProduction) header("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload")
    }

    install(CORS) {
        if ("*" in config.corsOrigins) {
            anyHost()
        } else {
            config.corsOrigins.forEach { origin ->
                val uri = URI(origin)
                allowHost(uri.host + if (uri.port > 0) ":${uri.port}" else "", schemes = listOf(uri.scheme ?: "https"))
            }
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("Idempotency-Key")
        allowHeader("X-Wave-Client")
        allowHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.ETag)
        maxAgeInSeconds = 3600
    }

    install(ContentNegotiation) { json(AppJson) }
    install(Compression) {
        gzip {
            minimumSize(1024)
        }
    }
    install(ConditionalHeaders)
    install(RequestBodyLimit) {
        bodyLimit { call -> if (call.request.path().startsWith("/internal")) 8L * 1024 * 1024 else 256L * 1024 }
    }

    install(WebSockets) {
        pingPeriod = 20.seconds
        timeout = 40.seconds
        maxFrameSize = 64 * 1024
        masking = false
        extensions { install(WebSocketDeflateExtension) }
    }

    install(MicrometerMetrics) {
        registry = c.metrics
        meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), JvmThreadMetrics(), ProcessorMetrics())
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .build()
    }
    c.registerGauges()

    install(RateLimit) {
        fun limit(name: RateLimitName, perMinute: Int, perInstall: Boolean = true) = register(name) {
            rateLimiter(limit = perMinute, refillPeriod = 1.minutes)
            requestKey { call -> if (perInstall) call.rateLimitKey() else call.clientIp() }
        }
        limit(Limits429.API, config.globalRateLimitPerMinute)
        limit(Limits429.SEARCH, config.searchRateLimitPerMinute)
        limit(Limits429.BOOKING, 20)
        limit(Limits429.LOOKUP, 30)
        limit(Limits429.AUTH, 10)
        limit(Limits429.INGEST, 120, perInstall = false)
    }

    install(Authentication) {
        jwt("user") {
            realm = "wave"
            verifier(c.jwt.verifier)
            validate { credential -> credential.payload.subject?.let { JWTPrincipal(credential.payload) } }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("UNAUTHORIZED", "Authentification requise.", call.callId))
            }
        }
    }

    install(StatusPages) {
        exception<ValidationException> { call, e ->
            val first = e.violations.firstOrNull()
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiError(first?.code ?: e.code, first?.message ?: "Requête invalide.", call.callId, e.violations),
            )
        }
        exception<NotFoundException> { call, e -> call.respond(HttpStatusCode.NotFound, ApiError(e.code, e.message ?: "", call.callId)) }
        exception<ConflictException> { call, e ->
            call.respond(HttpStatusCode.Conflict, ApiError(e.code, e.message ?: "", call.callId, details = e.details))
        }
        exception<UnauthorizedException> { call, e -> call.respond(HttpStatusCode.Unauthorized, ApiError(e.code, e.message ?: "", call.callId)) }
        exception<ForbiddenException> { call, e -> call.respond(HttpStatusCode.Forbidden, ApiError(e.code, e.message ?: "", call.callId)) }
        exception<ServiceUnavailableException> { call, e ->
            call.respond(HttpStatusCode.ServiceUnavailable, ApiError(e.code, e.message ?: "", call.callId))
        }
        exception<BadRequestException> { call, e -> badRequest(call, e) }
        exception<ContentTransformationException> { call, e -> badRequest(call, e) }
        exception<SerializationException> { call, e -> badRequest(call, e) }
        exception<IllegalArgumentException> { call, e -> badRequest(call, e) }
        exception<java.time.format.DateTimeParseException> { call, e -> badRequest(call, e) }
        // Postgres or Redis unreachable: a clear, retryable 503 instead of a 500.
        exception<io.lettuce.core.RedisException> { call, e -> dependencyDown(call, e) }
        exception<java.sql.SQLTransientException> { call, e -> dependencyDown(call, e) }
        exception<Throwable> { call, e ->
            log.error("Unhandled error on {} [{}]", call.request.path(), call.callId, e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError("INTERNAL_ERROR", "Une erreur est survenue. Réessayez dans un instant.", call.callId),
            )
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ApiError("RATE_LIMITED", "Trop de requêtes : patientez quelques secondes.", call.callId))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiError("NOT_FOUND", "Ressource introuvable.", call.callId))
        }
    }

    routing {
        healthRoutes(c)
        publicRoutes(c)
        bookingRoutes(c)
        accountRoutes(c)
        internalRoutes(c)
        liveSocket(c)
    }
}

private suspend fun badRequest(call: ApplicationCall, e: Throwable) {
    log.debug("Bad request on {}: {}", call.request.path(), e.message)
    call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "Requête invalide : vérifiez les champs envoyés.", call.callId))
}

private suspend fun dependencyDown(call: io.ktor.server.application.ApplicationCall, e: Throwable) {
    log.warn("Dependency unavailable on {} [{}]: {}", call.request.path(), call.callId, e.message)
    call.response.headers.append(HttpHeaders.RetryAfter, "5")
    call.respond(
        HttpStatusCode.ServiceUnavailable,
        ApiError("TEMPORARILY_UNAVAILABLE", "Service momentanément indisponible. Réessayez dans quelques secondes.", call.callId),
    )
}
