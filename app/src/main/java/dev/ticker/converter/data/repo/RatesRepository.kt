package dev.ticker.converter.data.repo

import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.core.model.SyncStatus
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SnapshotStore
import dev.ticker.converter.data.remote.DatedRates
import dev.ticker.converter.data.remote.EcbSource
import dev.ticker.converter.data.remote.FawazSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Owns the current [RateSnapshot] and its [SyncStatus] (spec §5). Merge precedence per code:
 * Frankfurter (ECB) → fawazahmed0 (other fiat and all crypto) → previous snapshot.
 *
 * Crypto used to come from Binance's public market-data API. Its data terms do not clearly allow use in a
 * published app, so crypto now comes from fawazahmed0 (public domain, CC0).
 */
class RatesRepository(
    private val ecb: EcbSource,
    private val fawaz: FawazSource,
    private val store: SnapshotStore,
    private val catalog: CurrencyCatalog,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _snapshot = MutableStateFlow<RateSnapshot?>(null)
    val snapshot: StateFlow<RateSnapshot?> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Syncing)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val mutex = Mutex()

    suspend fun loadCached() = mutex.withLock {
        if (_snapshot.value != null) return@withLock
        val cached = withContext(io) { store.load() }
        if (cached == null) {
            _status.value = SyncStatus.NoData
        } else {
            // Caches from builds that used Binance tag their coins BINANCE; keep showing them until a refresh.
            catalog.registerCryptos(cached.sourceFor.filterValues { it == RateSource.BINANCE }.keys)
            catalog.registerCryptos(cached.usdPer.keys.filter { it in catalog.knownCryptoCodes() })
            _snapshot.value = cached
            _status.value = SyncStatus.Offline(cached.fetchedAt)
        }
    }

    suspend fun refreshAll() = mutex.withLock {
        _status.value = SyncStatus.Syncing
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC))
        val results = withContext(io) {
            supervisorScope {
                val ecbLatest = async { attempt { ecb.latest() } }
                val ecbSeries = async { attempt { ecb.series(today.minusDays(7), today, emptyList()) } }
                val fawazLatest = async { attempt { fawaz.latest() } }
                val fl = fawazLatest.await()
                val fawazPrev = fl.getOrNull()?.let { attempt { fawaz.on(it.date.minusDays(1)) }.getOrNull() }
                Fetched(ecbLatest.await(), ecbSeries.await(), fl, fawazPrev)
            }
        }
        val previous = _snapshot.value ?: withContext(io) { store.load() }
        val now = clock.instant()

        val failed = buildSet {
            if (results.ecbLatest.isFailure || results.ecbSeries.isFailure) add(RateSource.FRANKFURTER)
            if (results.fawazLatest.isFailure) add(RateSource.FAWAZ)
        }
        val anySuccess = listOf(results.ecbLatest, results.ecbSeries, results.fawazLatest).any { it.isSuccess }

        if (!anySuccess) {
            if (previous != null) {
                _snapshot.value = previous
                _status.value = SyncStatus.Offline(previous.fetchedAt)
            } else {
                _status.value = SyncStatus.NoData
            }
            return@withLock
        }

        catalog.registerCryptos(PEGGED_TO_USD)
        results.fawazLatest.getOrNull()?.let { r -> catalog.registerCryptos(r.unitsPerUsd.keys.filter { it in catalog.knownCryptoCodes() }) }

        val merged = merge(previous, results, now)
        _snapshot.value = merged
        _status.value = if (failed.isEmpty()) SyncStatus.Live(now) else SyncStatus.Partial(now, failed)
        withContext(io) { store.save(merged) }
    }

    private fun merge(previous: RateSnapshot?, r: Fetched, now: java.time.Instant): RateSnapshot {
        val usdPer = previous?.usdPer.orEmpty().toMutableMap()
        val prev = previous?.usdPerPrev.orEmpty().toMutableMap()
        val source = previous?.sourceFor.orEmpty().toMutableMap()

        // Lowest precedence first; later layers overwrite.
        r.fawazLatest.getOrNull()?.let { layer(it, RateSource.FAWAZ, usdPer, source) }
        r.fawazPrev?.let { p -> p.unitsPerUsd.forEach { (c, u) -> inv(u)?.let { prev[c] = it } } }

        r.ecbLatest.getOrNull()?.let { latest ->
            layer(latest, RateSource.FRANKFURTER, usdPer, source)
            val prior = r.ecbSeries.getOrNull()?.lastOrNull { it.date < latest.date }
            prior?.unitsPerUsd?.forEach { (c, u) -> inv(u)?.let { prev[c] = it } }
        }

        usdPer["USD"] = BigDecimal.ONE
        prev["USD"] = BigDecimal.ONE
        source["USD"] = RateSource.FRANKFURTER
        for (c in PEGGED_TO_USD) { usdPer[c] = BigDecimal.ONE; prev[c] = BigDecimal.ONE; source[c] = RateSource.FAWAZ }
        return RateSnapshot(usdPer, prev, source, now)
    }

    private fun layer(r: DatedRates, src: RateSource, usdPer: MutableMap<String, BigDecimal>, source: MutableMap<String, RateSource>) {
        r.unitsPerUsd.forEach { (c, u) ->
            inv(u)?.let { usdPer[c] = it; source[c] = src }
        }
    }

    private fun inv(units: BigDecimal): BigDecimal? =
        if (units.signum() <= 0) null else BigDecimal.ONE.divide(units, MC)

    private class Fetched(
        val ecbLatest: Result<DatedRates>,
        val ecbSeries: Result<List<DatedRates>>,
        val fawazLatest: Result<DatedRates>,
        val fawazPrev: DatedRates?,
    )

    companion object {
        private val MC: MathContext = MathContext.DECIMAL64
        /** Stablecoins treated as exactly 1 USD (spec §5.1). */
        val PEGGED_TO_USD = setOf("USDT")
    }
}

/** runCatching that never swallows cancellation. */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.w("Ticker", "source failed: ${e.message}")
    Result.failure(e)
}
