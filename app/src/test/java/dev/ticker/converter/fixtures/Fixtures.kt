package dev.ticker.converter.fixtures

import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import java.math.BigDecimal
import java.time.Instant

object Fixtures {
    val USD = Currency("USD", "US Dollar", CurrencyKind.FIAT, "🇺🇸", 2)
    val EUR = Currency("EUR", "Euro", CurrencyKind.FIAT, "🇪🇺", 2)
    val HUF = Currency("HUF", "Hungarian Forint", CurrencyKind.FIAT, "🇭🇺", 2)
    val GBP = Currency("GBP", "British Pound", CurrencyKind.FIAT, "🇬🇧", 2)
    val JPY = Currency("JPY", "Japanese Yen", CurrencyKind.FIAT, "🇯🇵", 0)
    val VND = Currency("VND", "Vietnamese Dong", CurrencyKind.FIAT, "🇻🇳", 0)
    val BTC = Currency("BTC", "Bitcoin", CurrencyKind.CRYPTO, "₿", 8)
    val ETH = Currency("ETH", "Ethereum", CurrencyKind.CRYPTO, "Ξ", 8)

    val T0: Instant = Instant.parse("2026-11-07T09:41:07Z")

    val snapshot = RateSnapshot(
        usdPer = mapOf(
            "USD" to BigDecimal.ONE, "EUR" to BigDecimal("1.1165"), "HUF" to BigDecimal("0.0030681"),
            "GBP" to BigDecimal("1.3385"), "BTC" to BigDecimal("75933.63"), "ETH" to BigDecimal("3596.10"),
        ),
        usdPerPrev = mapOf(
            "USD" to BigDecimal.ONE, "EUR" to BigDecimal("1.1133"), "HUF" to BigDecimal("0.0030350"),
            "GBP" to BigDecimal("1.3370"), "BTC" to BigDecimal("76124.99"), "ETH" to BigDecimal("3515.20"),
        ),
        sourceFor = mapOf(
            "USD" to RateSource.FRANKFURTER, "EUR" to RateSource.FRANKFURTER, "HUF" to RateSource.FRANKFURTER,
            "GBP" to RateSource.FRANKFURTER, "BTC" to RateSource.FAWAZ, "ETH" to RateSource.FAWAZ,
        ),
        fetchedAt = T0,
    )
}
