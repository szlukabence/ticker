package dev.ticker.converter.data.catalog

import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyCatalogTest {
    private fun fiat(code: String, name: String) = Currency(code, name, CurrencyKind.FIAT, "🏳", 2)

    private fun catalog() = CurrencyCatalog(
        fiat = listOf(
            fiat("USD", "US Dollar"), fiat("EUR", "Euro"), fiat("HUF", "Hungarian Forint"),
            fiat("GBP", "British Pound"), fiat("ETB", "Ethiopian Birr"),
        ),
        cryptoNames = mapOf("BTC" to "Bitcoin", "ETH" to "Ethereum"),
    )

    @Test
    fun getIsCaseInsensitive() {
        assertEquals("HUF", catalog().get("huf")?.code)
    }

    @Test
    fun searchCodePrefixRanksFirst() {
        assertEquals("EUR", catalog().search("eu").first().code)
    }

    @Test
    fun searchMatchesName() {
        assertEquals(listOf("HUF"), catalog().search("forint").map { it.code })
    }

    @Test
    fun cryptoCollidingWithFiatIsDropped() {
        val c = catalog()
        c.registerCryptos(listOf("HUF", "BTC"))
        assertEquals(CurrencyKind.FIAT, c.get("HUF")!!.kind)
        assertEquals(CurrencyKind.CRYPTO, c.get("BTC")!!.kind)
    }

    @Test
    fun unknownCryptoUsesTickerAsName() {
        val c = catalog()
        c.registerCryptos(listOf("ZZZ"))
        assertEquals("ZZZ", c.get("ZZZ")!!.name)
    }

    @Test
    fun defaultFavoritesHungary() {
        assertEquals(listOf("HUF", "USD", "EUR", "GBP", "BTC", "ETH"), catalog().defaultFavorites(Locale("hu", "HU")))
    }

    @Test
    fun defaultFavoritesUsDeduped() {
        assertEquals(listOf("USD", "EUR", "GBP", "BTC", "ETH"), catalog().defaultFavorites(Locale.US))
    }

    private fun localized(tag: () -> Locale) = CurrencyCatalog(
        fiat = listOf(fiat("USD", "US Dollar"), fiat("EUR", "Euro"), fiat("HUF", "Hungarian Forint"), fiat("ZZZ", "Made Up")),
        cryptoNames = mapOf("BTC" to "Bitcoin"),
        localizedLanguages = setOf("hu"),
        locale = tag,
    )

    private fun hu() = localized { Locale.forLanguageTag("hu-HU") }

    @Test fun huNamesAreLocalised() {
        val name = hu().get("USD")!!.name
        assertNotEquals("US Dollar", name)
        assertTrue(name, "dollár" in name.lowercase())
    }

    @Test fun huSearchFindsBothNames() {
        val c = hu()
        assertTrue(c.search("dollár").any { it.code == "USD" })
        assertTrue(c.search("dollar").any { it.code == "USD" })
    }

    @Test fun englishKeepsBundledNames() =
        assertEquals("US Dollar", localized { Locale.forLanguageTag("en-US") }.get("USD")!!.name)

    @Test fun untranslatedLanguageKeepsEnglish() =
        assertEquals("US Dollar", localized { Locale.forLanguageTag("de-DE") }.get("USD")!!.name)

    @Test fun localeIsReadPerCall() {
        var l = Locale.forLanguageTag("en-US")
        val c = localized { l }
        assertEquals("US Dollar", c.get("USD")!!.name)
        l = Locale.forLanguageTag("hu-HU")
        assertNotEquals("US Dollar", c.get("USD")!!.name)
    }

    @Test fun unknownCodeFallsBack() = assertEquals("Made Up", hu().get("ZZZ")!!.name)

    @Test fun formatterStillFollowsRegion() {
        val f = AmountFormatter.forLocale(Locale.forLanguageTag("hu-HU"))
        assertTrue(f.format(java.math.BigDecimal("1234.5"), Currency("HUF", "Forint", CurrencyKind.FIAT, "🇭🇺", 2)).contains(","))
    }
}
