package dz.wave

import dz.wave.auth.AuthService
import dz.wave.auth.InMemoryUserRepository
import dz.wave.auth.JwtService
import dz.wave.auth.PostgresUserRepository
import dz.wave.booking.BookingService
import dz.wave.booking.HoldStore
import dz.wave.booking.InMemoryBookingRepository
import dz.wave.booking.InMemoryHoldStore
import dz.wave.booking.PiiCipher
import dz.wave.booking.PostgresBookingRepository
import dz.wave.booking.QuoteService
import dz.wave.booking.RedisHoldStore
import dz.wave.booking.TicketSigner
import dz.wave.catalog.CatalogData
import dz.wave.catalog.CatalogStore
import dz.wave.common.AppJson
import dz.wave.config.AppConfig
import dz.wave.content.ContentService
import dz.wave.currency.CurrencyService
import dz.wave.infra.AlwaysLeader
import dz.wave.infra.ClusterBus
import dz.wave.infra.Database
import dz.wave.infra.IdempotencyStore
import dz.wave.infra.InMemoryIdempotencyStore
import dz.wave.infra.LeaderLock
import dz.wave.infra.LocalBus
import dz.wave.infra.RedisBus
import dz.wave.infra.RedisIdempotencyStore
import dz.wave.infra.RedisLeaderLock
import dz.wave.ingest.InMemoryOverlayRepository
import dz.wave.ingest.IngestService
import dz.wave.ingest.PostgresOverlayRepository
import dz.wave.live.AisFix
import dz.wave.live.AisStreamClient
import dz.wave.live.LiveHub
import dz.wave.live.VesselTracker
import dz.wave.payment.AgencyPaymentProvider
import dz.wave.payment.PaymentProvider
import dz.wave.payment.PaymentService
import dz.wave.payment.SandboxPaymentProvider
import dz.wave.payment.SatimProvider
import dz.wave.payment.StripeProvider
import dz.wave.pricing.InventoryModel
import dz.wave.pricing.PricingEngine
import dz.wave.pricing.PromotionEngine
import dz.wave.pricing.SeasonCalendar
import dz.wave.search.LegEvaluator
import dz.wave.search.SearchService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.core.instrument.Gauge
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Wires every component from configuration. Tests build one with in-memory adapters. */
class AppContainer(
    val config: AppConfig,
    val clock: Clock = Clock.systemUTC(),
    catalogData: CatalogData = CatalogData.fromResources(),
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(AppContainer::class.java)
    val instanceId: String = UUID.randomUUID().toString().take(8)

    val database: Database? = config.databaseUrl?.let { url ->
        Database(
            primary = Database.parse(url, config.databaseUser, config.databasePassword),
            replica = config.databaseReadUrl?.let { Database.parse(it, config.databaseUser, config.databasePassword) },
            poolSize = config.dbPoolSize,
        )
    }
    // Fail fast instead of Lettuce's defaults (60 s timeout, unbounded queue while disconnected):
    // during a Redis incident search degrades instantly and booking answers 503.
    private val redisClient: RedisClient? = config.redisUrl?.let { url ->
        RedisClient.create(RedisURI.create(url).apply { timeout = Duration.ofSeconds(1) }).apply {
            options = ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .requestQueueSize(10_000)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(1)))
                .build()
        }
    }
    private val redis: StatefulRedisConnection<String, String>? = redisClient?.connect()

    val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val catalog = CatalogStore(catalogData, clock)
    val currency = CurrencyService()
    val holds: HoldStore = redis?.let { RedisHoldStore(it, clock) } ?: InMemoryHoldStore(clock)
    private val evaluator = LegEvaluator(
        pricing = PricingEngine(),
        seasons = SeasonCalendar(catalogData.seasons),
        inventory = InventoryModel(clock),
        consumption = holds,
        promotions = PromotionEngine(catalogData.promotions),
        clock = clock,
    )
    val search = SearchService(catalog, evaluator, currency, clock)
    val quotes = QuoteService(catalog, evaluator, currency, clock, config.serviceFeeDzd)
    val bookings = BookingService(
        quotes = quotes,
        holds = holds,
        repository = database?.let { PostgresBookingRepository(it, PiiCipher(config.piiKey)) } ?: InMemoryBookingRepository(),
        ticketSigner = TicketSigner(config.ticketKey),
        clock = clock,
    )
    val ticketSigner = TicketSigner(config.ticketKey)

    val http = HttpClient(CIO) {
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    private val providers: List<PaymentProvider> = buildList {
        add(AgencyPaymentProvider(config.agencyPhones))
        config.satim?.let { add(SatimProvider(it, http)) }
        config.stripeSecretKey?.let { add(StripeProvider(it, config.stripeWebhookSecret, http, clock)) }
        if (config.sandboxPayments) add(SandboxPaymentProvider(config.publicBaseUrl))
    }
    val stripe: StripeProvider? = providers.filterIsInstance<StripeProvider>().firstOrNull()

    // Real gateways take precedence over the sandbox for the methods they serve.
    val payments = PaymentService(providers.sortedBy { if (it is SandboxPaymentProvider) 0 else 1 }, bookings, config.publicBaseUrl, config.appReturnUrl, clock)

    val bus: ClusterBus = redisClient?.let { RedisBus(it) } ?: LocalBus()
    val ingest = IngestService(
        catalog = catalog,
        overlay = database?.let { PostgresOverlayRepository(it) } ?: InMemoryOverlayRepository(),
        currency = currency,
        clock = clock,
        onCatalogChanged = { version -> bus.publish(CATALOG_CHANNEL, "$instanceId:$version") },
    )
    val live = LiveHub(catalog, VesselTracker(), clock)
    val jwt = JwtService(config.jwtSecret, clock)
    val auth = AuthService(database?.let { PostgresUserRepository(it) } ?: InMemoryUserRepository(), jwt, clock)
    val idempotency: IdempotencyStore = redis?.let { RedisIdempotencyStore(it) } ?: InMemoryIdempotencyStore()
    private val leader: LeaderLock = redis?.let { RedisLeaderLock(it) } ?: AlwaysLeader()
    val content = ContentService(catalogData, clock)

    /**
     * Business gauges next to the JVM/HTTP ones: what dashboards and alerts actually watch.
     * Called once the metrics plugin is installed, so its meter filters apply to them.
     */
    fun registerGauges() {
        for (name in listOf("database", "redis")) {
            Gauge.builder("wave.dependency.up") { if (dependencies[name] == true) 1.0 else 0.0 }
                .tag("dependency", name).description("1 when the shared dependency answers").register(metrics)
        }
        Gauge.builder("wave.catalog.version") { catalog.current().version.toDouble() }
            .description("Version of the in-memory catalog (moves on every accepted ingest)").register(metrics)
        Gauge.builder("wave.live.sailings") { catalog.overlaySize().toDouble() }
            .description("Crossings currently backed by scraped (LIVE) data").register(metrics)
        Gauge.builder("wave.catalog.sailings") { catalog.current().sailingCount.toDouble() }
            .description("Crossings searchable on this pod").register(metrics)
    }

    fun count(name: String, amount: Double = 1.0, vararg tags: String) = metrics.counter(name, *tags).increment(amount)

    private val jobs = mutableListOf<Job>()

    /** Last known state of the shared dependencies, refreshed in the background (never on a probe). */
    @Volatile
    var dependencies: Map<String, Boolean> = mapOf("database" to true, "redis" to true)
        private set

    private fun checkDependencies(): Map<String, Boolean> = mapOf(
        "database" to (database?.healthy() ?: true),
        "redis" to (redis?.let { runCatching { it.sync().ping() == "PONG" }.getOrDefault(false) } ?: true),
    )

    fun start(scope: CoroutineScope) {
        database?.migrate()
        scope.launch {
            runCatching { ingest.reloadOverlay() }.onFailure { log.warn("Could not load live overlay", it) }
            config.adminEmail?.let { email -> config.adminPassword?.let { auth.ensureAdmin(email, it) } }
        }
        bus.subscribe(CATALOG_CHANNEL) { message ->
            if (!message.startsWith("$instanceId:")) scope.launch { ingest.reloadOverlay() }
        }
        bus.subscribe(AIS_CHANNEL) { message ->
            runCatching { live.accept(AppJson.decodeFromString(AisFix.serializer(), message)) }
        }
        jobs += live.start(scope)
        jobs += scope.launch { sweepHolds() }
        jobs += scope.launch(Dispatchers.IO) {
            while (isActive) {
                val now = checkDependencies()
                if (now != dependencies) log.warn("Dependencies changed: {}", now)
                dependencies = now
                delay(5_000)
            }
        }
        config.aisApiKey?.let { key -> jobs += scope.launch { runAisWhenLeader(scope, key) } }
        log.info("WAVE backend {} started (env={}, db={}, redis={})", instanceId, config.env, database != null, redis != null)
    }

    fun publishFix(fix: AisFix) = bus.publish(AIS_CHANNEL, AppJson.encodeToString(AisFix.serializer(), fix))

    private suspend fun CoroutineScope.sweepHolds() {
        while (isActive) {
            runCatching { bookings.expireHolds() }
                .onSuccess {
                    if (it > 0) {
                        log.info("Expired {} unpaid bookings", it)
                        count("wave.bookings", it.toDouble(), "event", "expired")
                    }
                }
                .onFailure { log.warn("Hold sweeper failed", it) }
            delay(60_000)
        }
    }

    private suspend fun CoroutineScope.runAisWhenLeader(scope: CoroutineScope, apiKey: String) {
        var client: Job? = null
        while (isActive) {
            val isLeader = runCatching { leader.tryAcquire("ais", instanceId, Duration.ofSeconds(30)) }.getOrDefault(false)
            if (isLeader && client?.isActive != true) {
                client = AisStreamClient(
                    http = http,
                    apiKey = apiKey,
                    mmsis = { catalog.catalogData.vessels.mapNotNull { it.mmsi } },
                    onFix = { fix -> publishFix(fix) },
                ).start(scope)
            } else if (!isLeader) {
                client?.cancel()
                client = null
            }
            delay(10_000)
        }
    }

    override fun close() {
        jobs.forEach { it.cancel() }
        runCatching { bus.close() }
        runCatching { http.close() }
        runCatching { redis?.close() }
        runCatching { redisClient?.shutdown() }
        runCatching { database?.close() }
    }

    companion object {
        const val CATALOG_CHANNEL = "wave:catalog"
        const val AIS_CHANNEL = "wave:ais"
    }
}
