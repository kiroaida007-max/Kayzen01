package dz.wave.rules

import dz.wave.domain.AccommodationPref
import dz.wave.domain.AccommodationType
import dz.wave.domain.AgeBands
import dz.wave.domain.PenaltyTier
import dz.wave.domain.TariffCode
import dz.wave.domain.TariffDef
import dz.wave.domain.LocalizedText
import dz.wave.domain.VehicleCategory
import dz.wave.domain.VehicleInput
import dz.wave.domain.VehicleType
import dz.wave.pricing.CancellationPolicy
import dz.wave.pricing.DateRanges
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {

    @Test
    fun `age bands follow each operator`() {
        val algerieFerries = AgeBands(infantMaxAge = 2, childMaxAge = 12, youthMaxAge = 25)
        val balearia = AgeBands(infantMaxAge = 0, childMaxAge = 13)
        val corsica = AgeBands(infantMaxAge = 2, childMaxAge = 12, seniorMinAge = 60)
        assertEquals(FareCategory.INFANT, FareClassifier.classify(2, algerieFerries))
        assertEquals(FareCategory.CHILD, FareClassifier.classify(2, balearia))
        assertEquals(FareCategory.CHILD, FareClassifier.classify(13, balearia))
        assertEquals(FareCategory.ADULT, FareClassifier.classify(14, balearia))
        assertEquals(FareCategory.YOUTH, FareClassifier.classify(17, algerieFerries))
        assertEquals(FareCategory.SENIOR, FareClassifier.classify(65, corsica))
        assertEquals(FareCategory.ADULT, FareClassifier.classify(65, balearia))
    }

    @Test
    fun `vehicle category comes from type and real dimensions`() {
        assertEquals(VehicleCategory.CAR, VehicleClassifier.category(VehicleInput(VehicleType.CAR)))
        assertEquals(VehicleCategory.CAR_HIGH, VehicleClassifier.category(VehicleInput(VehicleType.CAR, heightM = 1.95)))
        assertEquals(VehicleCategory.CAR_HIGH, VehicleClassifier.category(VehicleInput(VehicleType.CAR, roofBox = true)), "roof box pushes a car above 1.90 m")
        assertEquals(VehicleCategory.CAR_HIGH, VehicleClassifier.category(VehicleInput(VehicleType.CAR, lengthM = 5.2)))
        assertEquals(VehicleCategory.CAR_TRAILER, VehicleClassifier.category(VehicleInput(VehicleType.CAR, withTrailer = true)))
        assertEquals(VehicleCategory.VAN, VehicleClassifier.category(VehicleInput(VehicleType.VAN)))
        assertEquals(10, VehicleClassifier.laneMeters(VehicleInput(VehicleType.CAR, lengthM = 4.5, withTrailer = true, trailerLengthM = 4.0)))
    }

    @Test
    fun `cabin allocator picks the cheapest whole cabins`() {
        val options = listOf(
            CabinAllocator.Option(AccommodationType.CABIN_INT_2, price = 100, available = 5),
            CabinAllocator.Option(AccommodationType.CABIN_INT_4, price = 150, available = 5),
        )
        // 3 people: one 4-berth (150) beats two 2-berth (200).
        val three = assertNotNull(CabinAllocator.allocate(3, maxCabins = 2, options = options))
        assertEquals(listOf(dz.wave.domain.CabinAllocation(AccommodationType.CABIN_INT_4, 1)), three.cabins)
        // 2 people: a 2-berth.
        val two = assertNotNull(CabinAllocator.allocate(2, maxCabins = 2, options = options))
        assertEquals(AccommodationType.CABIN_INT_2, two.cabins.single().type)
        // 6 people with only one adult cannot be split over two cabins.
        assertNull(CabinAllocator.allocate(6, maxCabins = 1, options = options))
        // Availability is respected.
        val scarce = listOf(CabinAllocator.Option(AccommodationType.CABIN_INT_4, 150, available = 1))
        assertNull(CabinAllocator.allocate(6, maxCabins = 3, options = scarce))
    }

    @Test
    fun `mandatory pet cabin is part of the allocation`() {
        val options = listOf(CabinAllocator.Option(AccommodationType.CABIN_EXT_2, price = 120, available = 3))
        val pet = CabinAllocator.Option(AccommodationType.CABIN_PET, price = 180, available = 1)
        val result = assertNotNull(CabinAllocator.allocate(5, maxCabins = 2, options = options, mandatory = listOf(pet)))
        assertEquals(setOf(AccommodationType.CABIN_PET, AccommodationType.CABIN_EXT_2), result.cabins.map { it.type }.toSet())
        assertEquals(300, result.totalPrice)
    }

    @Test
    fun `allowed cabin types per preference`() {
        assertTrue(CabinAllocator.allowedTypes(AccommodationPref.SEAT).isEmpty())
        assertEquals(listOf(AccommodationType.SUITE), CabinAllocator.allowedTypes(AccommodationPref.SUITE))
    }

    @Test
    fun `Algérie Ferries F0 cancellation tiers`() {
        val f0 = TariffDef(
            code = TariffCode.STANDARD, name = LocalizedText("F0", "F0", "F0"), multiplier = 1.0, refundable = true, modifiable = true,
            cancellation = listOf(PenaltyTier(720, 0.20), PenaltyTier(240, 0.30), PenaltyTier(48, 0.50), PenaltyTier(0, 1.0)),
        )
        val departure = ZonedDateTime.of(2026, 12, 20, 15, 0, 0, 0, ZoneId.of("Europe/Paris"))
        val booked = Instant.parse("2026-10-01T10:00:00Z")
        fun at(daysBefore: Long) = CancellationPolicy.feePercent(f0, departure, booked, departure.minusDays(daysBefore).toInstant())
        assertEquals(0.20, at(45))
        assertEquals(0.30, at(20))
        assertEquals(0.50, at(5))
        assertEquals(1.0, at(1))
    }

    @Test
    fun `Baleària free cancellation within 24 h of purchase`() {
        val reduced = TariffDef(
            code = TariffCode.STANDARD, name = LocalizedText("R", "R", "R"), multiplier = 1.0, refundable = true, modifiable = true,
            freeCancellationHoursAfterPurchase = 24,
            cancellation = listOf(PenaltyTier(48, 0.10), PenaltyTier(24, 0.20), PenaltyTier(0, 1.0)),
        )
        val departure = ZonedDateTime.of(2026, 11, 2, 23, 0, 0, 0, ZoneId.of("Europe/Madrid"))
        val booked = Instant.parse("2026-10-20T10:00:00Z")
        assertEquals(0.0, CancellationPolicy.feePercent(reduced, departure, booked, booked.plusSeconds(3600)))
        assertEquals(0.10, CancellationPolicy.feePercent(reduced, departure, booked, booked.plusSeconds(3 * 86400)))
        assertEquals(1.0, CancellationPolicy.feePercent(reduced, departure, booked, departure.toInstant().plusSeconds(60)))
    }

    @Test
    fun `date ranges wrap over new year and support one-off dates`() {
        assertTrue(DateRanges.contains("12-18", "01-04", LocalDate.of(2027, 1, 2)))
        assertTrue(DateRanges.contains("12-18", "01-04", LocalDate.of(2026, 12, 25)))
        assertTrue(!DateRanges.contains("12-18", "01-04", LocalDate.of(2026, 11, 25)))
        assertTrue(DateRanges.contains("2027-03-04", "2027-03-14", LocalDate.of(2027, 3, 10)))
        assertTrue(!DateRanges.contains("06-15", "09-15", LocalDate.of(2026, 9, 16), years = listOf(2026)))
    }

    @Test
    fun `phone numbers are normalised`() {
        assertEquals("+213549705582", dz.wave.booking.BookingService.normalisePhone("0549 705 582"))
        assertEquals("+33612345678", dz.wave.booking.BookingService.normalisePhone("+33 6 12 34 56 78"))
        assertEquals("+213776167407", dz.wave.booking.BookingService.normalisePhone("00213776167407"))
        assertNull(dz.wave.booking.BookingService.normalisePhone("12345"))
    }
}
