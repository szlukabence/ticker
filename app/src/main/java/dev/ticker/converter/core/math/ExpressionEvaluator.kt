package dev.ticker.converter.core.math

import java.math.BigDecimal
import java.math.MathContext

/**
 * Keypad expressions: decimal numbers joined by + − × ÷ (U+2212, U+00D7, U+00F7), "." as the
 * decimal point, normal precedence, no parentheses. A leading "−" makes the first number negative.
 */
object ExpressionEvaluator {
    const val OPS = "+−×÷"
    private const val MAX_DIGITS = 15
    private val MC = MathContext.DECIMAL64

    /** Result of [expr], ignoring a trailing operator. Null when empty or on division by zero. */
    fun evaluate(expr: String): BigDecimal? {
        var e = expr
        while (e.isNotEmpty() && e.last() in OPS && e != "−") e = e.dropLast(1)
        if (e.isEmpty() || e == "−") return null

        val numbers = mutableListOf<BigDecimal>()
        val ops = mutableListOf<Char>()
        var negative = false
        var start = 0
        if (e.first() == '−') { negative = true; start = 1 }
        val sb = StringBuilder()
        fun flush(): Boolean {
            val text = sb.toString().removeSuffix(".")
            if (text.isEmpty() || text == ".") return false
            var n = BigDecimal(if (text.startsWith(".")) "0$text" else text)
            if (negative) { n = n.negate(); negative = false }
            numbers += n
            sb.clear()
            return true
        }
        for (i in start until e.length) {
            val c = e[i]
            if (c in OPS) {
                if (!flush()) return null
                ops += c
            } else {
                sb.append(c)
            }
        }
        if (!flush()) return null

        // First pass: × and ÷.
        val terms = mutableListOf(numbers[0])
        val addOps = mutableListOf<Char>()
        for (i in ops.indices) {
            val rhs = numbers[i + 1]
            when (val op = ops[i]) {
                '×' -> terms[terms.lastIndex] = terms.last().multiply(rhs, MC)
                '÷' -> {
                    if (rhs.signum() == 0) return null
                    terms[terms.lastIndex] = terms.last().divide(rhs, MC)
                }
                else -> { addOps += op; terms += rhs }
            }
        }
        // Second pass: + and −.
        var result = terms[0]
        for (i in addOps.indices) {
            result = if (addOps[i] == '+') result.add(terms[i + 1], MC) else result.subtract(terms[i + 1], MC)
        }
        return result
    }

    /** Appends a keypad [key] ('0'..'9', '.', or one of [OPS]) following the keypad input rules. */
    fun append(expr: String, key: Char): String {
        val current = currentNumber(expr)
        return when {
            key.isDigit() -> when {
                current.count { it.isDigit() } >= MAX_DIGITS -> expr
                current == "0" -> expr.dropLast(1) + key
                else -> expr + key
            }
            key == '.' -> when {
                '.' in current -> expr
                current.isEmpty() -> "${expr}0."
                else -> "$expr."
            }
            key in OPS -> when {
                expr.isEmpty() -> if (key == '−') "−" else expr
                expr == "−" -> expr
                expr.last() in OPS -> expr.dropLast(1) + key
                else -> expr + key
            }
            else -> expr
        }
    }

    fun backspace(expr: String): String = expr.dropLast(1)

    private fun currentNumber(expr: String): String {
        val lastOp = expr.indexOfLast { it in OPS }
        return expr.substring(lastOp + 1)
    }
}
