package dev.ticker.converter.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ExpressionEvaluatorTest {
    private fun eq(expected: String, actual: BigDecimal?) =
        assertEquals(0, BigDecimal(expected).compareTo(actual!!), "expected $expected but was $actual")

    private fun assertEquals(expected: Int, actual: Int, msg: String) = org.junit.Assert.assertEquals(msg, expected, actual)

    @Test fun precedence() = eq("14", ExpressionEvaluator.evaluate("2+3×4"))
    @Test fun leftToRight() = eq("1", ExpressionEvaluator.evaluate("20÷4÷5"))
    @Test fun trailingOperatorIgnored() = eq("25", ExpressionEvaluator.evaluate("25×"))
    @Test fun divisionByZeroIsNull() = assertNull(ExpressionEvaluator.evaluate("5÷0"))
    @Test fun emptyIsNull() = assertNull(ExpressionEvaluator.evaluate(""))
    @Test fun decimalExact() = eq("0.3", ExpressionEvaluator.evaluate("0.1+0.2"))
    @Test fun negativeStart() = eq("-3", ExpressionEvaluator.evaluate("−5+2"))
    @Test fun subtraction() = eq("7", ExpressionEvaluator.evaluate("10−3"))

    @Test fun appendReplacesOperator() = assertEquals("5×", ExpressionEvaluator.append("5+", '×'))
    @Test fun appendLeadingZero() = assertEquals("5", ExpressionEvaluator.append("0", '5'))
    @Test fun appendSecondDotIgnored() = assertEquals("1.5", ExpressionEvaluator.append("1.5", '.'))
    @Test fun appendDotStartsZero() = assertEquals("5+0.", ExpressionEvaluator.append("5+", '.'))
    @Test fun appendOperatorOnEmptyIgnored() = assertEquals("", ExpressionEvaluator.append("", '×'))
    @Test fun appendMinusOnEmptyStartsNegative() = assertEquals("−", ExpressionEvaluator.append("", '−'))

    @Test fun rejectsSixteenthDigit() {
        var e = ""
        repeat(16) { e = ExpressionEvaluator.append(e, '9') }
        assertEquals(15, e.length)
    }

    @Test fun backspace() {
        assertEquals("12", ExpressionEvaluator.backspace("12+"))
        assertEquals("", ExpressionEvaluator.backspace(""))
    }
}
