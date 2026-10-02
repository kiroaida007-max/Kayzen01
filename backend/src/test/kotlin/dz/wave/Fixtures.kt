package dz.wave

import dz.wave.catalog.CatalogData
import dz.wave.config.AppConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

object Fixtures {
    /** Friday 2 October 2026, 10:00 in Algiers. */
    val NOW: Instant = Instant.parse("2026-10-02T09:00:00Z")
    val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    const val INGEST_SECRET = "test-ingest-secret-0123456789"

    val catalog: CatalogData by lazy { CatalogData.fromResources() }

    fun config(extra: Map<String, String> = emptyMap()): AppConfig = AppConfig.fromEnv(
        mapOf(
            "WAVE_ENV" to "test",
            "WAVE_INGEST_SECRET" to INGEST_SECRET,
            "WAVE_PUBLIC_BASE_URL" to "http://localhost",
        ) + extra,
    )

    fun container(clock: Clock = this.clock, extra: Map<String, String> = emptyMap()) =
        AppContainer(config(extra), clock, catalog)
}
