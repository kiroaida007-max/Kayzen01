package dz.wave.api

import dz.wave.Fixtures
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RateLimitTest {

    private val calendar = "/api/v1/search/calendar?from=DZALG&to=FRMRS&month=2026-11&adults=1"

    @Test
    fun `travellers sharing a carrier NAT address get their own buckets`() = testApplication {
        val container = Fixtures.container(extra = mapOf("WAVE_SEARCH_RATE_LIMIT" to "3"))
        application { waveModule(container) }

        // Ten app installs behind the same public IP: none of them is throttled.
        repeat(10) { install ->
            val status = client.get(calendar) { header("X-Wave-Client", "install-${install.toString().padStart(12, '0')}") }.status
            assertEquals(HttpStatusCode.OK, status, "install $install")
        }

        // One install still gets its own limit.
        val statuses = (1..4).map { client.get(calendar) { header("X-Wave-Client", "install-000000000042") }.status }
        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.TooManyRequests), statuses)
    }

    @Test
    fun `malformed client ids fall back to the IP bucket`() = testApplication {
        val container = Fixtures.container(extra = mapOf("WAVE_SEARCH_RATE_LIMIT" to "2"))
        application { waveModule(container) }

        val statuses = listOf("x", "<script>", "", "a".repeat(200)).map { id -> client.get(calendar) { header("X-Wave-Client", id) }.status }
        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.TooManyRequests, HttpStatusCode.TooManyRequests), statuses)
    }
}
