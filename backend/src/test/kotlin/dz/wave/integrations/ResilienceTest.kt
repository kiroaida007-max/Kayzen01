package dz.wave.integrations

import dz.wave.Fixtures
import dz.wave.api.HealthResponse
import dz.wave.api.waveModule
import dz.wave.pricing.Consumption
import dz.wave.booking.RedisHoldStore
import dz.wave.common.AppJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.SQLTransientConnectionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A Postgres or Redis incident must degrade booking, never take search down. */
class ResilienceTest {

    @Test
    fun `search keeps answering when the Redis connection is lost`() {
        val url = System.getenv("WAVE_TEST_REDIS_URL")
        assumeTrue(url != null, "WAVE_TEST_REDIS_URL not set")
        val client = RedisClient.create(url)
        val connection = client.connect()
        val store = RedisHoldStore(connection, Fixtures.clock)
        assertEquals(Consumption.NONE, store.consumed("BAL-VLC-MOS-20261014-2300"))
        connection.close()
        // Previously an exception (and a 500 on search); now the last known level, or none.
        assertEquals(Consumption.NONE, store.consumed("BAL-VLC-MOS-20261014-2300"))
        assertEquals(Consumption.NONE, store.consumed("AF-ALG-MRS-20261016-2000"))
        client.shutdown()
    }

    @Test
    fun `dependency failures answer 503 with Retry-After`() = testApplication {
        val container = Fixtures.container()
        application {
            waveModule(container)
            routing {
                get("/test/redis-down") { throw RedisConnectionException("Unable to connect") }
                get("/test/db-down") { throw SQLTransientConnectionException("Connection is not available, request timed out") }
            }
        }
        for (path in listOf("/test/redis-down", "/test/db-down")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, path)
            assertEquals("5", response.headers[HttpHeaders.RetryAfter])
            assertTrue(response.bodyAsText().contains("TEMPORARILY_UNAVAILABLE"))
        }
    }

    @Test
    fun `readiness reports dependencies without failing the probe`() = testApplication {
        val container = Fixtures.container()
        application { waveModule(container) }
        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        val health = AppJson.decodeFromString(HealthResponse.serializer(), response.bodyAsText())
        assertEquals("ready", health.status)
        assertEquals(mapOf("database" to true, "redis" to true), health.dependencies)
    }
}
