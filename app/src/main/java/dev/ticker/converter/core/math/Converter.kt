package dev.ticker.converter.core.math

import dev.ticker.converter.core.model.RateSnapshot
import java.math.BigDecimal
import java.math.MathContext

object Converter {
    private val MC = MathContext.DECIMAL64

    /** [amount] of [from] expressed in [to]; null if either rate is missing. */
    fun convert(amount: BigDecimal, from: String, to: String, s: RateSnapshot): BigDecimal? {
        val f = s.usdPer[from] ?: return null
        val t = s.usdPer[to]?.takeIf { it.signum() != 0 } ?: return null
        return amount.multiply(f, MC).divide(t, MC)
    }

    /** Daily change of [code] measured in [against], in percent (spec §5.1). */
    fun dailyChangePct(code: String, against: String, s: RateSnapshot): Double? {
        val now = ratio(s.usdPer, code, against) ?: return null
        val prev = ratio(s.usdPerPrev, code, against)?.takeIf { it.signum() != 0 } ?: return null
        return (now.divide(prev, MC).toDouble() - 1.0) * 100.0
    }

    private fun ratio(m: Map<String, BigDecimal>, a: String, b: String): BigDecimal? {
        val x = m[a] ?: return null
        val y = m[b]?.takeIf { it.signum() != 0 } ?: return null
        return x.divide(y, MC)
    }
}
