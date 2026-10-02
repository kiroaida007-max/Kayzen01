package dz.wave.search

import dz.wave.catalog.CatalogSnapshot
import dz.wave.domain.AccessibilityInput
import dz.wave.domain.AccommodationPref
import dz.wave.domain.AccommodationType
import dz.wave.domain.Amenity
import dz.wave.domain.CabinAllocation
import dz.wave.domain.CurrencyCode
import dz.wave.domain.FareTable
import dz.wave.domain.Lang
import dz.wave.domain.Money
import dz.wave.domain.Operator
import dz.wave.domain.PassengersInput
import dz.wave.domain.PetInput
import dz.wave.domain.PetPlacement
import dz.wave.domain.Port
import dz.wave.domain.PriceLine
import dz.wave.domain.PriceLineKind
import dz.wave.domain.Promotion
import dz.wave.domain.Route
import dz.wave.domain.RuleSeverity
import dz.wave.domain.Sailing
import dz.wave.domain.SailingStatus
import dz.wave.domain.TariffDef
import dz.wave.domain.VehicleCategory
import dz.wave.domain.VehicleInput
import dz.wave.domain.Vessel
import dz.wave.domain.Violation
import dz.wave.pricing.AvailabilityView
import dz.wave.pricing.Consumption
import dz.wave.pricing.InventoryModel
import dz.wave.pricing.Labels
import dz.wave.pricing.LegPriceInput
import dz.wave.pricing.PricingEngine
import dz.wave.pricing.PromotionContext
import dz.wave.pricing.PromotionEngine
import dz.wave.pricing.SeasonCalendar
import dz.wave.pricing.TariffAvailability
import dz.wave.pricing.directionOf
import dz.wave.rules.CabinAllocator
import dz.wave.rules.FareCategory
import dz.wave.rules.FareClassifier
import dz.wave.rules.Messages
import dz.wave.rules.RegulationChecker
import dz.wave.rules.VehicleClassifier
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/** The traveller's group and options, as needed to evaluate any single crossing. */
data class LegRequest(
    val passengers: PassengersInput,
    val vehicle: VehicleInput?,
    val accommodation: AccommodationPref,
    val pets: List<PetInput>,
    val accessibility: AccessibilityInput,
    val lang: Lang,
    val roundTrip: Boolean,
)

/** Inventory WAVE needs to hold on a sailing to sell this group a place. */
data class ResourceNeed(
    val seats: Int,
    val cabins: Map<AccommodationType, Int>,
    val laneMeters: Int,
    val kennels: Int,
)

/** Result of checking one sailing against the rules, before pricing a specific tariff. */
data class LegEvaluation(
    val sailing: Sailing,
    val route: Route,
    val operator: Operator,
    val vessel: Vessel?,
    val profile: Vessel,
    val fromPort: Port,
    val toPort: Port,
    val fareTable: FareTable,
    val reasons: List<Violation>,
    val notes: List<Violation>,
    val availability: AvailabilityView,
    val limits: AvailabilityView,
    val categories: List<FareCategory>,
    val vehicleCategory: VehicleCategory?,
    val cabins: List<CabinAllocation>,
    val petsInCabin: Int,
    val petsInKennel: Int,
    val seasonMultiplier: Double,
    val yieldMultiplier: Double,
    val need: ResourceNeed,
    val request: LegRequest,
) {
    val bookable: Boolean get() = reasons.isEmpty()
}

data class PricedTariff(
    val tariff: TariffDef,
    val nativeLines: List<PriceLine>,
    val nativeTotal: Money,
    val promotion: Promotion?,
)

fun interface ConsumptionReader {
    fun consumed(sailingId: String): Consumption
}

class LegEvaluator(
    private val pricing: PricingEngine,
    private val seasons: SeasonCalendar,
    private val inventory: InventoryModel,
    private val consumption: ConsumptionReader,
    private val promotions: PromotionEngine,
    private val clock: Clock,
) {

    fun evaluate(snap: CatalogSnapshot, sailing: Sailing, req: LegRequest): LegEvaluation {
        val lang = req.lang
        val route = snap.routes.getValue(sailing.routeId)
        val operator = snap.operators.getValue(sailing.operator)
        val vessel = sailing.vessel?.let { snap.vessels[it] }
        // Unannounced ships are evaluated with the operator's first ship as a capacity profile.
        val profile = vessel ?: snap.data.vessels.first { it.operator == operator.code }
        val fromPort = snap.ports.getValue(sailing.from)
        val toPort = snap.ports.getValue(sailing.to)
        val table = snap.fareTables.getValue(route.id)
        val direction = directionOf(fromPort, toPort)
        val seasonMultiplier = seasons.multiplier(sailing.departureDate, direction)
        val consumed = consumption.consumed(sailing.id)
        val availability = inventory.view(sailing, profile, seasonMultiplier, direction, consumed)
        val limits = inventory.limits(sailing, profile, seasonMultiplier, direction)
        val yieldMultiplier = if (sailing.livePrices != null) 1.0 else inventory.yieldMultiplier(availability.loadFactor)

        val reasons = mutableListOf<Violation>()
        val notes = mutableListOf<Violation>()
        fun reason(code: String, params: Map<String, String> = emptyMap()) {
            reasons += Messages.violation(code, RuleSeverity.ERROR, lang, params = params)
        }
        fun note(code: String, severity: RuleSeverity = RuleSeverity.INFO, params: Map<String, String> = emptyMap()) {
            notes += Messages.violation(code, severity, lang, params = params)
        }

        if (vessel == null) note("VESSEL_TBA")

        // 1. Sailing status and sales cut-off.
        val now = clock.instant()
        if (sailing.status == SailingStatus.CANCELLED) reason("SAILING_CANCELLED")
        val hoursLeft = Duration.between(now, sailing.departure.toInstant()).toMinutes() / 60.0
        if (sailing.status != SailingStatus.CANCELLED && hoursLeft < operator.rules.bookingCutoffHours) {
            reason("SAILING_CLOSED", mapOf("hours" to operator.rules.bookingCutoffHours.toString()))
        }

        // 2. Passengers classified with this operator's age bands.
        val categories = req.passengers.ages().map { FareClassifier.classify(it, operator.rules.ageBands) }
        val berthPassengers = categories.count { FareClassifier.occupiesBerth(it) }

        // 3. Vehicle: ship limits, fare availability and authorities' regulations.
        val vehicleCategory = req.vehicle?.let { VehicleClassifier.category(it) }
        var laneMeters = 0
        req.vehicle?.let { v ->
            laneMeters = VehicleClassifier.laneMeters(v)
            val height = VehicleClassifier.effectiveHeight(v)
            val length = VehicleClassifier.totalLength(v)
            if (height > profile.maxVehicleHeightM) {
                reason("VEHICLE_TOO_HIGH_VESSEL", mapOf("max" to profile.maxVehicleHeightM.toString()))
            }
            if (length > profile.maxVehicleLengthM) {
                reason("VEHICLE_TOO_LONG_VESSEL", mapOf("max" to profile.maxVehicleLengthM.toString()))
            }
            if (vehicleCategory !in table.vehicles.keys && sailing.livePrices?.vehicles?.containsKey(vehicleCategory) != true) {
                reason("VEHICLE_NOT_ACCEPTED")
            }
            if (availability.laneMeters < laneMeters) reason("VEHICLE_SPACE_FULL")
            RegulationChecker.check(snap.data.regulations, sailing, fromPort, toPort, v, lang).forEach {
                if (it.severity == RuleSeverity.ERROR) reasons += it else notes += it
            }
        }

        // 4. Animals: pet cabin with a cabin booking when possible, otherwise the kennel.
        val petCount = req.pets.sumOf { it.count }
        val petCabinsOnBoard = profile.accommodations[AccommodationType.CABIN_PET] ?: 0
        var petsInCabin = 0
        var petsInKennel = 0
        val mandatory = mutableListOf<AccommodationType>()
        if (petCount > 0) {
            val wantsCabin = req.pets.any { it.placement == PetPlacement.CABIN }
            val canUseCabin = req.accommodation != AccommodationPref.SEAT && petCabinsOnBoard > 0 &&
                AccommodationType.CABIN_PET in table.accommodation
            when {
                petCount > operator.rules.maxPetsPerBooking ->
                    reason("PETS_MAX", mapOf("max" to operator.rules.maxPetsPerBooking.toString()))
                profile.petKennels == 0 && petCabinsOnBoard == 0 -> reason("PETS_NOT_ACCEPTED")
                canUseCabin && (wantsCabin || req.pets.all { it.placement == PetPlacement.ANY }) &&
                    (availability.cabins[AccommodationType.CABIN_PET] ?: 0) > 0 -> {
                    petsInCabin = petCount
                    mandatory += AccommodationType.CABIN_PET
                }
                wantsCabin -> reason("PET_CABIN_UNAVAILABLE")
                availability.kennels < petCount -> reason("PET_KENNEL_UNAVAILABLE")
                else -> {
                    petsInKennel = petCount
                    if (req.accommodation == AccommodationPref.SEAT) note("PETS_IN_KENNEL")
                }
            }
        }

        // 5. Reduced mobility: an adapted cabin is mandatory when sleeping in a cabin.
        if (req.accessibility.wheelchair && req.accommodation != AccommodationPref.SEAT) {
            if ((availability.cabins[AccommodationType.CABIN_PMR] ?: 0) > 0 &&
                AccommodationType.CABIN_PMR in table.accommodation
            ) {
                mandatory += AccommodationType.CABIN_PMR
            } else {
                reason("PMR_CABIN_UNAVAILABLE")
            }
        }
        if (req.accessibility.wheelchair && Amenity.PMR_ACCESS !in profile.amenities) {
            note("PMR_NOTICE", RuleSeverity.WARNING)
        }

        // 6. Accommodation: seats, or the cheapest valid set of whole cabins.
        val baseInput = LegPriceInput(
            sailing = sailing, operator = operator, fareTable = table, categories = categories,
            pmrPassengers = 0, vehicleCategory = vehicleCategory, petsInCabin = petsInCabin, petsInKennel = petsInKennel,
            cabins = emptyList(), tariff = operator.rules.tariffs.first(), seasonMultiplier = seasonMultiplier,
            yieldMultiplier = yieldMultiplier, lang = lang,
        )
        var cabins = emptyList<CabinAllocation>()
        var seatsNeeded = 0
        if (req.accommodation == AccommodationPref.SEAT) {
            seatsNeeded = berthPassengers
            if (availability.seats < seatsNeeded) reason("SEATS_FULL")
        } else if (reasons.none { it.code == "PMR_CABIN_UNAVAILABLE" || it.code == "PET_CABIN_UNAVAILABLE" }) {
            val options = CabinAllocator.allowedTypes(req.accommodation).mapNotNull { type ->
                val price = pricing.cabinUnitPrice(baseInput, type) ?: return@mapNotNull null
                val left = availability.cabins[type] ?: 0
                if ((profile.accommodations[type] ?: 0) == 0) null else CabinAllocator.Option(type, price.minor, left)
            }
            val mandatoryOptions = mandatory.map { type ->
                CabinAllocator.Option(type, pricing.cabinUnitPrice(baseInput, type)?.minor ?: 0, availability.cabins[type] ?: 0)
            }
            if (options.isEmpty() && mandatoryOptions.isEmpty()) {
                reason("ACCOMMODATION_NOT_OFFERED")
            } else {
                val result = CabinAllocator.allocate(berthPassengers, req.passengers.grownUps, options, mandatoryOptions)
                if (result != null) {
                    cabins = result.cabins
                } else {
                    val withoutAdultLimit = CabinAllocator.allocate(berthPassengers, berthPassengers, options, mandatoryOptions)
                    reason(if (withoutAdultLimit != null) "CABINS_NEED_ADULTS" else "CABINS_FULL")
                }
            }
        }

        val need = ResourceNeed(
            seats = seatsNeeded,
            cabins = cabins.associate { it.type to it.count },
            laneMeters = laneMeters,
            kennels = petsInKennel,
        )
        return LegEvaluation(
            sailing, route, operator, vessel, profile, fromPort, toPort, table, reasons, notes, availability, limits,
            categories, vehicleCategory, cabins, petsInCabin, petsInKennel, seasonMultiplier, yieldMultiplier, need, req,
        )
    }

    /** Prices every tariff the operator sells and that is still open on this sailing. */
    fun priceTariffs(eval: LegEvaluation): List<PricedTariff> {
        val today = LocalDate.now(clock)
        return eval.operator.rules.tariffs
            .filter { TariffAvailability.isOpen(it, eval.sailing.departureDate, today, eval.availability.loadFactor) }
            .map { price(eval, it) }
    }

    fun price(eval: LegEvaluation, tariff: TariffDef): PricedTariff {
        val req = eval.request
        val input = LegPriceInput(
            sailing = eval.sailing,
            operator = eval.operator,
            fareTable = eval.fareTable,
            categories = eval.categories,
            pmrPassengers = if (req.accessibility.wheelchair || req.accessibility.reducedMobility) 1 else 0,
            vehicleCategory = eval.vehicleCategory,
            petsInCabin = eval.petsInCabin,
            petsInKennel = eval.petsInKennel,
            cabins = eval.cabins,
            tariff = tariff,
            seasonMultiplier = eval.seasonMultiplier,
            yieldMultiplier = eval.yieldMultiplier,
            lang = req.lang,
        )
        val currency = pricing.currencyOf(eval.sailing, eval.fareTable)
        val lines = pricing.lines(input).toMutableList()
        val promotion = promotions.best(
            PromotionContext(
                operator = eval.operator.code,
                departureDate = eval.sailing.departureDate,
                today = LocalDate.now(clock),
                passengers = req.passengers,
                vehicle = req.vehicle,
                accommodation = req.accommodation,
                roundTrip = req.roundTrip,
                tariff = tariff.code,
            ),
        )
        if (promotion != null) {
            val base = PricingEngine.discountableBase(lines, currency)
            val discount = base.scale(BigDecimal.valueOf(promotion.discount)).roundedForSale()
            if (!discount.isZero) {
                lines += PricingEngine.line(PriceLineKind.DISCOUNT, Labels.promotion(promotion.title.get(req.lang), req.lang), 1, -discount)
            }
        }
        return PricedTariff(tariff, lines, PricingEngine.total(lines, currency), promotion)
    }

    companion object {
        fun currencyOf(eval: LegEvaluation): CurrencyCode = eval.fareTable.currency
    }
}
