package dz.wave.pricing

import dz.wave.domain.AccommodationType
import dz.wave.domain.CabinAllocation
import dz.wave.domain.CurrencyCode
import dz.wave.domain.FareTable
import dz.wave.domain.Lang
import dz.wave.domain.Money
import dz.wave.domain.Operator
import dz.wave.domain.PriceLine
import dz.wave.domain.PriceLineKind
import dz.wave.domain.PriceSource
import dz.wave.domain.Sailing
import dz.wave.domain.TariffDef
import dz.wave.domain.VehicleCategory
import dz.wave.rules.FareCategory
import dz.wave.rules.FareClassifier
import dz.wave.rules.VehicleClassifier
import java.math.BigDecimal

/** Everything needed to price one crossing for one group, in the operator's currency. */
data class LegPriceInput(
    val sailing: Sailing,
    val operator: Operator,
    val fareTable: FareTable,
    val categories: List<FareCategory>,
    val pmrPassengers: Int,
    val vehicleCategory: VehicleCategory?,
    val petsInCabin: Int,
    val petsInKennel: Int,
    val cabins: List<CabinAllocation>,
    val tariff: TariffDef,
    val seasonMultiplier: Double,
    val yieldMultiplier: Double,
    val lang: Lang,
)

/**
 * Builds the itemised fare of a leg. REFERENCE prices = public fare table × season × yield;
 * LIVE prices observed on the operator's channel replace the corresponding components as-is.
 */
class PricingEngine {

    /** Ingestion normalises live prices to the route's selling currency, so one leg = one currency. */
    @Suppress("UNUSED_PARAMETER")
    fun currencyOf(sailing: Sailing, table: FareTable): CurrencyCode = table.currency

    fun lines(input: LegPriceInput): List<PriceLine> {
        val table = input.fareTable
        val live = input.sailing.livePrices?.takeIf { input.sailing.source == PriceSource.LIVE }
        val currency = currencyOf(input.sailing, table)
        val dynamic = input.seasonMultiplier * input.yieldMultiplier
        val tariffMultiplier = input.tariff.multiplier
        val rules = input.operator.rules
        val lang = input.lang
        val out = mutableListOf<PriceLine>()

        val seatBase = live?.adultSeat ?: (table.adultSeat * dynamic)
        val byCategory = input.categories.groupingBy { it }.eachCount()
        listOf(FareCategory.ADULT, FareCategory.SENIOR, FareCategory.YOUTH, FareCategory.CHILD, FareCategory.INFANT)
            .forEach { category ->
                val count = byCategory[category] ?: return@forEach
                val discount = FareClassifier.discount(category, rules.discounts)
                val unit = money(seatBase * tariffMultiplier * (1.0 - discount), currency)
                out += line(PriceLineKind.PASSAGE, Labels.passage(FareClassifier.label(category, lang), lang), count, unit)
            }

        if (input.pmrPassengers > 0 && rules.discounts.pmr > 0) {
            val unit = money(seatBase * tariffMultiplier * rules.discounts.pmr, currency)
            out += line(PriceLineKind.DISCOUNT, Labels.pmrReduction(lang), input.pmrPassengers, -unit)
        }

        input.cabins.forEach { allocation ->
            val base = live?.accommodation?.get(allocation.type)
                ?: (table.accommodation[allocation.type] ?: error("No fare for ${allocation.type}")) * dynamic
            val unit = money(base * tariffMultiplier, currency)
            out += line(PriceLineKind.ACCOMMODATION, Labels.accommodation(allocation.type, lang), allocation.count, unit)
        }

        input.vehicleCategory?.let { category ->
            val base = live?.vehicles?.get(category)
                ?: (table.vehicles[category] ?: error("No fare for $category")) * dynamic
            out += line(PriceLineKind.VEHICLE, VehicleClassifier.label(category, lang), 1, money(base * tariffMultiplier, currency))
        }

        if (input.petsInCabin > 0) {
            out += line(PriceLineKind.PET, Labels.pet(true, lang), input.petsInCabin, money(table.pet, currency))
        }
        if (input.petsInKennel > 0) {
            out += line(PriceLineKind.PET, Labels.pet(false, lang), input.petsInKennel, money(table.pet, currency))
        }

        val taxedPassengers = input.categories.count { it != FareCategory.INFANT || rules.infantsPayTaxes }
        if (taxedPassengers > 0 && table.taxes.perPassenger > 0) {
            out += line(PriceLineKind.TAX, Labels.passengerTaxes(lang), taxedPassengers, money(table.taxes.perPassenger, currency))
        }
        if (input.vehicleCategory != null && table.taxes.perVehicle > 0) {
            out += line(PriceLineKind.TAX, Labels.vehicleTaxes(lang), 1, money(table.taxes.perVehicle, currency))
        }
        return out
    }

    /** Price of one cabin of [type] before tariff multiplier — used to pick the cheapest allocation. */
    fun cabinUnitPrice(input: LegPriceInput, type: AccommodationType): Money? {
        val live = input.sailing.livePrices?.takeIf { input.sailing.source == PriceSource.LIVE }
        val currency = currencyOf(input.sailing, input.fareTable)
        val base = live?.accommodation?.get(type)
            ?: input.fareTable.accommodation[type]?.times(input.seasonMultiplier * input.yieldMultiplier)
            ?: return null
        return money(base, currency)
    }

    companion object {
        fun money(amount: Double, currency: CurrencyCode): Money =
            Money.of(BigDecimal.valueOf(amount), currency).roundedForSale()

        fun line(kind: PriceLineKind, label: String, quantity: Int, unit: Money): PriceLine =
            PriceLine(kind, label, quantity, unit, unit.times(quantity))

        fun total(lines: List<PriceLine>, currency: CurrencyCode): Money =
            Money.sum(lines.map { it.total }, currency)

        /** Part of a leg that promotions and round-trip discounts may reduce (taxes excluded). */
        fun discountableBase(lines: List<PriceLine>, currency: CurrencyCode): Money =
            Money.sum(
                lines.filter {
                    it.kind == PriceLineKind.PASSAGE || it.kind == PriceLineKind.ACCOMMODATION ||
                        it.kind == PriceLineKind.VEHICLE || (it.kind == PriceLineKind.DISCOUNT && it.total.isNegative)
                }.map { it.total },
                currency,
            )
    }
}
