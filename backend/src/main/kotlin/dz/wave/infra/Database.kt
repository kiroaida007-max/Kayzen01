package dz.wave.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.net.URI
import java.sql.Connection

data class JdbcTarget(val url: String, val user: String?, val password: String?)

/**
 * HikariCP pools (primary + optional read replica) behind a bounded coroutine dispatcher so that
 * blocking JDBC never starves Netty's event loops. In production the URLs point at PgBouncer.
 */
class Database(
    primary: JdbcTarget,
    replica: JdbcTarget? = null,
    poolSize: Int = 20,
) : AutoCloseable {
    private val writer = pool(primary, poolSize, "wave-primary", readOnly = false)
    private val reader = replica?.let { pool(it, poolSize, "wave-replica", readOnly = true) } ?: writer
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(poolSize * 2)

    fun migrate() {
        Flyway.configure()
            .dataSource(writer)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    suspend fun <T> read(block: (Connection) -> T): T = withContext(dispatcher) {
        reader.connection.use(block)
    }

    suspend fun <T> tx(block: (Connection) -> T): T = withContext(dispatcher) {
        writer.connection.use { conn ->
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun healthy(): Boolean = runCatching {
        writer.connection.use { it.isValid(2) }
    }.getOrDefault(false)

    override fun close() {
        writer.close()
        if (reader !== writer) (reader as HikariDataSource).close()
    }

    companion object {
        /** Accepts `jdbc:postgresql://...` or `postgres://user:pass@host:5432/db` (12-factor style). */
        fun parse(url: String, user: String? = null, password: String? = null): JdbcTarget {
            if (url.startsWith("jdbc:")) return JdbcTarget(url, user, password)
            val uri = URI(url.replaceFirst("postgres://", "postgresql://"))
            val (u, p) = uri.userInfo?.split(":", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: (user to password)
            val port = if (uri.port > 0) uri.port else 5432
            val query = uri.rawQuery?.let { "?$it" } ?: ""
            return JdbcTarget("jdbc:postgresql://${uri.host}:$port${uri.path}$query", u ?: user, p ?: password)
        }

        private fun pool(target: JdbcTarget, size: Int, name: String, readOnly: Boolean): HikariDataSource {
            val cfg = HikariConfig().apply {
                jdbcUrl = target.url
                username = target.user
                password = target.password
                maximumPoolSize = size
                minimumIdle = (size / 4).coerceAtLeast(1)
                poolName = name
                isReadOnly = readOnly
                connectionTimeout = 3_000
                validationTimeout = 2_000
                maxLifetime = 30 * 60_000
                // PgBouncer in transaction mode does not support server-side prepared statements.
                addDataSourceProperty("prepareThreshold", "0")
                addDataSourceProperty("reWriteBatchedInserts", "true")
            }
            return HikariDataSource(cfg)
        }
    }
}
