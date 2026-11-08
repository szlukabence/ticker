package dev.ticker.converter.data.catalog

import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import org.junit.Assert.assertEquals
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
}
