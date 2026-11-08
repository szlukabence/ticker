package dev.ticker.converter.data.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BundledAssetsTest {
    private val cryptoCodes = (Json.parseToJsonElement(File("src/main/assets/crypto_names.json").readText()) as JsonObject).keys

    /** Codes that are money issued by a state (current or historic ISO 4217, or territory pounds/offshore yuan). */
    private val notCrypto = java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() +
        setOf("GGP", "JEP", "IMP", "CNH", "TVD", "SPL")

    @Test fun cryptoNamesContainNoFiatOrObsoleteCurrencies() {
        assertEquals(emptyList<String>(), cryptoCodes.filter { it in notCrypto }.sorted())
    }
}
