package dz.wave.booking

import dz.wave.common.AppJson
import dz.wave.domain.AccommodationType
import dz.wave.pricing.AvailabilityView
import dz.wave.pricing.Consumption
import dz.wave.search.ConsumptionReader
import dz.wave.search.ResourceNeed
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.RedisException
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ResourceLimits(
    val seats: Int,
    val cabins: Map<AccommodationType, Int>,
    val laneMeters: Int,
    val kennels: Int,
)

fun AvailabilityView.toResourceLimits() = ResourceLimits(seats, cabins, laneMeters, kennels)

@Serializable
data class HoldRequest(val sailingId: String, val need: ResourceNeedDto, val limits: ResourceLimits) {
    constructor(sailingId: String, need: ResourceNeed, limits: ResourceLimits) :
        this(sailingId, ResourceNeedDto(need.seats, need.cabins, need.laneMeters, need.kennels), limits)
}

@Serializable
data class ResourceNeedDto(
    val seats: Int,
    val cabins: Map<AccommodationType, Int>,
    val laneMeters: Int,
    val kennels: Int,
) {
    /** Flattened counter fields, e.g. `seats`, `cab:CABIN_INT_4`, `lane`, `kennel`. */
    fun fields(): Map<String, Int> = buildMap {
        if (seats > 0) put("seats", seats)
        cabins.forEach { (type, n) -> if (n > 0) put("cab:${type.name}", n) }
        if (laneMeters > 0) put("lane", laneMeters)
        if (kennels > 0) put("kennel", kennels)
    }
}

private fun ResourceLimits.limitFor(field: String): Int = when {
    field == "seats" -> seats
    field == "lane" -> laneMeters
    field == "kennel" -> kennels
    field.startsWith("cab:") -> cabins[AccommodationType.valueOf(field.removePrefix("cab:"))] ?: 0
    else -> 0
}

private fun consumptionOf(fields: Map<String, Int>) = Consumption(
    seats = fields["seats"] ?: 0,
    cabins = fields.filterKeys { it.startsWith("cab:") }
        .mapKeys { AccommodationType.valueOf(it.key.removePrefix("cab:")) },
    laneMeters = fields["lane"] ?: 0,
    kennels = fields["kennel"] ?: 0,
)

/**
 * Inventory WAVE holds while a traveller pays, then keeps once the booking is confirmed.
 * Placing a hold is atomic across all legs and resources: either everything fits or nothing is taken.
 */
interface HoldStore : ConsumptionReader {
    suspend fun place(holdId: String, requests: List<HoldRequest>, ttl: Duration): Boolean
    suspend fun extend(holdId: String, ttl: Duration)
    suspend fun commit(holdId: String)
    suspend fun release(holdId: String)
    suspend fun releaseExpired(now: Instant): Int
}

class InMemoryHoldStore(private val clock: Clock) : HoldStore {
    private data class Hold(val requests: List<HoldRequest>, val expiresAt: Instant?, val committed: Boolean)

    private val counters = ConcurrentHashMap<String, MutableMap<String, Int>>()
    private val holds = ConcurrentHashMap<String, Hold>()
    private val lock = Any()

    override fun consumed(sailingId: String): Consumption =
        counters[sailingId]?.let { synchronized(lock) { consumptionOf(it.toMap()) } } ?: Consumption.NONE

    override suspend fun place(holdId: String, requests: List<HoldRequest>, ttl: Duration): Boolean = synchronized(lock) {
        if (holds.containsKey(holdId)) return true
        val fits = requests.all { req ->
            val current = counters[req.sailingId].orEmpty()
            req.need.fields().all { (field, qty) -> (current[field] ?: 0) + qty <= req.limits.limitFor(field) }
        }
        if (!fits) return false
        requests.forEach { req ->
            val map = counters.getOrPut(req.sailingId) { HashMap() }
            req.need.fields().forEach { (field, qty) -> map.merge(field, qty, Int::plus) }
        }
        holds[holdId] = Hold(requests, clock.instant().plus(ttl), committed = false)
        true
    }

    override suspend fun extend(holdId: String, ttl: Duration) {
        synchronized(lock) {
            holds.computeIfPresent(holdId) { _, h -> if (h.committed) h else h.copy(expiresAt = clock.instant().plus(ttl)) }
        }
    }

    override suspend fun commit(holdId: String) {
        synchronized(lock) { holds.computeIfPresent(holdId) { _, h -> h.copy(expiresAt = null, committed = true) } }
    }

    override suspend fun release(holdId: String) {
        synchronized(lock) {
            val hold = holds.remove(holdId) ?: return
            hold.requests.forEach { req ->
                val map = counters[req.sailingId] ?: return@forEach
                req.need.fields().forEach { (field, qty) -> map.merge(field, -qty) { a, b -> (a + b).coerceAtLeast(0) } }
            }
        }
    }

    override suspend fun releaseExpired(now: Instant): Int {
        val expired = holds.filter { (_, h) -> !h.committed && h.expiresAt != null && h.expiresAt.isBefore(now) }.keys
        expired.forEach { release(it) }
        return expired.size
    }
}

/**
 * Redis implementation for multi-pod deployments. All keys share the `{inv}` hash tag so the Lua
 * scripts stay valid on Redis Cluster. Consumption is cached per pod for a few seconds because
 * search only needs an indicative level; the exact check happens inside the hold script.
 */
class RedisHoldStore(
    private val connection: StatefulRedisConnection<String, String>,
    private val clock: Clock,
    private val cacheTtl: Duration = Duration.ofSeconds(5),
) : HoldStore {
    private data class Cached(val value: Consumption, val at: Instant)

    private val cache = ConcurrentHashMap<String, Cached>()

    private fun counterKey(sailingId: String) = "wave:{inv}:sailing:$sailingId"
    private fun holdKey(holdId: String) = "wave:{inv}:hold:$holdId"
    private val expiryKey = "wave:{inv}:holds:expiry"

    override fun consumed(sailingId: String): Consumption {
        val now = clock.instant()
        val cached = cache[sailingId]
        cached?.takeIf { Duration.between(it.at, now) < cacheTtl }?.let { return it.value }
        val fields = try {
            connection.sync().hgetall(counterKey(sailingId)).mapValues { it.value.toIntOrNull() ?: 0 }
        } catch (e: RedisException) {
            // Search keeps answering with the last known level; capacity is re-checked atomically
            // by the hold script when booking, which fails cleanly while Redis is away.
            if (Duration.between(lastWarning, now) > Duration.ofSeconds(30)) {
                lastWarning = now
                log.warn("Hold counters unavailable, search uses last known availability: {}", e.message)
            }
            return cached?.value ?: Consumption.NONE
        }
        val value = consumptionOf(fields)
        cache[sailingId] = Cached(value, now)
        return value
    }

    @Volatile
    private var lastWarning: Instant = Instant.EPOCH
    private val log = org.slf4j.LoggerFactory.getLogger(RedisHoldStore::class.java)

    override suspend fun place(holdId: String, requests: List<HoldRequest>, ttl: Duration): Boolean {
        val keys = mutableListOf(holdKey(holdId), expiryKey)
        val args = mutableListOf(
            AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(HoldRequest.serializer()), requests),
            clock.instant().plus(ttl).epochSecond.toString(),
            holdId,
        )
        requests.forEach { req ->
            keys += counterKey(req.sailingId)
            val keyIndex = keys.size
            req.need.fields().forEach { (field, qty) ->
                args += listOf(keyIndex.toString(), field, qty.toString(), req.limits.limitFor(field).toString())
            }
        }
        val result = connection.async()
            .eval<Long>(PLACE_SCRIPT, ScriptOutputType.INTEGER, keys.toTypedArray(), *args.toTypedArray())
            .await()
        requests.forEach { cache.remove(it.sailingId) }
        return result == 1L
    }

    override suspend fun extend(holdId: String, ttl: Duration) {
        connection.async().zadd(expiryKey, clock.instant().plus(ttl).epochSecond.toDouble(), holdId).await()
    }

    override suspend fun commit(holdId: String) {
        connection.async().zrem(expiryKey, holdId).await()
    }

    override suspend fun release(holdId: String) {
        connection.async()
            .eval<Long>(RELEASE_SCRIPT, ScriptOutputType.INTEGER, arrayOf(holdKey(holdId), expiryKey), holdId)
            .await()
        cache.clear()
    }

    override suspend fun releaseExpired(now: Instant): Int {
        val expired = connection.async().zrangebyscore(expiryKey, io.lettuce.core.Range.create(0.0, now.epochSecond.toDouble())).await()
        expired.forEach { release(it) }
        return expired.size
    }

    companion object {
        // KEYS[1]=hold, KEYS[2]=expiry zset, KEYS[3..]=sailing counters
        // ARGV[1]=payload, ARGV[2]=expiry epoch, ARGV[3]=holdId, then quadruples (keyIndex, field, qty, limit)
        private val PLACE_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then return 1 end
            local i = 4
            while i <= #ARGV do
              local key = KEYS[tonumber(ARGV[i])]
              local cur = tonumber(redis.call('HGET', key, ARGV[i + 1]) or '0')
              if cur + tonumber(ARGV[i + 2]) > tonumber(ARGV[i + 3]) then return 0 end
              i = i + 4
            end
            i = 4
            while i <= #ARGV do
              redis.call('HINCRBY', KEYS[tonumber(ARGV[i])], ARGV[i + 1], tonumber(ARGV[i + 2]))
              i = i + 4
            end
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('ZADD', KEYS[2], tonumber(ARGV[2]), ARGV[3])
            return 1
        """.trimIndent()

        private val RELEASE_SCRIPT = """
            local payload = redis.call('GET', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            if not payload then return 0 end
            local holds = cjson.decode(payload)
            for _, h in ipairs(holds) do
              local key = 'wave:{inv}:sailing:' .. h['sailingId']
              local need = h['need']
              if need['seats'] > 0 then redis.call('HINCRBY', key, 'seats', -need['seats']) end
              if need['laneMeters'] > 0 then redis.call('HINCRBY', key, 'lane', -need['laneMeters']) end
              if need['kennels'] > 0 then redis.call('HINCRBY', key, 'kennel', -need['kennels']) end
              for t, n in pairs(need['cabins']) do
                if n > 0 then redis.call('HINCRBY', key, 'cab:' .. t, -n) end
              end
            end
            redis.call('DEL', KEYS[1])
            return 1
        """.trimIndent()
    }
}
