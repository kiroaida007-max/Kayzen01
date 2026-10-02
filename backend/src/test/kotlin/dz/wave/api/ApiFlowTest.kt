package dz.wave.api

import dz.wave.Fixtures
import dz.wave.common.ApiError
import dz.wave.common.AppJson
import dz.wave.domain.BookingSelection
import dz.wave.domain.BookingStatus
import dz.wave.domain.ContactInfo
import dz.wave.domain.CreateBookingRequest
import dz.wave.domain.CurrencyCode
import dz.wave.domain.DocType
import dz.wave.domain.LegSelection
import dz.wave.domain.PassengersInput
import dz.wave.domain.PaymentMethod
import dz.wave.domain.Quote
import dz.wave.domain.SearchRequest
import dz.wave.domain.SearchResponse
import dz.wave.domain.Sex
import dz.wave.domain.TariffCode
import dz.wave.domain.TravelDocument
import dz.wave.domain.Traveller
import dz.wave.ingest.IngestResult
import dz.wave.payment.PaymentInitiation
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiFlowTest {

    private fun ApplicationTestBuilder.setup(): Pair<dz.wave.AppContainer, HttpClient> {
        val container = Fixtures.container()
        application { waveModule(container) }
        val client = createClient { install(ContentNegotiation) { json(AppJson) } }
        return container to client
    }

    private fun traveller(first: String, last: String, dob: LocalDate) = Traveller(
        firstName = first,
        lastName = last,
        sex = Sex.F,
        dateOfBirth = dob,
        nationality = "DZ",
        document = TravelDocument(DocType.PASSPORT, "AB${(100000..999999).random()}", LocalDate.of(2031, 5, 1), "DZ"),
    )

    @Test
    fun `search, quote, book, pay and get an e-ticket`() = testApplication {
        val (container, client) = setup()

        val meta = client.get("/api/v1/meta")
        assertEquals(HttpStatusCode.OK, meta.status)
        assertNotNull(meta.headers[HttpHeaders.ETag])
        assertEquals("nosniff", meta.headers["X-Content-Type-Options"])

        val search = client.post("/api/v1/search") {
            contentType(ContentType.Application.Json)
            setBody(SearchRequest(from = "DZALG", to = "ESBCN", departureDate = LocalDate.of(2026, 10, 10), passengers = PassengersInput(adults = 1, childrenAges = listOf(6))))
        }.body<SearchResponse>()
        val offer = search.outbound.offers.single { it.bookable }

        val selection = BookingSelection(
            outbound = LegSelection(offer.sailingId, tariff = TariffCode.STANDARD),
            passengers = PassengersInput(adults = 1, childrenAges = listOf(6)),
            currency = CurrencyCode.DZD,
        )
        val quote = client.post("/api/v1/quote") {
            contentType(ContentType.Application.Json)
            setBody(selection)
        }.body<Quote>()
        assertEquals(CurrencyCode.DZD, quote.total.currency)
        assertNotNull(quote.payable[CurrencyCode.EUR])

        val booking = CreateBookingRequest(
            selection = selection,
            travellers = listOf(
                traveller("Amina", "Benali", LocalDate.of(1990, 4, 12)),
                traveller("Yacine", "Benali", LocalDate.of(2020, 3, 1)),
            ),
            contact = ContactInfo("amina@example.com", "0549 705 582"),
            acceptTerms = true,
            expectedTotal = quote.total,
        )
        val key = UUID.randomUUID().toString()
        val created = client.post("/api/v1/bookings") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", key)
            setBody(booking)
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val view = created.body<BookingView>()
        assertEquals(BookingStatus.HELD, view.status)
        assertEquals("+213549705582", view.contact.phone)
        assertTrue(view.travellers.all { it.documentNumber.startsWith("••••") }, "document numbers are masked")

        // A retry with the same key returns the same booking instead of creating a second one.
        val retry = client.post("/api/v1/bookings") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", key)
            setBody(booking)
        }
        assertEquals(HttpStatusCode.OK, retry.status)
        assertEquals(view.reference, retry.body<BookingView>().reference)

        val fetched = client.get("/api/v1/bookings/${view.reference}?lastName=benali")
        assertEquals(HttpStatusCode.OK, fetched.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/bookings/${view.reference}?lastName=Martin").status)

        val payment = client.post("/api/v1/bookings/${view.reference}/payments") {
            contentType(ContentType.Application.Json)
            setBody(PaymentRequest(PaymentMethod.CIB, "Benali"))
        }.body<PaymentInitiation>()
        assertEquals(CurrencyCode.DZD, payment.amount.currency)
        val redirect = assertNotNull(payment.redirectUrl)

        val confirmed = client.get(redirect.removePrefix("http://localhost")) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }.body<BookingView>()
        assertEquals(BookingStatus.CONFIRMED, confirmed.status)
        val ticket = assertNotNull(confirmed.ticket)
        assertEquals(view.reference, container.ticketSigner.verify(ticket.qrPayload))
        assertEquals(null, container.ticketSigner.verify(ticket.qrPayload.dropLast(2) + "xx"))

        val preview = client.post("/api/v1/bookings/${view.reference}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelRequest("Benali", dryRun = true))
        }.body<dz.wave.domain.CancellationResult>()
        // Baleària's reduced fare is fully refundable within 24 h of purchase.
        assertEquals(0L, preview.fee.minor)
        assertEquals(confirmed.total, preview.refund)
        container.close()
    }

    @Test
    fun `booking validation explains what is wrong`() = testApplication {
        val (container, client) = setup()
        val sailing = container.catalog.current().sailingsById.values.first { it.routeId == "BAL-ALG-BCN" && it.departureDate == LocalDate.of(2026, 10, 10) }
        val response = client.post("/api/v1/bookings") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateBookingRequest(
                    selection = BookingSelection(LegSelection(sailing.id), passengers = PassengersInput(1)),
                    travellers = listOf(traveller("Amina", "Benali", LocalDate.of(1990, 4, 12)).let { it.copy(document = it.document.copy(type = DocType.ID_CARD)) }),
                    contact = ContactInfo("not-an-email", "123"),
                    acceptTerms = false,
                ),
            )
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val codes = response.body<ApiError>().violations.map { it.code }.toSet()
        assertTrue(codes.containsAll(setOf("TERMS_NOT_ACCEPTED", "DOC_ID_CARD_NOT_ACCEPTED", "EMAIL_INVALID", "PHONE_INVALID")), "$codes")
        container.close()
    }

    @Test
    fun `retrieval endpoint is rate limited`() = testApplication {
        val (container, client) = setup()
        val statuses = (1..35).map { client.get("/api/v1/bookings/ABCDEF?lastName=X").status }
        assertTrue(HttpStatusCode.TooManyRequests in statuses)
        container.close()
    }

    @Test
    fun `ingest requires a valid signature and overlays live sailings`() = testApplication {
        val (container, client) = setup()
        val body = """
            {"source":"test","fetchedAt":"2026-10-02T08:00:00Z","sailings":[
              {"operator":"BAL","from":"ESVLC","to":"DZMOS","departureLocal":"2026-10-12T23:00","durationMin":900,
               "vessel":"Visborg","prices":[{"category":"ADULT_SEAT","amount":119.0,"currency":"EUR"}]},
              {"operator":"BAL","from":"ESVLC","to":"FRMRS","departureLocal":"2026-10-12T23:00","durationMin":900}
            ]}
        """.trimIndent()
        val ts = Fixtures.NOW.epochSecond.toString()
        fun sign(secret: String): String {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
            return mac.doFinal("$ts.$body".toByteArray()).joinToString("") { "%02x".format(it) }
        }

        val bad = client.post("/internal/v1/ingest/sailings") {
            header("X-Wave-Key", "scraper")
            header("X-Wave-Timestamp", ts)
            header("X-Wave-Signature", sign("wrong-secret"))
            setBody(body)
        }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)

        val ok = client.post("/internal/v1/ingest/sailings") {
            header("X-Wave-Key", "scraper")
            header("X-Wave-Timestamp", ts)
            header("X-Wave-Signature", sign(Fixtures.INGEST_SECRET))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, ok.status, ok.bodyAsText())
        val result = ok.body<IngestResult>()
        assertEquals(1, result.accepted)
        assertEquals(1, result.rejected.size, "the route Valencia -> Marseille does not exist for Baleària")

        val live = container.catalog.current().sailingsById.getValue("BAL-VLC-MOS-20261012-2300")
        assertEquals(dz.wave.domain.PriceSource.LIVE, live.source)
        assertEquals("VIS", live.vessel)
        container.close()
    }

    @Test
    fun `live map snapshot lists ships with positions`() = testApplication {
        val (container, client) = setup()
        val text = client.get("/api/v1/live/vessels").bodyAsText()
        val positions = AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(dz.wave.live.VesselPosition.serializer()), text)
        assertTrue(positions.size >= 8)
        assertTrue(positions.all { it.lat in 34.0..44.0 && it.lon in -3.0..12.5 })
        container.close()
    }

    @Test
    fun `auth register, login, refresh rotation and reuse detection`() = testApplication {
        val (container, client) = setup()
        val register = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(dz.wave.auth.RegisterRequest("amina@example.com", "Mediterranee2026", "Amina Benali"))
        }
        assertEquals(HttpStatusCode.Created, register.status, register.bodyAsText())
        val tokens = register.body<dz.wave.auth.TokenPair>()

        val me = client.get("/api/v1/me") { header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}") }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me").status)

        val wrong = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(dz.wave.auth.LoginRequest("amina@example.com", "nope-nope-123"))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)

        val refreshed = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(dz.wave.auth.RefreshRequest(tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        // Reusing the rotated token is treated as theft.
        val reuse = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(dz.wave.auth.RefreshRequest(tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)
        val afterTheft = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(dz.wave.auth.RefreshRequest(refreshed.body<dz.wave.auth.TokenPair>().refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, afterTheft.status, "all sessions revoked after reuse")
        container.close()
    }
}
