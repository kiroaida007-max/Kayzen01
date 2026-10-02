package dz.wave.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
enum class CurrencyCode(val minorDigits: Int, val isoNumeric: String) {
    DZD(2, "012"),
    EUR(2, "978"),
    USD(2, "840"),
}

/**
 * Monetary amount stored in minor units (centimes / cents) so that JSON clients never
 * do floating point arithmetic on prices. Immutable: every operation returns a new value.
 */
@Serializable
data class Money(val minor: Long, val currency: CurrencyCode) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(minor, other.minor), currency)
    }

    operator fun unaryMinus(): Money = Money(-minor, currency)

    fun scale(factor: BigDecimal): Money = of(toDecimal().multiply(factor), currency)

    fun scale(factor: Double): Money = scale(BigDecimal.valueOf(factor))

    fun times(quantity: Int): Money = Money(Math.multiplyExact(minor, quantity.toLong()), currency)

    fun toDecimal(): BigDecimal = BigDecimal.valueOf(minor, currency.minorDigits)

    /** Dinar prices are quoted and charged in whole dinars; EUR/USD keep cents. */
    fun roundedForSale(): Money = when (currency) {
        CurrencyCode.DZD -> of(toDecimal().setScale(0, RoundingMode.HALF_UP), currency)
        else -> this
    }

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    private fun requireSameCurrency(other: Money) =
        require(other.currency == currency) { "Currency mismatch: $currency vs ${other.currency}" }

    override fun toString(): String = "${toDecimal().toPlainString()} $currency"

    companion object {
        fun zero(currency: CurrencyCode) = Money(0, currency)

        fun of(amount: BigDecimal, currency: CurrencyCode): Money =
            Money(amount.setScale(currency.minorDigits, RoundingMode.HALF_UP).unscaledValue().longValueExact(), currency)

        fun of(amount: Double, currency: CurrencyCode): Money = of(BigDecimal.valueOf(amount), currency)

        fun sum(items: Iterable<Money>, currency: CurrencyCode): Money =
            items.fold(zero(currency)) { acc, m -> acc + m }
    }
}
