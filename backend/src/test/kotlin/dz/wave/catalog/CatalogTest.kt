package dz.wave.catalog

import dz.wave.Fixtures
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogTest {
    private val data = Fixtures.catalog
    private val generator = ScheduleGenerator(data)

    @Test
    fun `catalog files load and cross-reference`() {
        assertEquals(14, data.ports.size)
        assertTrue(data.operators.count { it.active } >= 6)
        assertEquals(44, data.routes.size)
        val tables = FareTables.resolve(data.fareTables)
        assertTrue(data.routes.all { it.id in tables }, "every route has a fare table")
    }

    @Test
    fun `fare inheritance applies scale and overrides`() {
        val tables = FareTables.resolve(data.fareTables)
        val base = tables.getValue("AF-MRS-ALG")
        val oran = tables.getValue("AF-MRS-ORN")
        assertEquals(base.adultSeat * 1.06, oran.adultSeat, 0.0001)
        assertEquals(base.vehicles.getValue(dz.wave.domain.VehicleCategory.CAR) * 1.06, oran.vehicles.getValue(dz.wave.domain.VehicleCategory.CAR), 0.0001)
        assertEquals(75.0, tables.getValue("GNV-SET-BJA").adultSeat)
    }

    @Test
    fun `summer Marseille departure lands in Algiers local time`() {
        val sailings = generator.generate(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30))
        // Baleària publishes until end of June 2027; Valencia (CEST, UTC+2) -> Mostaganem (UTC+1).
        val s = sailings.first { it.routeId == "BAL-VLC-MOS" && !it.departureDate.isBefore(LocalDate.of(2027, 6, 14)) }
        assertEquals(ZoneId.of("Europe/Madrid"), s.departure.zone)
        assertEquals(ZoneId.of("Africa/Algiers"), s.arrival.zone)
        assertEquals(900, Duration.between(s.departure, s.arrival).toMinutes())
        // 23:00 CEST + 15 h = 14:00 CEST next day = 13:00 in Algiers.
        assertEquals(LocalDateTime.of(s.departureDate.plusDays(1), java.time.LocalTime.of(13, 0)), s.arrival.toLocalDateTime())
    }

    @Test
    fun `winter Algérie Ferries schedule has two weekly Marseille - Alger crossings`() {
        val sailings = generator.generate(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 1))
        val afMrsAlg = sailings.filter { it.routeId == "AF-MRS-ALG" }
        assertEquals(8, afMrsAlg.size, "Wed (Badji Mokhtar III) + Sat (Tassili II) for 4 weeks")
        assertTrue(afMrsAlg.all { it.departure.toLocalTime().hour == 15 })
    }

    @Test
    fun `GNV Civitavecchia - Annaba starts on 8 August 2026`() {
        val sailings = generator.generate(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 20))
        val first = sailings.filter { it.routeId == "GNV-CVV-AAE" }.minOf { it.departureDate }
        assertEquals(LocalDate.of(2026, 8, 8), first)
    }

    @Test
    fun `operators never publish beyond their horizon`() {
        val sailings = generator.generate(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30))
        val ops = data.operators.associateBy { it.code }
        sailings.forEach { s ->
            val until = ops.getValue(s.operator).publishedUntil
            if (until != null) assertTrue(!s.departureDate.isAfter(until), "${s.id} after $until")
        }
    }

    @Test
    fun `no ship is ever in two places at once`() {
        val sailings = generator.generate(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30))
        val minTurnaround = Duration.ofHours(2)
        sailings.filter { it.vessel != null }.groupBy { it.vessel }.forEach { (vessel, list) ->
            val sorted = list.sortedBy { it.departure.toInstant() }
            sorted.zipWithNext().forEach { (a, b) ->
                val gap = Duration.between(a.arrival.toInstant(), b.departure.toInstant())
                assertTrue(gap >= minTurnaround, "$vessel: ${a.id} arrives after ${b.id} leaves (gap $gap)")
                assertEquals(a.to, b.from, "$vessel teleports: ${a.id} ends in ${a.to} but ${b.id} starts in ${b.from}")
            }
        }
    }

    @Test
    fun `week helpers`() {
        assertEquals(1, ScheduleGenerator.weekOfMonth(LocalDate.of(2026, 10, 5)))
        assertEquals(4, ScheduleGenerator.weekOfMonth(LocalDate.of(2026, 10, 26)))
        val a = ScheduleGenerator.weekParity(LocalDate.of(2026, 12, 28))
        val b = ScheduleGenerator.weekParity(LocalDate.of(2027, 1, 4))
        assertTrue(a != b, "parity alternates across the year boundary")
        assertTrue(ScheduleGenerator.isSummer(LocalDate.of(2026, 9, 15)))
        assertTrue(!ScheduleGenerator.isSummer(LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `live overlay replaces the matching reference crossing`() {
        val store = CatalogStore(data, Fixtures.clock)
        val snap = store.current()
        val ref = snap.sailingsById.values.first { it.routeId == "BAL-VLC-MOS" }
        val live = ref.copy(
            id = ScheduleGenerator.sailingId(ref.routeId, ref.departure.plusMinutes(30)),
            departure = ref.departure.plusMinutes(30),
            arrival = ref.arrival.plusMinutes(30),
            source = dz.wave.domain.PriceSource.LIVE,
        )
        store.upsertLive(listOf(live))
        val after = store.current()
        assertTrue(ref.id !in after.sailingsById)
        assertTrue(live.id in after.sailingsById)
        assertEquals(snap.sailingCount, after.sailingCount)
    }
}
