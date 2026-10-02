package dz.wave.integrations

import dz.wave.domain.Booking
import dz.wave.domain.BookingSelection
import dz.wave.domain.BookingStatus
import dz.wave.domain.ContactInfo
import dz.wave.domain.CurrencyCode
import dz.wave.domain.LegSelection
import dz.wave.domain.Money
import dz.wave.domain.PassengersInput
import dz.wave.domain.PaymentMethod
import dz.wave.domain.PaymentRecord
import dz.wave.domain.PaymentStatus
import dz.wave.domain.Quote
import dz.wave.domain.RateInfo
import dz.wave.live.AisStreamClient
import dz.wave.payment.PaymentContext
import dz.wave.payment.SatimConfig
import dz.wave.payment.SatimProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntegrationsTest {

    @Test
    fun `aisstream position reports are parsed`() {
        val client = AisStreamClient(HttpClient(MockEngine { respond("") }), "key", { listOf("605016420") }, {})
        val message = """
            {"Message":{"PositionReport":{"Cog":192.3,"Latitude":39.1234,"Longitude":4.5678,"NavigationalStatus":0,
             "Sog":21.4,"TrueHeading":191,"UserID":605016420,"Valid":true}},
             "MessageType":"PositionReport",
             "MetaData":{"MMSI":605016420,"ShipName":"BADJI MOKHTAR III","latitude":39.1234,"longitude":4.5678,
             "time_utc":"2026-10-02 08:15:30.123456789 +0000 UTC"}}
        """.trimIndent()
        val fix = assertNotNull(client.parse(message))
        assertEquals("605016420", fix.mmsi)
        assertEquals(39.1234, fix.lat)
        assertEquals(21.4, fix.speedKn)
        assertEquals(191.0, fix.heading)
        assertEquals(Instant.parse("2026-10-02T08:15:30.123456789Z"), fix.timestamp)
        assertEquals(null, client.parse("""{"MessageType":"ShipStaticData"}"""))
        val sub = client.subscription().toString()
        assertTrue(sub.contains("FiltersShipMMSI") && sub.contains("605016420") && sub.contains("PositionReport"))
    }

    @Test
    fun `SATIM register then confirm`() = runBlocking {
        val calls = mutableListOf<String>()
        val engine = MockEngine { request ->
            calls += request.url.encodedPath
            val form = (request.body as FormDataContent).formData
            val body = if (request.url.encodedPath.endsWith("register.do")) {
                assertEquals("012", form["currency"])
                assertEquals("1234500", form["amount"])
                assertTrue(form["jsonParams"]!!.contains("force_terminal_id"))
                """{"orderId":"ORD-1","formUrl":"https://test.satim.dz/payment/merchants/form?mdOrder=ORD-1","errorCode":"0"}"""
            } else {
                """{"ErrorCode":"0","OrderStatus":2,"actionCode":0,"Amount":1234500,"actionCodeDescription":"Votre paiement a été accepté"}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val provider = SatimProvider(SatimConfig("https://test.satim.dz/payment/rest", "user", "pass", "E010900000"), HttpClient(engine))
        val booking = sampleBooking()
        val amount = Money(1_234_500, CurrencyCode.DZD)
        val start = provider.start(PaymentContext(booking, PaymentMethod.CIB, amount, "pay-1", "https://x/ok", "https://x/ko", dz.wave.domain.Lang.FR))
        assertEquals("ORD-1", start.providerOrderId)
        assertTrue(start.redirectUrl!!.contains("mdOrder=ORD-1"))
        val record = PaymentRecord("pay-1", PaymentMethod.CIB, "satim", "ORD-1", PaymentStatus.PENDING, amount, Instant.EPOCH, Instant.EPOCH)
        val verification = provider.verify(record, emptyMap())
        assertEquals(PaymentStatus.APPROVED, verification.status)
        assertEquals(listOf("/payment/rest/register.do", "/payment/rest/confirmOrder.do"), calls)
    }

    @Test
    fun `SATIM amount mismatch is declined`() = runBlocking {
        val engine = MockEngine {
            respond("""{"ErrorCode":"0","OrderStatus":2,"actionCode":0,"Amount":100}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val provider = SatimProvider(SatimConfig("https://test", "u", "p", "t"), HttpClient(engine))
        val record = PaymentRecord("p", PaymentMethod.CIB, "satim", "ORD", PaymentStatus.PENDING, Money(500_000, CurrencyCode.DZD), Instant.EPOCH, Instant.EPOCH)
        assertEquals(PaymentStatus.DECLINED, provider.verify(record, emptyMap()).status)
    }

    private fun sampleBooking() = Booking(
        id = "00000000-0000-0000-0000-000000000001",
        reference = "WVTEST",
        status = BookingStatus.HELD,
        contact = ContactInfo("a@b.dz", "+213549705582"),
        selection = BookingSelection(LegSelection("X"), passengers = PassengersInput(1)),
        travellers = emptyList(),
        quote = Quote(
            legs = emptyList(), adjustments = emptyList(), total = Money(1_234_500, CurrencyCode.DZD), currency = CurrencyCode.DZD,
            rates = RateInfo(CurrencyCode.DZD, emptyMap(), Instant.EPOCH, "test"), promotions = emptyList(), notices = emptyList(),
            createdAt = Instant.EPOCH,
        ),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
