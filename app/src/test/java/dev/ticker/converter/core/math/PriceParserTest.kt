package dev.ticker.converter.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigDecimal

@RunWith(Parameterized::class)
class PriceParserTest(private val input: String, private val expected: String?) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: \"{0}\" -> {1}")
        fun rows(): List<Array<Any?>> = listOf(
            arrayOf("1 290", "1290"), arrayOf("1 290", "1290"), arrayOf("1.290", "1290"), arrayOf("1,290", "1290"),
            arrayOf("12.99", "12.99"), arrayOf("12,99", "12.99"), arrayOf("12,5", "12.5"),
            arrayOf("1.290,00", "1290.00"), arrayOf("1,290.00", "1290.00"),
            arrayOf("1'299.00", "1299.00"), arrayOf("1’299", "1299"), arrayOf("1.234.567", "1234567"),
            arrayOf("1,234,567.89", "1234567.89"),
            arrayOf("0,99", "0.99"), arrayOf(".99", "0.99"), arrayOf("990", "990"), arrayOf(" 45 ", "45"),
            arrayOf("45.", "45"), arrayOf("45,-", "45"),
            arrayOf("0", null), arrayOf("0,00", null), arrayOf("", null), arrayOf(".", null), arrayOf(",,", null),
            arrayOf("abc", null),
            arrayOf("12.3.4,5", "1234.5"),
            arrayOf("1 2", "12"), arrayOf("100 000", "100000"), arrayOf("9.999", "9999"), arrayOf("9.99", "9.99"),
            arrayOf("1 290", "1290"), arrayOf("€ 3,49", "3.49"),
        )
    }

    @Test
    fun table() {
        val r = PriceParser.parse(input)
        if (expected == null) assertNull("\"$input\"", r)
        else assertEquals("\"$input\"", 0, BigDecimal(expected).compareTo(r ?: error("\"$input\" parsed to null")))
    }
}
