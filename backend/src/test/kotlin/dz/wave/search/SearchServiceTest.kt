package dz.wave.search

import dz.wave.Fixtures
import dz.wave.common.ValidationException
import dz.wave.domain.AccessibilityInput
import dz.wave.domain.AccommodationPref
import dz.wave.domain.CurrencyCode
import dz.wave.domain.Lang
import dz.wave.domain.PassengersInput
import dz.wave.domain.PetInput
import dz.wave.domain.PetPlacement
import dz.wave.domain.PetType
import dz.wave.domain.PriceLineKind
import dz.wave.domain.SearchRequest
import dz.wave.domain.TripType
import dz.wave.domain.VehicleInput
import dz.wave.domain.VehicleType
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchServiceTest {
    private val container = Fixtures.container()
    private val search = container.search

    @AfterTest
    fun close() = container.close()

    private fun request(
        from: String = "DZALG",
        to: String = "ESBCN",
        date: LocalDate = LocalDate.of(2026, 10, 10),
        passengers: PassengersInput = PassengersInput(adults = 2, childrenAges = listOf(8)),
        currency: CurrencyCode = CurrencyCode.DZD,
        accommodation: AccommodationPref = AccommodationPref.SEAT,
        vehicle: VehicleInput? = null,
        pets: List<PetInput> = emptyList(),
        flexDays: Int = 0,
        lang: Lang = Lang.FR,
    ) = SearchRequest(
        from = from, to = to, departureDate = date, passengers = passengers, currency = currency,
        accommodation = accommodation, vehicle = vehicle, pets = pets, flexDays = flexDays, lang = lang,
    )

    @Test
    fun `Alger to Barcelona on Saturday returns a priced Baleària crossing in dinars`() {
        val response = search.search(request())
        val offer = response.outbound.offers.single()
        assertEquals("BAL", offer.operator.code)
        assertTrue(offer.bookable, offer.reasons.joinToString { it.message })
        val cheapest = assertNotNull(offer.cheapest)
        assertEquals(CurrencyCode.DZD, cheapest.total.currency)
        assertEquals(0L, cheapest.total.minor % 100, "dinar prices are whole dinars")
        assertEquals(CurrencyCode.EUR, cheapest.totalNative.currency)
        // Two adults + a child at Baleària's 50 % child fare.
        val passages = offer.breakdown.filter { it.kind == PriceLineKind.PASSAGE }
        val adult = passages.first { it.label.contains("Adulte") }
        val child = passages.first { it.label.contains("Enfant") }
        assertEquals(2, adult.quantity)
        assertTrue(kotlin.math.abs(adult.unit.minor / 2 - child.unit.minor) <= 100, "child pays half")
        assertEquals(cheapest.total.minor, offer.breakdown.sumOf { it.total.minor })
        assertTrue(response.notices.any { it.code == "MINOR_AUTHORIZATION" })
    }

    @Test
    fun `switching currency converts at the official rate`() {
        val dzd = search.search(request()).outbound.offers.single().cheapest!!.total
        val eur = search.search(request(currency = CurrencyCode.EUR)).outbound.offers.single().cheapest!!.total
        val usd = search.search(request(currency = CurrencyCode.USD)).outbound.offers.single().cheapest!!.total
        assertEquals(CurrencyCode.EUR, eur.currency)
        assertEquals(CurrencyCode.USD, usd.currency)
        val ratio = dzd.toDecimal().toDouble() / eur.toDecimal().toDouble()
        assertEquals(151.06, ratio, 0.5)
        assertTrue(usd.minor > eur.minor, "1 EUR buys more than 1 USD")
    }

    @Test
    fun `Alger to Marseille offers several companies`() {
        val response = search.search(request(to = "FRMRS", date = LocalDate.of(2026, 10, 7), flexDays = 1))
        val operators = response.outbound.offers.map { it.operator.code }.toSet()
        assertTrue(operators.containsAll(setOf("CL", "NE")), "got $operators")
        assertEquals(7, response.outbound.nearbyDays.size)
    }

    @Test
    fun `round trip searches both directions`() {
        val response = search.search(
            request(to = "FRMRS", date = LocalDate.of(2026, 10, 7)).copy(tripType = TripType.ROUND_TRIP, returnDate = LocalDate.of(2026, 10, 21)),
        )
        val inbound = assertNotNull(response.inbound)
        assertTrue(inbound.offers.all { it.from.code == "FRMRS" && it.to.code == "DZALG" })
    }

    @Test
    fun `cabin search allocates cabins for the family`() {
        val response = search.search(request(accommodation = AccommodationPref.CABIN_ANY, passengers = PassengersInput(adults = 2, childrenAges = listOf(8, 5))))
        val offer = response.outbound.offers.single()
        assertTrue(offer.bookable)
        assertEquals(4, offer.cabins.sumOf { it.type.berths * it.count }, "a 4-berth cabin is cheapest for four")
        assertTrue(offer.breakdown.any { it.kind == PriceLineKind.ACCOMMODATION })
    }

    @Test
    fun `vehicle and pets are priced`() {
        val response = search.search(
            request(
                vehicle = VehicleInput(VehicleType.CAR),
                pets = listOf(PetInput(PetType.DOG, 1, PetPlacement.KENNEL)),
            ),
        )
        val offer = response.outbound.offers.single()
        assertTrue(offer.bookable, offer.reasons.joinToString { it.code })
        assertTrue(offer.breakdown.any { it.kind == PriceLineKind.VEHICLE })
        assertTrue(offer.breakdown.any { it.kind == PriceLineKind.PET })
        assertTrue(response.notices.any { it.code == "VEHICLE_DOCS" } && response.notices.any { it.code == "PET_DOCS" })
    }

    @Test
    fun `vans are refused during the summer restriction`() {
        // Baleària publishes Valencia–Mostaganem until 30 June 2027, inside the 15 June–15 September window.
        val response = search.search(
            request(from = "ESVLC", to = "DZMOS", date = LocalDate.of(2027, 6, 21), vehicle = VehicleInput(VehicleType.VAN), passengers = PassengersInput(1)),
        )
        val offer = response.outbound.offers.single()
        assertTrue(!offer.bookable)
        assertTrue(offer.reasons.any { it.code == "REGULATION_DZ_SUMMER_VANS" })
        // Same van in October is fine.
        val october = search.search(
            request(from = "ESVLC", to = "DZMOS", date = LocalDate.of(2026, 10, 12), vehicle = VehicleInput(VehicleType.VAN), passengers = PassengersInput(1)),
        )
        assertTrue(october.outbound.offers.single().bookable)
    }

    @Test
    fun `wheelchair users in a cabin need an accessible cabin`() {
        val response = search.search(
            request(accommodation = AccommodationPref.CABIN_ANY).copy(accessibility = AccessibilityInput(wheelchair = true)),
        )
        val offer = response.outbound.offers.single()
        assertTrue(offer.bookable)
        assertTrue(offer.cabins.any { it.type == dz.wave.domain.AccommodationType.CABIN_PMR })
        assertTrue(response.notices.any { it.code == "PMR_NOTICE" })
    }

    @Test
    fun `invalid requests are rejected with explanations`() {
        fun codes(req: SearchRequest) = assertFailsWith<ValidationException> { search.search(req) }.violations.map { it.code }
        assertTrue("PAX_ADULT_REQUIRED" in codes(request(passengers = PassengersInput(adults = 0, childrenAges = listOf(10)))))
        assertTrue("INFANTS_EXCEED_ADULTS" in codes(request(passengers = PassengersInput(adults = 1, childrenAges = listOf(0, 1)))))
        assertTrue("ROUTE_SAME_PORT" in codes(request(to = "DZALG")))
        assertTrue("NO_ROUTE" in codes(request(from = "DZGHZ", to = "FRMRS")))
        assertTrue("DATE_IN_PAST" in codes(request(date = LocalDate.of(2026, 9, 1))))
        assertTrue("PAX_MAX" in codes(request(passengers = PassengersInput(adults = 10))))
        assertTrue("VEHICLE_TOO_HIGH" in codes(request(vehicle = VehicleInput(VehicleType.CAMPER, heightM = 4.8))))
        assertTrue("RETURN_REQUIRED" in codes(request().copy(tripType = TripType.ROUND_TRIP)))
    }

    @Test
    fun `messages are localised`() {
        val ar = assertFailsWith<ValidationException> { search.search(request(to = "DZALG", lang = Lang.AR)) }
        assertTrue(ar.violations.first().message.contains("ميناء"))
    }

    @Test
    fun `no sailing on the requested day points to the next departure`() {
        val response = search.search(request(date = LocalDate.of(2026, 10, 6)))
        assertTrue(response.outbound.offers.isEmpty())
        assertTrue(response.notices.any { it.code == "NO_SAILING_ON_DATE" })
    }

    @Test
    fun `price calendar covers the whole month`() {
        val days = search.calendar(request(), YearMonth.of(2026, 11))
        assertEquals(30, days.size)
        assertTrue(days.filter { it.sailings > 0 }.all { it.minPrice != null })
        assertEquals(4, days.count { it.sailings > 0 }, "one Baleària departure every Saturday")
    }
}
