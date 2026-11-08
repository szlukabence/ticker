package dev.ticker.converter.core.math

import java.math.BigDecimal
import java.math.MathContext

/** Card/bank markup on converted amounts, in basis points (175 = 1.75 %). Spec: extras §2. */
object Fee {
    const val MAX_BPS = 1000
    const val STEP_BPS = 10
    val PRESETS = listOf(50, 100, 175, 200, 250, 300)

    private val TEN_THOUSAND = BigDecimal(10_000)

    /** [amount] × (1 + bps / 10 000). */
    fun apply(amount: BigDecimal, bps: Int): BigDecimal =
        amount.multiply(TEN_THOUSAND + BigDecimal(bps), MathContext.DECIMAL64).divide(TEN_THOUSAND, MathContext.DECIMAL64)

    /** "2%", "1.75%", "0.5%". */
    fun label(bps: Int): String = BigDecimal(bps).movePointLeft(2).stripTrailingZeros().toPlainString() + "%"
}
