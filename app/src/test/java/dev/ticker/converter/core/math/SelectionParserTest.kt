package dev.ticker.converter.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SelectionParserTest {
    private val known = setOf("USD", "EUR", "HUF", "GBP", "JPY", "INR", "PLN", "CZK", "RON", "CHF", "BTC", "CAD", "AUD", "ALL", "TRY", "TOP")
    private fun p(t: String) = SelectionParser.parse(t) { it in known }
    private fun check(text: String, amount: String?, code: String?) {
        val s = p(text)
        if (amount == null) assertNull("amount of '$text'", s.amount)
        else assertEquals("amount of '$text'", 0, BigDecimal(amount).compareTo(s.amount ?: error("no amount in '$text'")))
        assertEquals("currency of '$text'", code, s.currencyCode)
    }

    @Test fun symbolTable() {
        check("€12.99", "12.99", "EUR")
        check("12,99 €", "12.99", "EUR")
        check("1 290 Ft", "1290", "HUF")
        check("£5", "5", "GBP")
        check("¥300", "300", "JPY")
        check("₹99", "99", "INR")
        check("25 zł", "25", "PLN")
        check("120 Kč", "120", "CZK")
        check("45 lei", "45", "RON")
        check("CHF 9.50", "9.50", "CHF")
        check("₿0.01", "0.01", "BTC")
        check("$5", "5", "USD")
        check("US$5", "5", "USD")
        check("C$5", "5", "CAD")
        check("CA$5", "5", "CAD")
        check("A$5", "5", "AUD")
        check("AU$5", "5", "AUD")
    }

    @Test fun isoCodeBeforeAndAfter() {
        check("USD 5", "5", "USD")
        check("5 usd", "5", "USD")
        check("5.00 EUR", "5.00", "EUR")
    }

    @Test fun unknownCodeIgnored() = check("5 XYZ", "5", null)
    @Test fun ambiguousKrIsNull() = check("50 kr", "50", null)
    @Test fun noNumber() = check("hello €", null, "EUR")

    @Test fun prefersNumberNextToSymbol() {
        check("2 for €5", "5", "EUR")
        check("€12,99 incl. 27% VAT", "12.99", "EUR")
    }

    @Test fun thousands() {
        check("€1,234.50", "1234.50", "EUR")
        check("1.290,00 Ft", "1290", "HUF")
    }

    // Ordinary words that happen to be ISO codes (ALL, TOP, TRY) only count right next to a number.
    @Test fun codeWordsAwayFromNumberIgnored() = check("try all 3 for 12.99", "3", null)

    @Test fun spaceIsNotAThousandsSeparatorBeforeShortRuns() = check("12.99 2 items", "12.99", null)

    // Any app can send text to "Convert"; a huge selection must not freeze the card.
    @Test(timeout = 2_000) fun hugeSelectionStaysFast() = check("€5 " + "1 \$ ".repeat(100_000), "5", "EUR")
}
