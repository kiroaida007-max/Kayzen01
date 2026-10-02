package dz.wave.infra

import com.github.benmanes.caffeine.cache.Caffeine
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Fan-out between pods: catalog version bumps and AIS fixes. */
interface ClusterBus : AutoCloseable {
    fun publish(channel: String, message: String)
    fun subscribe(channel: String, handler: (String) -> Unit)
    override fun close() {}
}

/** Single-node deployments and tests: delivers in-process. */
class LocalBus : ClusterBus {
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()
    override fun publish(channel: String, message: String) = handlers[channel].orEmpty().forEach { it(message) }
    override fun subscribe(channel: String, handler: (String) -> Unit) {
        handlers.getOrPut(channel) { CopyOnWriteArrayList() } += handler
    }
}

class RedisBus(client: RedisClient) : ClusterBus {
    private val log = LoggerFactory.getLogger(RedisBus::class.java)
    private val pub = client.connect()
    private val sub = client.connectPubSub()
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()

    init {
        sub.addListener(
            object : RedisPubSubAdapter<String, String>() {
                override fun message(channel: String, message: String) {
                    handlers[channel].orEmpty().forEach { handler ->
                        runCatching { handler(message) }.onFailure { log.warn("Bus handler failed on {}", channel, it) }
                    }
                }
            },
        )
    }

    override fun publish(channel: String, message: String) {
        pub.async().publish(channel, message)
    }

    override fun subscribe(channel: String, handler: (String) -> Unit) {
        val list = handlers.getOrPut(channel) { CopyOnWriteArrayList() }
        if (list.isEmpty()) sub.sync().subscribe(channel)
        list += handler
    }

    override fun close() {
        sub.close()
        pub.close()
    }
}

/**
 * Makes POST /bookings safe to retry (mobile networks, double taps): the first request claims the
 * key, later ones get the stored result, concurrent ones are told the first is still running.
 */
interface IdempotencyStore {
    suspend fun get(key: String): String?

    /** Atomically marks the key as in progress; false if it was already claimed or completed. */
    suspend fun claim(key: String, ttl: Duration): Boolean
    suspend fun complete(key: String, value: String, ttl: Duration)
    suspend fun release(key: String)

    companion object {
        const val IN_PROGRESS = "__in_progress__"
    }
}

class InMemoryIdempotencyStore : IdempotencyStore {
    private val cache = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofHours(24)).build<String, String>()
    override suspend fun get(key: String): String? = cache.getIfPresent(key)
    override suspend fun claim(key: String, ttl: Duration): Boolean =
        cache.asMap().putIfAbsent(key, IdempotencyStore.IN_PROGRESS) == null
    override suspend fun complete(key: String, value: String, ttl: Duration) = cache.put(key, value)
    override suspend fun release(key: String) = cache.invalidate(key)
}

class RedisIdempotencyStore(private val connection: StatefulRedisConnection<String, String>) : IdempotencyStore {
    private fun k(key: String) = "wave:idem:$key"
    override suspend fun get(key: String): String? = connection.async().get(k(key)).await()
    override suspend fun claim(key: String, ttl: Duration): Boolean =
        connection.async().set(k(key), IdempotencyStore.IN_PROGRESS, SetArgs().nx().ex(ttl.seconds)).await() == "OK"
    override suspend fun complete(key: String, value: String, ttl: Duration) {
        connection.async().set(k(key), value, SetArgs().ex(ttl.seconds)).await()
    }
    override suspend fun release(key: String) {
        connection.async().del(k(key)).await()
    }
}

/** Lease so that singleton jobs (the AIS socket) run on exactly one pod. */
interface LeaderLock {
    suspend fun tryAcquire(name: String, owner: String, lease: Duration): Boolean
}

class AlwaysLeader : LeaderLock {
    override suspend fun tryAcquire(name: String, owner: String, lease: Duration) = true
}

class RedisLeaderLock(private val connection: StatefulRedisConnection<String, String>) : LeaderLock {
    override suspend fun tryAcquire(name: String, owner: String, lease: Duration): Boolean {
        val key = "wave:leader:$name"
        val acquired = connection.async().set(key, owner, SetArgs().nx().px(lease.toMillis())).await() == "OK"
        if (acquired) return true
        // Renew if we already own it.
        val renewed = connection.async().eval<Long>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) else return 0 end",
            io.lettuce.core.ScriptOutputType.INTEGER,
            arrayOf(key),
            owner,
            lease.toMillis().toString(),
        ).await()
        return renewed == 1L
    }
}
