package dz.wave.booking

import dz.wave.common.AppJson
import dz.wave.domain.Booking
import dz.wave.domain.BookingStatus
import dz.wave.domain.ContactInfo
import dz.wave.domain.Traveller
import dz.wave.domain.VehicleDetails
import dz.wave.infra.Database
import kotlinx.serialization.Serializable
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface BookingRepository {
    suspend fun insert(booking: Booking)

    /** Optimistic locking: succeeds only if the stored version equals [booking].version - 1. */
    suspend fun update(booking: Booking): Boolean
    suspend fun findByReference(reference: String): Booking?
    suspend fun findByUser(userId: String): List<Booking>
    suspend fun findByStatus(status: BookingStatus, limit: Int): List<Booking>
    suspend fun findExpiredHolds(now: Instant, limit: Int): List<Booking>
}

class InMemoryBookingRepository : BookingRepository {
    private val byRef = ConcurrentHashMap<String, Booking>()

    override suspend fun insert(booking: Booking) {
        require(byRef.putIfAbsent(booking.reference, booking) == null) { "Duplicate reference" }
    }

    override suspend fun update(booking: Booking): Boolean {
        var updated = false
        byRef.computeIfPresent(booking.reference) { _, current ->
            if (current.version == booking.version - 1) {
                updated = true
                booking
            } else {
                current
            }
        }
        return updated
    }

    override suspend fun findByReference(reference: String): Booking? = byRef[reference]

    override suspend fun findByUser(userId: String): List<Booking> =
        byRef.values.filter { it.userId == userId }.sortedByDescending { it.createdAt }

    override suspend fun findByStatus(status: BookingStatus, limit: Int): List<Booking> =
        byRef.values.filter { it.status == status }.sortedByDescending { it.createdAt }.take(limit)

    override suspend fun findExpiredHolds(now: Instant, limit: Int): List<Booking> =
        byRef.values.filter { it.status == BookingStatus.HELD && it.holdExpiresAt?.isBefore(now) == true }.take(limit)
}

/** Personal data stored encrypted; everything else is plain JSONB for querying and support. */
@Serializable
private data class PiiBlob(
    val contact: ContactInfo,
    val travellers: List<Traveller>,
    val vehicleDetails: VehicleDetails? = null,
)

class PostgresBookingRepository(private val db: Database, private val cipher: PiiCipher) : BookingRepository {

    override suspend fun insert(booking: Booking) = db.tx { conn ->
        conn.prepareStatement(
            """
            INSERT INTO bookings (id, reference, status, user_id, pii, document, hold_id, hold_expires_at,
                                  total_minor, currency, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { st ->
            bind(st, booking)
            st.executeUpdate()
        }
        Unit
    }

    override suspend fun update(booking: Booking): Boolean = db.tx { conn ->
        conn.prepareStatement(
            """
            UPDATE bookings SET status = ?, user_id = ?, pii = ?, document = ?::jsonb, hold_id = ?, hold_expires_at = ?,
                   total_minor = ?, currency = ?, updated_at = ?, version = ?
             WHERE reference = ? AND version = ?
            """.trimIndent(),
        ).use { st ->
            st.setString(1, booking.status.name)
            st.setObject(2, booking.userId?.let(UUID::fromString))
            st.setBytes(3, encryptPii(booking))
            st.setString(4, documentJson(booking))
            st.setString(5, booking.holdId)
            st.setTimestamp(6, booking.holdExpiresAt?.let(Timestamp::from))
            st.setLong(7, booking.quote.total.minor)
            st.setString(8, booking.quote.currency.name)
            st.setTimestamp(9, Timestamp.from(booking.updatedAt))
            st.setInt(10, booking.version)
            st.setString(11, booking.reference)
            st.setInt(12, booking.version - 1)
            st.executeUpdate() == 1
        }
    }

    override suspend fun findByReference(reference: String): Booking? = db.read { conn ->
        conn.prepareStatement("SELECT * FROM bookings WHERE reference = ?").use { st ->
            st.setString(1, reference)
            st.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
        }
    }

    override suspend fun findByUser(userId: String): List<Booking> = db.read { conn ->
        conn.prepareStatement("SELECT * FROM bookings WHERE user_id = ? ORDER BY created_at DESC LIMIT 200").use { st ->
            st.setObject(1, UUID.fromString(userId))
            st.executeQuery().use { rs -> generateSequence { if (rs.next()) map(rs) else null }.toList() }
        }
    }

    override suspend fun findByStatus(status: BookingStatus, limit: Int): List<Booking> = db.read { conn ->
        conn.prepareStatement("SELECT * FROM bookings WHERE status = ? ORDER BY created_at DESC LIMIT ?").use { st ->
            st.setString(1, status.name)
            st.setInt(2, limit)
            st.executeQuery().use { rs -> generateSequence { if (rs.next()) map(rs) else null }.toList() }
        }
    }

    override suspend fun findExpiredHolds(now: Instant, limit: Int): List<Booking> = db.read { conn ->
        conn.prepareStatement(
            "SELECT * FROM bookings WHERE status = 'HELD' AND hold_expires_at < ? ORDER BY hold_expires_at LIMIT ?",
        ).use { st ->
            st.setTimestamp(1, Timestamp.from(now))
            st.setInt(2, limit)
            st.executeQuery().use { rs -> generateSequence { if (rs.next()) map(rs) else null }.toList() }
        }
    }

    private fun bind(st: java.sql.PreparedStatement, b: Booking) {
        st.setObject(1, UUID.fromString(b.id))
        st.setString(2, b.reference)
        st.setString(3, b.status.name)
        st.setObject(4, b.userId?.let(UUID::fromString))
        st.setBytes(5, encryptPii(b))
        st.setString(6, documentJson(b))
        st.setString(7, b.holdId)
        st.setTimestamp(8, b.holdExpiresAt?.let(Timestamp::from))
        st.setLong(9, b.quote.total.minor)
        st.setString(10, b.quote.currency.name)
        st.setTimestamp(11, Timestamp.from(b.createdAt))
        st.setTimestamp(12, Timestamp.from(b.updatedAt))
        st.setInt(13, b.version)
    }

    private fun encryptPii(b: Booking): ByteArray =
        cipher.encrypt(AppJson.encodeToString(PiiBlob.serializer(), PiiBlob(b.contact, b.travellers, b.vehicleDetails)).toByteArray())

    /** The non-personal remainder of the booking (selection, quote, payments, ticket). */
    private fun documentJson(b: Booking): String = AppJson.encodeToString(
        Booking.serializer(),
        b.copy(contact = ContactInfo("", ""), travellers = emptyList(), vehicleDetails = null),
    )

    private fun map(rs: ResultSet): Booking {
        val doc = AppJson.decodeFromString(Booking.serializer(), rs.getString("document"))
        val pii = AppJson.decodeFromString(PiiBlob.serializer(), String(cipher.decrypt(rs.getBytes("pii"))))
        return doc.copy(
            status = BookingStatus.valueOf(rs.getString("status")),
            version = rs.getInt("version"),
            contact = pii.contact,
            travellers = pii.travellers,
            vehicleDetails = pii.vehicleDetails,
        )
    }
}
