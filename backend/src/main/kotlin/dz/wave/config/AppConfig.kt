package dz.wave.config

import dz.wave.payment.SatimConfig
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

/**
 * Twelve-factor configuration read from environment variables. Production refuses to start with
 * missing secrets; development generates throwaway ones so `./gradlew run` just works.
 */
data class AppConfig(
    val env: String,
    val port: Int,
    val managementPort: Int,
    val publicBaseUrl: String,
    val appReturnUrl: String,
    val corsOrigins: List<String>,
    val databaseUrl: String?,
    val databaseReadUrl: String?,
    val databaseUser: String?,
    val databasePassword: String?,
    val dbPoolSize: Int,
    val redisUrl: String?,
    val jwtSecret: String,
    val piiKey: ByteArray,
    val ticketKey: ByteArray,
    val ingestKeyId: String,
    val ingestSecret: String,
    val aisApiKey: String?,
    val satim: SatimConfig?,
    val stripeSecretKey: String?,
    val stripeWebhookSecret: String?,
    val sandboxPayments: Boolean,
    val serviceFeeDzd: Long,
    val agencyPhones: List<String>,
    val adminEmail: String?,
    val adminPassword: String?,
    val trustedProxyHops: Int,
    val searchRateLimitPerMinute: Int,
    val globalRateLimitPerMinute: Int,
) {
    val isProduction: Boolean get() = env == "prod"

    companion object {
        private val log = LoggerFactory.getLogger(AppConfig::class.java)
        private val random = SecureRandom()

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            fun get(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            val mode = get("WAVE_ENV") ?: "dev"
            val prod = mode == "prod"

            fun secret(name: String, bytes: Int): String {
                get(name)?.let { return it }
                require(!prod) { "$name must be set in production" }
                log.warn("{} not set: generating an ephemeral development secret", name)
                return Base64.getEncoder().encodeToString(ByteArray(bytes).also(random::nextBytes))
            }

            val jwtSecret = secret("WAVE_JWT_SECRET", 48)
            require(jwtSecret.length >= 32) { "WAVE_JWT_SECRET must be at least 32 characters" }
            val piiKey = Base64.getDecoder().decode(secret("WAVE_PII_KEY", 32))
            require(piiKey.size == 32) { "WAVE_PII_KEY must be 32 bytes, base64-encoded" }
            val ticketKey = Base64.getDecoder().decode(secret("WAVE_TICKET_KEY", 32))

            val satim = get("SATIM_USERNAME")?.let { user ->
                SatimConfig(
                    baseUrl = get("SATIM_BASE_URL") ?: "https://test.satim.dz/payment/rest",
                    userName = user,
                    password = get("SATIM_PASSWORD") ?: error("SATIM_PASSWORD missing"),
                    terminalId = get("SATIM_TERMINAL_ID") ?: error("SATIM_TERMINAL_ID missing"),
                )
            }
            val sandbox = get("WAVE_SANDBOX_PAYMENTS")?.toBooleanStrictOrNull() ?: !prod

            return AppConfig(
                env = mode,
                port = get("PORT")?.toInt() ?: 8080,
                managementPort = get("MANAGEMENT_PORT")?.toInt() ?: 9090,
                publicBaseUrl = get("WAVE_PUBLIC_BASE_URL") ?: "http://localhost:8080",
                appReturnUrl = get("WAVE_APP_RETURN_URL") ?: "http://localhost:8080/payment-result",
                corsOrigins = get("WAVE_CORS_ORIGINS")?.split(',')?.map { it.trim() } ?: if (prod) emptyList() else listOf("*"),
                databaseUrl = get("DATABASE_URL"),
                databaseReadUrl = get("DATABASE_READ_URL"),
                databaseUser = get("DATABASE_USER"),
                databasePassword = get("DATABASE_PASSWORD"),
                dbPoolSize = get("DB_POOL_SIZE")?.toInt() ?: 20,
                redisUrl = get("REDIS_URL"),
                jwtSecret = jwtSecret,
                piiKey = piiKey,
                ticketKey = ticketKey,
                ingestKeyId = get("WAVE_INGEST_KEY_ID") ?: "scraper",
                ingestSecret = secret("WAVE_INGEST_SECRET", 32),
                aisApiKey = get("AISSTREAM_API_KEY"),
                satim = satim,
                stripeSecretKey = get("STRIPE_SECRET_KEY"),
                stripeWebhookSecret = get("STRIPE_WEBHOOK_SECRET"),
                sandboxPayments = sandbox,
                serviceFeeDzd = get("WAVE_SERVICE_FEE_DZD")?.toLong() ?: 0L,
                agencyPhones = get("WAVE_AGENCY_PHONES")?.split(',')?.map { it.trim() } ?: listOf("0549 705 582", "0776 167 407"),
                adminEmail = get("WAVE_ADMIN_EMAIL"),
                adminPassword = get("WAVE_ADMIN_PASSWORD"),
                trustedProxyHops = get("WAVE_TRUSTED_PROXY_HOPS")?.toInt() ?: 0,
                searchRateLimitPerMinute = get("WAVE_SEARCH_RATE_LIMIT")?.toInt() ?: 120,
                globalRateLimitPerMinute = get("WAVE_GLOBAL_RATE_LIMIT")?.toInt() ?: 600,
            )
        }
    }
}
