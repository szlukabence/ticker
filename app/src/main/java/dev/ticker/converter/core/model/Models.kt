package dev.ticker.converter.core.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

enum class CurrencyKind { FIAT, CRYPTO }

/** [glyph] is a flag emoji for fiat or a short symbol for crypto. */
data class Currency(
    val code: String,
    val name: String,
    val kind: CurrencyKind,
    val glyph: String,
    val decimals: Int,
)

/** [BINANCE] is no longer fetched; it stays so rate caches saved by older builds still load. */
enum class RateSource { BINANCE, FRANKFURTER, FAWAZ }

/** All values are USD per 1 unit of the currency. Codes are uppercase. */
data class RateSnapshot(
    val usdPer: Map<String, BigDecimal>,
    val usdPerPrev: Map<String, BigDecimal>,
    val sourceFor: Map<String, RateSource>,
    val fetchedAt: Instant,
)

sealed interface SyncStatus {
    data object Syncing : SyncStatus
    data object NoData : SyncStatus
    data class Live(val at: Instant) : SyncStatus
    data class Partial(val at: Instant, val failed: Set<RateSource>) : SyncStatus
    data class Offline(val lastSync: Instant) : SyncStatus
}

/** [value] is USD per unit for single-currency series, or quote per base for pair series. */
data class PricePoint(val time: Instant, val value: BigDecimal)

enum class ChartRange(val label: String) { D1("1D"), W1("1W"), M1("1M"), M3("3M"), Y1("1Y"), Y5("5Y"), MAX("MAX") }

@Serializable
data class SavedPair(val base: String, val quote: String)

@Serializable
data class Settings(
    val favorites: List<String> = emptyList(),
    val activeCode: String? = null,
    val expression: String = "100",
    val cameraFrom: String? = null,
    /** Camera's "convert into" currency; null until the person picks one. */
    val cameraTo: String? = null,
    val chartBase: String? = null,
    val chartQuote: String? = null,
    val savedPairs: List<SavedPair> = emptyList(),
    val initialized: Boolean = false,
    /** Card fee in basis points (200 = 2 %), applied to converted amounts while [feeOn]. */
    val feeBps: Int = 200,
    val feeOn: Boolean = false,
    /** Last Camera mode used, so the tab reopens where the person left it. */
    val cameraMode: CameraMode = CameraMode.PRICE,
    /** Light or dark look; SYSTEM follows the phone's dark-mode setting. */
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

@Serializable
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** Camera tab: read one price live, or photograph a whole receipt. */
@Serializable
enum class CameraMode { PRICE, RECEIPT }
