@file:UseSerializers(InstantSerializer::class)

package dz.wave.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import dz.wave.common.ConflictException
import dz.wave.common.UnauthorizedException
import dz.wave.common.ValidationException
import dz.wave.domain.InstantSerializer
import dz.wave.domain.RuleSeverity
import dz.wave.domain.Violation
import dz.wave.infra.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
enum class Role { CUSTOMER, STAFF, ADMIN }

@Serializable
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val fullName: String,
    val phone: String? = null,
    val role: Role = Role.CUSTOMER,
    val createdAt: Instant,
)

@Serializable
data class PublicUser(val id: String, val email: String, val fullName: String, val phone: String?, val role: Role)

fun User.toPublic() = PublicUser(id, email, fullName, phone, role)

@Serializable
data class RegisterRequest(val email: String, val password: String, val fullName: String, val phone: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: PublicUser,
)

/** Argon2id with OWASP's recommended parameters (19 MiB, 2 iterations, 1 lane). */
object PasswordHasher {
    private const val MEMORY_KB = 19_456
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder().withoutPadding()
    private val decoder = Base64.getDecoder()

    // Hashing is deliberately expensive; cap concurrency so a login burst cannot exhaust memory.
    private val dispatcher = Dispatchers.Default.limitedParallelism(4)

    suspend fun hash(password: String): String = withContext(dispatcher) {
        val salt = ByteArray(16).also(random::nextBytes)
        val out = derive(password, salt, MEMORY_KB, ITERATIONS, PARALLELISM)
        "\$argon2id\$v=19\$m=$MEMORY_KB,t=$ITERATIONS,p=$PARALLELISM\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(out)}"
    }

    suspend fun verify(password: String, encoded: String): Boolean = withContext(dispatcher) {
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[1] != "argon2id") return@withContext false
        val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
        val salt = decoder.decode(parts[4])
        val expected = decoder.decode(parts[5])
        val actual = derive(password, salt, params.getValue("m"), params.getValue("t"), params.getValue("p"))
        MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, memoryKb: Int, iterations: Int, parallelism: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        return ByteArray(32).also { generator.generateBytes(password.toCharArray(), it) }
    }
}

class JwtService(secret: String, private val clock: Clock, val accessTtl: Duration = Duration.ofMinutes(15)) {
    private val algorithm = Algorithm.HMAC256(secret)

    /** Built on the injected clock so expiry checks agree with the clock that issued the token. */
    val verifier: JWTVerifier =
        (JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE) as JWTVerifier.BaseVerification).build(clock)

    fun issue(user: User): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withSubject(user.id)
        .withClaim("role", user.role.name)
        .withIssuedAt(Date.from(clock.instant()))
        .withExpiresAt(Date.from(clock.instant().plus(accessTtl)))
        .withJWTId(UUID.randomUUID().toString())
        .sign(algorithm)

    companion object {
        const val ISSUER = "wave-api"
        const val AUDIENCE = "wave-app"
    }
}

data class RefreshToken(
    val id: String,
    val userId: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

interface UserRepository {
    suspend fun create(user: User): Boolean
    suspend fun findByEmail(email: String): User?
    suspend fun findById(id: String): User?
    suspend fun saveRefresh(token: RefreshToken)
    suspend fun findRefresh(hash: String): RefreshToken?
    suspend fun revokeRefresh(id: String, at: Instant)
    suspend fun revokeAllRefresh(userId: String, at: Instant)
}

class InMemoryUserRepository : UserRepository {
    private val users = ConcurrentHashMap<String, User>()
    private val tokens = ConcurrentHashMap<String, RefreshToken>()

    override suspend fun create(user: User): Boolean =
        users.values.none { it.email == user.email } && users.putIfAbsent(user.id, user) == null

    override suspend fun findByEmail(email: String) = users.values.firstOrNull { it.email == email }
    override suspend fun findById(id: String) = users[id]
    override suspend fun saveRefresh(token: RefreshToken) {
        tokens[token.tokenHash] = token
    }
    override suspend fun findRefresh(hash: String) = tokens[hash]
    override suspend fun revokeRefresh(id: String, at: Instant) {
        tokens.replaceAll { _, t -> if (t.id == id) t.copy(revokedAt = at) else t }
    }
    override suspend fun revokeAllRefresh(userId: String, at: Instant) {
        tokens.replaceAll { _, t -> if (t.userId == userId && t.revokedAt == null) t.copy(revokedAt = at) else t }
    }
}

class PostgresUserRepository(private val db: Database) : UserRepository {
    override suspend fun create(user: User): Boolean = db.tx { conn ->
        conn.prepareStatement(
            """
            INSERT INTO users (id, email, password_hash, full_name, phone, role, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, UUID.fromString(user.id))
            st.setString(2, user.email)
            st.setString(3, user.passwordHash)
            st.setString(4, user.fullName)
            st.setString(5, user.phone)
            st.setString(6, user.role.name)
            st.setTimestamp(7, Timestamp.from(user.createdAt))
            st.executeUpdate() == 1
        }
    }

    override suspend fun findByEmail(email: String): User? = db.read { conn ->
        conn.prepareStatement("SELECT * FROM users WHERE lower(email) = lower(?)").use { st ->
            st.setString(1, email)
            st.executeQuery().use { rs -> if (rs.next()) user(rs) else null }
        }
    }

    override suspend fun findById(id: String): User? = db.read { conn ->
        conn.prepareStatement("SELECT * FROM users WHERE id = ?").use { st ->
            st.setObject(1, UUID.fromString(id))
            st.executeQuery().use { rs -> if (rs.next()) user(rs) else null }
        }
    }

    override suspend fun saveRefresh(token: RefreshToken) = db.tx { conn ->
        conn.prepareStatement(
            "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, now())",
        ).use { st ->
            st.setObject(1, UUID.fromString(token.id))
            st.setObject(2, UUID.fromString(token.userId))
            st.setString(3, token.tokenHash)
            st.setTimestamp(4, Timestamp.from(token.expiresAt))
            st.executeUpdate()
        }
        Unit
    }

    override suspend fun findRefresh(hash: String): RefreshToken? = db.read { conn ->
        conn.prepareStatement("SELECT * FROM refresh_tokens WHERE token_hash = ?").use { st ->
            st.setString(1, hash)
            st.executeQuery().use { rs ->
                if (!rs.next()) {
                    null
                } else {
                    RefreshToken(
                        id = rs.getObject("id").toString(),
                        userId = rs.getObject("user_id").toString(),
                        tokenHash = rs.getString("token_hash"),
                        expiresAt = rs.getTimestamp("expires_at").toInstant(),
                        revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
                    )
                }
            }
        }
    }

    override suspend fun revokeRefresh(id: String, at: Instant) = db.tx { conn ->
        conn.prepareStatement("UPDATE refresh_tokens SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL").use { st ->
            st.setTimestamp(1, Timestamp.from(at))
            st.setObject(2, UUID.fromString(id))
            st.executeUpdate()
        }
        Unit
    }

    override suspend fun revokeAllRefresh(userId: String, at: Instant) = db.tx { conn ->
        conn.prepareStatement("UPDATE refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL").use { st ->
            st.setTimestamp(1, Timestamp.from(at))
            st.setObject(2, UUID.fromString(userId))
            st.executeUpdate()
        }
        Unit
    }

    private fun user(rs: ResultSet) = User(
        id = rs.getObject("id").toString(),
        email = rs.getString("email"),
        passwordHash = rs.getString("password_hash"),
        fullName = rs.getString("full_name"),
        phone = rs.getString("phone"),
        role = Role.valueOf(rs.getString("role")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}

class AuthService(
    private val users: UserRepository,
    private val jwt: JwtService,
    private val clock: Clock,
    private val refreshTtl: Duration = Duration.ofDays(30),
) {
    private val random = SecureRandom()

    // Verifying against a real hash when the user does not exist keeps timings indistinguishable.
    private val dummyHash = "\$argon2id\$v=19\$m=19456,t=2,p=1\$c29tZXNhbHRzb21lc2FsdA\$Hq3o1hG6cH8m3l2iV+3x6Q1e3B8s0h3Pq6pX4xg0d2E"

    suspend fun register(req: RegisterRequest): TokenPair {
        val email = req.email.trim().lowercase()
        val problems = mutableListOf<Violation>()
        if (!EMAIL.matches(email) || email.length > 254) problems += violation("EMAIL_INVALID", "email", "Adresse e-mail invalide.")
        if (!strongEnough(req.password)) {
            problems += violation("PASSWORD_WEAK", "password", "Mot de passe trop faible : 10 caractères minimum, avec lettres et chiffres.")
        }
        if (req.fullName.trim().length !in 2..80) problems += violation("NAME_INVALID", "fullName", "Nom invalide.")
        if (problems.isNotEmpty()) throw ValidationException(problems)
        val user = User(
            id = UUID.randomUUID().toString(),
            email = email,
            passwordHash = PasswordHasher.hash(req.password),
            fullName = req.fullName.trim(),
            phone = req.phone?.trim(),
            createdAt = clock.instant(),
        )
        if (!users.create(user)) throw ConflictException("EMAIL_TAKEN", "Un compte existe déjà avec cette adresse.")
        return issue(user)
    }

    suspend fun login(req: LoginRequest): TokenPair {
        val user = users.findByEmail(req.email.trim().lowercase())
        val ok = PasswordHasher.verify(req.password, user?.passwordHash ?: dummyHash)
        if (user == null || !ok) throw UnauthorizedException("Identifiants invalides.")
        return issue(user)
    }

    suspend fun refresh(token: String): TokenPair {
        val now = clock.instant()
        val stored = users.findRefresh(sha256(token)) ?: throw UnauthorizedException("Session expirée.")
        if (stored.revokedAt != null) {
            // A rotated token used twice means it leaked: end every session of that user.
            users.revokeAllRefresh(stored.userId, now)
            throw UnauthorizedException("Session expirée.")
        }
        if (stored.expiresAt.isBefore(now)) throw UnauthorizedException("Session expirée.")
        users.revokeRefresh(stored.id, now)
        val user = users.findById(stored.userId) ?: throw UnauthorizedException("Session expirée.")
        return issue(user)
    }

    suspend fun logout(token: String) {
        users.findRefresh(sha256(token))?.let { users.revokeRefresh(it.id, clock.instant()) }
    }

    suspend fun ensureAdmin(email: String, password: String) {
        if (users.findByEmail(email.lowercase()) != null) return
        users.create(
            User(UUID.randomUUID().toString(), email.lowercase(), PasswordHasher.hash(password), "WAVE Admin", null, Role.ADMIN, clock.instant()),
        )
    }

    suspend fun user(id: String): User? = users.findById(id)

    private suspend fun issue(user: User): TokenPair {
        val raw = ByteArray(32).also(random::nextBytes)
        val refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        users.saveRefresh(RefreshToken(UUID.randomUUID().toString(), user.id, sha256(refresh), clock.instant().plus(refreshTtl), null))
        return TokenPair(jwt.issue(user), refresh, jwt.accessTtl.seconds, user.toPublic())
    }

    private fun violation(code: String, field: String, message: String) = Violation(code, RuleSeverity.ERROR, message, field)

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[a-z]{2,}$")
        private val COMMON = setOf("password123", "azerty1234", "1234567890", "qwerty1234", "motdepasse1")

        fun strongEnough(p: String) =
            p.length in 10..128 && p.any(Char::isLetter) && p.any(Char::isDigit) && p.lowercase() !in COMMON && p.toSet().size >= 5

        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
