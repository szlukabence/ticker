package dev.ticker.converter.core.math

import dev.ticker.converter.fixtures.Fixtures.BTC
import dev.ticker.converter.fixtures.Fixtures.ETH
import dev.ticker.converter.fixtures.Fixtures.HUF
import dev.ticker.converter.fixtures.Fixtures.JPY
import dev.ticker.converter.fixtures.Fixtures.USD
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class AmountFormatterTest {
    private val dot = AmountFormatter('.')
    private val comma = AmountFormatter(',')
    private val ts = ' '

    @Test fun groupsWithThinSpace() = assertEquals("39${ts}540.00", dot.format(BigDecimal("39540"), HUF))
    @Test fun commaLocale() = assertEquals("115,62", comma.format(BigDecimal("115.62"), USD))
    @Test fun smallValueSigDigits() = assertEquals("0.001094", dot.format(BigDecimal("0.0010943217"), BTC))
    @Test fun smallFiatKeepsMinorUnits() = assertEquals("0.50", dot.format(BigDecimal("0.5"), USD))
    @Test fun cryptoAboveOneTwoDecimals() = assertEquals("3.21", dot.format(BigDecimal("3.21456"), ETH))
    @Test fun zeroDecimalsCurrency() = assertEquals("1${ts}290", dot.format(BigDecimal("1290"), JPY))
    @Test fun hugeValuePlain() = assertEquals(
        "1${ts}000${ts}000${ts}000${ts}000${ts}000${ts}000.00",
        dot.format(BigDecimal("1E+18"), USD),
    )
    @Test fun negativeValue() = assertEquals("-1${ts}290", dot.format(BigDecimal("-1290"), JPY))
    @Test fun plainForKeypad() = assertEquals("39540", dot.plain(BigDecimal("39540.004"), HUF))
    @Test fun plainKeepsNeededDecimals() = assertEquals("12.5", dot.plain(BigDecimal("12.50"), USD))
    @Test fun forLocaleHungarianUsesComma() =
        assertEquals("115,62", AmountFormatter.forLocale(java.util.Locale("hu", "HU")).format(BigDecimal("115.62"), USD))

    @Test fun formatTypedKeepsDigits() = assertEquals("1${ts}234.5", dot.formatTyped("1234.5"))
    @Test fun formatTypedTrailingSeparator() = assertEquals("12,", comma.formatTyped("12."))
    @Test fun formatTypedSmallValue() = assertEquals("0.0010943", dot.formatTyped("0.0010943"))
    @Test fun formatTypedNegative() = assertEquals("-1${ts}234", dot.formatTyped("−1234"))
}
