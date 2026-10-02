package dz.wave.live

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Client for aisstream.io (free global AIS over WebSocket). Subscribes to the western
 * Mediterranean, filtered on the MMSI of the ferries in the catalog, and reconnects with backoff.
 */
class AisStreamClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val mmsis: () -> List<String>,
    private val onFix: suspend (AisFix) -> Unit,
    private val url: String = "wss://stream.aisstream.io/v0/stream",
) {
    private val log = LoggerFactory.getLogger(AisStreamClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun start(scope: CoroutineScope): Job = scope.launch {
        var backoffMs = 1_000L
        while (isActive) {
            try {
                http.webSocket(urlString = url) {
                    send(Frame.Text(subscription().toString()))
                    log.info("AIS stream connected ({} ships)", mmsis().size)
                    backoffMs = 1_000L
                    for (frame in incoming) {
                        val text = when (frame) {
                            is Frame.Text -> frame.readText()
                            is Frame.Binary -> frame.data.decodeToString()
                            else -> continue
                        }
                        parse(text)?.let { onFix(it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("AIS stream error: {}", e.message)
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        }
    }

    internal fun subscription(): JsonObject = buildJsonObject {
        put("APIKey", apiKey)
        putJsonArray("BoundingBoxes") {
            // Western Mediterranean: Gibraltar to Sicily, North Africa to the French coast.
            addJsonArray {
                addJsonArray { add(30.0); add(-6.0) }
                addJsonArray { add(46.0); add(16.0) }
            }
        }
        putJsonArray("FiltersShipMMSI") { mmsis().forEach { add(it) } }
        putJsonArray("FilterMessageTypes") { add("PositionReport") }
    }

    internal fun parse(text: String): AisFix? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["MessageType"]?.jsonPrimitive?.content != "PositionReport") return null
        val meta = root["MetaData"]?.jsonObject ?: return null
        val report = root["Message"]?.jsonObject?.get("PositionReport")?.jsonObject ?: return null
        val mmsi = meta["MMSI"]?.jsonPrimitive?.content ?: report["UserID"]?.jsonPrimitive?.content ?: return null
        val lat = report["Latitude"]?.jsonPrimitive?.doubleOrNull ?: meta["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = report["Longitude"]?.jsonPrimitive?.doubleOrNull ?: meta["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val heading = report["TrueHeading"]?.jsonPrimitive?.doubleOrNull?.takeIf { it in 0.0..359.9 }
        val time = meta["time_utc"]?.jsonPrimitive?.content?.let(::parseTime) ?: Instant.now()
        AisFix(
            mmsi = mmsi,
            lat = lat,
            lon = lon,
            speedKn = report["Sog"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            courseDeg = report["Cog"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            heading = heading,
            timestamp = time,
        )
    }.getOrNull()

    companion object {
        /** aisstream sends e.g. `2026-10-02 08:15:30.123456789 +0000 UTC`. */
        private val AIS_TIME: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .appendPattern(" xx")
            .toFormatter()

        fun parseTime(raw: String): Instant? = runCatching {
            OffsetDateTime.parse(raw.removeSuffix(" UTC").trim(), AIS_TIME).toInstant()
        }.getOrNull()
    }
}
