package dev.ticker.converter.data.catalog

import android.content.Context
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * All currencies the app knows. Fiat comes from the bundled ISO list; crypto is added at runtime
 * from the rate sources. A crypto ticker that equals a fiat code is never registered.
 */
class CurrencyCatalog(
    fiat: List<Currency>,
    private val cryptoNames: Map<String, String>,
    /** Languages whose fiat names come from the platform (java.util.Currency); others keep the bundled English. */
    private val localizedLanguages: Set<String> = emptySet(),
    private val locale: () -> Locale = Locale::getDefault,
) {
    private val fiatByCode: Map<String, Currency> = fiat.associateBy { it.code.uppercase() }
    private val cryptoByCode = ConcurrentHashMap<String, Currency>()
    private val localizedFiat = ConcurrentHashMap<Locale, Map<String, Currency>>()

    /** Fiat currencies with names in the current language; the bundled English map when that has no translation. */
    private fun fiatNow(): Map<String, Currency> {
        val loc = locale()
        if (loc.language !in localizedLanguages) return fiatByCode
        return localizedFiat.getOrPut(loc) {
            fiatByCode.mapValues { (code, c) ->
                val name = runCatching { java.util.Currency.getInstance(code).getDisplayName(loc) }.getOrNull()
                    ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
                if (name != null) c.copy(name = name) else c
            }
        }
    }

    fun get(code: String): Currency? {
        val c = code.uppercase()
        return fiatNow()[c] ?: cryptoByCode[c]
    }

    fun isFiat(code: String): Boolean = code.uppercase() in fiatByCode

    fun all(): List<Currency> =
        fiatNow().values.sortedBy { it.code } + cryptoByCode.values.sortedBy { it.code }

    fun search(query: String): List<Currency> {
        val q = query.trim()
        if (q.isEmpty()) return all()
        val all = all()
        val byCode = all.filter { it.code.startsWith(q, ignoreCase = true) }
        // Also match the bundled English name, so "dollar" still finds USD in Hungarian.
        val byName = all.filter {
            it !in byCode && (it.name.contains(q, ignoreCase = true) ||
                fiatByCode[it.code]?.name?.contains(q, ignoreCase = true) == true)
        }
        return byCode + byName
    }

    fun registerCryptos(codes: Collection<String>) {
        for (raw in codes) {
            val code = raw.uppercase()
            if (code in fiatByCode || cryptoByCode.containsKey(code)) continue
            cryptoByCode[code] = Currency(
                code = code,
                name = cryptoNames[code] ?: code,
                kind = CurrencyKind.CRYPTO,
                glyph = when (code) { "BTC" -> "₿"; "ETH" -> "Ξ"; else -> code.take(1) },
                decimals = 8,
            )
        }
    }

    fun defaultFavorites(locale: Locale): List<String> {
        val local = runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()
            ?.takeIf { it in fiatByCode }
        return (listOfNotNull(local) + listOf("USD", "EUR", "GBP", "BTC", "ETH")).distinct()
    }

    /** Crypto names known from the bundled map (used to register fawazahmed0-only coins). */
    fun knownCryptoCodes(): Set<String> = cryptoNames.keys

    @Serializable
    private data class FiatJson(val code: String, val name: String, val flag: String, val decimals: Int)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromAssets(context: Context): CurrencyCatalog {
            val fiatText = context.assets.open("currencies.json").bufferedReader().use { it.readText() }
            val cryptoText = context.assets.open("crypto_names.json").bufferedReader().use { it.readText() }
            val fiat = json.decodeFromString<List<FiatJson>>(fiatText)
                .map { Currency(it.code, it.name, CurrencyKind.FIAT, it.flag, it.decimals) }
            val names = json.decodeFromString<Map<String, String>>(cryptoText)
            return CurrencyCatalog(fiat, names, localizedLanguages = setOf("hu")).also { it.registerCryptos(listOf("BTC", "ETH")) }
        }
    }
}
