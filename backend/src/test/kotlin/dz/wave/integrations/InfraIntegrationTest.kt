package dz.wave.integrations

import dz.wave.Fixtures
import dz.wave.booking.HoldRequest
import dz.wave.booking.PiiCipher
import dz.wave.booking.PostgresBookingRepository
import dz.wave.booking.RedisHoldStore
import dz.wave.booking.ResourceLimits
import dz.wave.booking.ResourceNeedDto
import dz.wave.domain.AccommodationType
import dz.wave.domain.Booking
import dz.wave.domain.BookingSelection
import dz.wave.domain.BookingStatus
import dz.wave.domain.ContactInfo
import dz.wave.domain.CurrencyCode
import dz.wave.domain.DocType
import dz.wave.domain.LegSelection
import dz.wave.domain.Money
import dz.wave.domain.PassengersInput
import dz.wave.domain.Quote
import dz.wave.domain.RateInfo
import dz.wave.domain.Sex
import dz.wave.domain.TravelDocument
import dz.wave.domain.Traveller
import dz.wave.infra.Database
import io.lettuce.core.RedisClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against real services when WAVE_TEST_DATABASE_URL / WAVE_TEST_REDIS_URL are set
 * (CI starts Postgres and Redis containers); skipped otherwise.
 */
class InfraIntegrationTest {
    private val dbUrl: String? = System.getenv("WAVE_TEST_DATABASE_URL")
    private val redisUrl: String? = System.getenv("WAVE_TEST_REDIS_URL")

    @Test
    fun `bookings round-trip through Postgres with encrypted PII`() = runBlocking {
        assumeTrue(dbUrl != null, "WAVE_TEST_DATABASE_URL not set")
        Database(Database.parse(dbUrl!!)).use { db ->
            db.migrate()
            val key = ByteArray(32).also(SecureRandom()::nextBytes)
            val repo = PostgresBookingRepository(db, PiiCipher(key))
            val booking = booking()
            repo.insert(booking)
            val loaded = assertNotNull(repo.findByReference(booking.reference))
            assertEquals("BENALI", loaded.travellers.single().lastName)
            assertEquals("P1234567", loaded.travellers.single().document.number)

            // The raw column never contains the passport number in clear text.
            val raw = db.read { conn ->
                conn.prepareStatement("SELECT pii, document::text FROM bookings WHERE reference = ?").use { st ->
                    st.setString(1, booking.reference)
                    st.executeQuery().use { rs -> rs.next(); String(rs.getBytes(1), Charsets.ISO_8859_1) + rs.getString(2) }
                }
            }
            assertTrue("P1234567" !in raw && "BENALI" !in raw)

            assertTrue(repo.update(loaded.copy(status = BookingStatus.CONFIRMED, version = loaded.version + 1)))
            assertTrue(!repo.update(loaded.copy(status = BookingStatus.CANCELLED, version = loaded.version + 1)), "stale version rejected")
            assertEquals(BookingStatus.CONFIRMED, repo.findByReference(booking.reference)?.status)
        }
    }

    @Test
    fun `Redis holds never oversell under concurrency`() = runBlocking {
        assumeTrue(redisUrl != null, "WAVE_TEST_REDIS_URL not set")
        val client = RedisClient.create(redisUrl)
        client.connect().use { conn ->
            val store = RedisHoldStore(conn, Fixtures.clock, cacheTtl = Duration.ZERO)
            val sailing = "TEST-${UUID.randomUUID()}"
            val limits = ResourceLimits(seats = 10, cabins = mapOf(AccommodationType.CABIN_INT_4 to 2), laneMeters = 50, kennels = 1)
            val need = ResourceNeedDto(seats = 0, cabins = mapOf(AccommodationType.CABIN_INT_4 to 1), laneMeters = 6, kennels = 0)
            val results = (1..20).map { i ->
                async { store.place("h$i-$sailing", listOf(HoldRequest(sailing, need, limits)), Duration.ofMinutes(5)) }
            }.awaitAll()
            assertEquals(2, results.count { it }, "only two 4-berth cabins exist")
            assertEquals(2, store.consumed(sailing).cabins[AccommodationType.CABIN_INT_4])
            val winner = (1..20).first { results[it - 1] }
            store.release("h$winner-$sailing")
            assertEquals(1, store.consumed(sailing).cabins[AccommodationType.CABIN_INT_4])
        }
        client.shutdown()
    }

    private fun booking(): Booking {
        val now = Instant.parse("2026-10-02T09:00:00Z")
        return Booking(
            id = UUID.randomUUID().toString(),
            reference = dz.wave.booking.BookingReferences.next(),
            status = BookingStatus.HELD,
            contact = ContactInfo("amina@example.com", "+213549705582"),
            selection = BookingSelection(LegSelection("BAL-ALG-BCN-20261010-1400"), passengers = PassengersInput(1)),
            travellers = listOf(
                Traveller("AMINA", "BENALI", Sex.F, LocalDate.of(1990, 4, 12), "DZ", TravelDocument(DocType.PASSPORT, "P1234567", LocalDate.of(2031, 1, 1), "DZ")),
            ),
            quote = Quote(
                legs = emptyList(), adjustments = emptyList(), total = Money(3_000_000, CurrencyCode.DZD), currency = CurrencyCode.DZD,
                rates = RateInfo(CurrencyCode.DZD, emptyMap(), now, "test"), promotions = emptyList(), notices = emptyList(), createdAt = now,
            ),
            createdAt = now,
            updatedAt = now,
        )
    }
}
