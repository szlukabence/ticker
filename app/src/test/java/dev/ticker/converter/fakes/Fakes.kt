package dev.ticker.converter.fakes

import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.CachedSeries
import dev.ticker.converter.data.local.HistoryStore
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.local.SnapshotStore
import dev.ticker.converter.data.remote.DatedRates
import dev.ticker.converter.data.remote.EcbSource
import dev.ticker.converter.data.remote.FawazSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

fun bd(s: String) = BigDecimal(s)
fun rates(date: String, vararg pairs: Pair<String, String>) =
    DatedRates(LocalDate.parse(date), pairs.associate { it.first to BigDecimal(it.second) })

class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

class FakeEcb(
    var latest: DatedRates? = rates("2026-11-09", "EUR" to "0.89566", "HUF" to "325.93", "GBP" to "0.74711"),
    var series: List<DatedRates>? = listOf(
        rates("2026-11-05", "EUR" to "0.89095", "HUF" to "323.57", "GBP" to "0.7470"),
        rates("2026-11-06", "EUR" to "0.8920", "HUF" to "324.10", "GBP" to "0.7475"),
        rates("2026-11-09", "EUR" to "0.89566", "HUF" to "325.93", "GBP" to "0.74711"),
    ),
) : EcbSource {
    val seriesCalls = mutableListOf<Triple<LocalDate, LocalDate, Collection<String>>>()
    override suspend fun latest() = latest ?: throw IOException("ecb down")
    override suspend fun series(start: LocalDate, end: LocalDate, codes: Collection<String>): List<DatedRates> {
        seriesCalls += Triple(start, end, codes)
        return (series ?: throw IOException("ecb down")).filter { it.date in start..end }
    }
}

class FakeFawaz(
    var latest: DatedRates? = rates("2026-11-09", "HUF" to "326.0", "EUR" to "0.8960", "VND" to "26300", "BTC" to "0.0000131", "SOL" to "0.0055"),
    var byDate: (LocalDate) -> DatedRates? = { d -> rates(d.toString(), "HUF" to "325.0", "EUR" to "0.8950", "VND" to "26250", "BTC" to "0.0000130", "SOL" to "0.0054") },
) : FawazSource {
    val onCalls = mutableListOf<LocalDate>()
    override suspend fun latest() = latest ?: throw IOException("fawaz down")
    override suspend fun on(date: LocalDate): DatedRates {
        onCalls += date
        return byDate(date) ?: throw IOException("fawaz down for $date")
    }
}

class InMemorySnapshotStore(var value: RateSnapshot? = null) : SnapshotStore {
    var saves = 0
    override suspend fun load() = value
    override suspend fun save(s: RateSnapshot) { value = s; saves++ }
}

class InMemoryHistoryStore : HistoryStore {
    val map = mutableMapOf<String, CachedSeries>()
    override suspend fun load(key: String) = map[key]
    override suspend fun save(key: String, s: CachedSeries) { map[key] = s }
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings) { state.value = transform(state.value) }
}

fun testCatalog(): CurrencyCatalog {
    fun f(c: String, n: String, d: Int = 2) = Currency(c, n, CurrencyKind.FIAT, "🏳", d)
    return CurrencyCatalog(
        listOf(f("USD", "US Dollar"), f("EUR", "Euro"), f("HUF", "Hungarian Forint"), f("GBP", "British Pound"),
            f("VND", "Vietnamese Dong", 0), f("JPY", "Japanese Yen", 0)),
        mapOf("BTC" to "Bitcoin", "ETH" to "Ethereum", "SOL" to "Solana"),
    ).also { it.registerCryptos(listOf("BTC", "ETH")) }
}
