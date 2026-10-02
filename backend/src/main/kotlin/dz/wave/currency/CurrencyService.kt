package dz.wave.currency

import dz.wave.domain.CurrencyCode
import dz.wave.domain.Money
import dz.wave.domain.RateInfo
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Official Banque d'Algérie rates, expressed as dinars per one unit of each currency. */
data class RateSnapshot(
    val dzdPerUnit: Map<CurrencyCode, BigDecimal>,
    val asOf: Instant,
    val source: String,
    val version: Long,
) {
    fun rate(currency: CurrencyCode): BigDecimal =
        if (currency == CurrencyCode.DZD) BigDecimal.ONE else dzdPerUnit[currency] ?: error("No rate for $currency")

    fun info(): RateInfo = RateInfo(
        base = CurrencyCode.DZD,
        dzdPerUnit = CurrencyCode.entries.associateWith { rate(it).stripTrailingZeros().toPlainString() },
        asOf = asOf,
        source = source,
    )
}

class CurrencyService(initial: RateSnapshot = defaultOfficialRates()) {
    private val ref = AtomicReference(initial)
    private val listeners = CopyOnWriteArrayList<(RateSnapshot) -> Unit>()

    fun current(): RateSnapshot = ref.get()

    fun onChange(listener: (RateSnapshot) -> Unit) {
        listeners += listener
    }

    /** Rejects obviously wrong feeds (a scraper parsing the wrong column) instead of repricing everything. */
    fun update(rates: Map<CurrencyCode, BigDecimal>, asOf: Instant, source: String): RateSnapshot {
        val previous = current()
        rates.forEach { (currency, value) ->
            require(currency != CurrencyCode.DZD) { "DZD is the base currency" }
            require(value > BigDecimal.ZERO) { "Rate for $currency must be positive" }
            val old = previous.dzdPerUnit[currency]
            if (old != null) {
                val change = value.subtract(old).abs().divide(old, 6, RoundingMode.HALF_UP)
                require(change <= MAX_DAILY_CHANGE) { "Rate change for $currency too large ($change)" }
            }
        }
        val next = RateSnapshot(previous.dzdPerUnit + rates, asOf, source, previous.version + 1)
        ref.set(next)
        listeners.forEach { it(next) }
        return next
    }

    fun convert(amount: Money, to: CurrencyCode, snapshot: RateSnapshot = current()): Money {
        if (amount.currency == to) return amount
        val dzd = amount.toDecimal().multiply(snapshot.rate(amount.currency))
        val converted = dzd.divide(snapshot.rate(to), 6, RoundingMode.HALF_UP)
        return Money.of(converted, to).roundedForSale()
    }

    companion object {
        private val MAX_DAILY_CHANGE = BigDecimal("0.10")

        /** Banque d'Algérie selling rates published on 1–2 October 2026 (see docs/research). */
        fun defaultOfficialRates(): RateSnapshot = RateSnapshot(
            dzdPerUnit = mapOf(
                CurrencyCode.DZD to BigDecimal.ONE,
                CurrencyCode.EUR to BigDecimal("151.06"),
                CurrencyCode.USD to BigDecimal("132.73"),
            ),
            asOf = Instant.parse("2026-10-01T10:00:00Z"),
            source = "Banque d'Algérie — cours officiel",
            version = 1,
        )
    }
}
