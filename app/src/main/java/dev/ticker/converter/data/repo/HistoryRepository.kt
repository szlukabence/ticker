package dev.ticker.converter.data.repo

import dev.ticker.converter.core.math.SeriesMath
import dev.ticker.converter.core.model.ChartRange
import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.CachedSeries
import dev.ticker.converter.data.local.HistoryStore
import dev.ticker.converter.data.remote.EcbSource
import dev.ticker.converter.data.remote.FawazSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class PairSeries(val points: List<PricePoint>, val stale: Boolean)

/** Extracted so ChartsViewModel can be tested with a fake. */
interface PairHistory {
    fun availableRanges(base: String, quote: String, s: RateSnapshot): Set<ChartRange>
    suspend fun pairSeries(base: String, quote: String, range: ChartRange, s: RateSnapshot): PairSeries
}

/** Pair history for the Charts tab (spec §6.2). */
class HistoryRepository(
    private val ecb: EcbSource,
    private val fawaz: FawazSource,
    private val store: HistoryStore,
    @Suppress("unused") private val catalog: CurrencyCatalog,
    private val clock: Clock,
) : PairHistory {

    private enum class Kind { USD, ECB, FAWAZ }

    private fun kind(code: String, s: RateSnapshot) = when {
        code == "USD" || code in RatesRepository.PEGGED_TO_USD -> Kind.USD
        s.sourceFor[code] == RateSource.FRANKFURTER -> Kind.ECB
        else -> Kind.FAWAZ
    }

    override fun availableRanges(base: String, quote: String, s: RateSnapshot): Set<ChartRange> {
        val kinds = listOf(kind(base, s), kind(quote, s))
        return ChartRange.entries.filter { r ->
            when (r) {
                // Every source is daily, so there is nothing to draw within a day.
                ChartRange.D1 -> false
                ChartRange.Y5, ChartRange.MAX -> Kind.FAWAZ !in kinds
                else -> true
            }
        }.toSet()
    }

    override suspend fun pairSeries(base: String, quote: String, range: ChartRange, s: RateSnapshot): PairSeries {
        val b = usdSeries(base, range, s)
        val q = usdSeries(quote, range, s)
        return PairSeries(SeriesMath.thin(SeriesMath.divide(b.points, q.points), MAX_POINTS), b.stale || q.stale)
    }

    private suspend fun usdSeries(code: String, range: ChartRange, s: RateSnapshot): PairSeries {
        val now = clock.instant()
        val kind = kind(code, s)
        if (kind == Kind.USD) return PairSeries(flat(BigDecimal.ONE, range, now), false)
        if (range == ChartRange.D1) {
            val latest = s.usdPer[code] ?: throw IOException("No rate for $code")
            return PairSeries(flat(latest, range, now), false)
        }

        val key = "$code|${range.name}"
        val cached = store.load(key)
        if (cached != null && Duration.between(cached.fetchedAt, now) < ttl(range)) {
            return PairSeries(cached.points, false)
        }
        return try {
            val points = fetch(code, kind, range, now)
            if (points.isEmpty()) throw IOException("Empty history for $code")
            store.save(key, CachedSeries(points, now))
            PairSeries(points, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.let { PairSeries(it.points, true) } ?: throw (e as? IOException ?: IOException(e))
        }
    }

    private suspend fun fetch(code: String, kind: Kind, range: ChartRange, now: Instant): List<PricePoint> {
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        return when (kind) {
            Kind.ECB -> {
                val start = if (range == ChartRange.MAX) ECB_MAX_START else today.minusDays(span(range).toDays())
                ecb.series(start, today, listOf(code)).mapNotNull { r ->
                    inv(r.unitsPerUsd[code])?.let { PricePoint(r.date.atStartOfDay().toInstant(ZoneOffset.UTC), it) }
                }
            }
            Kind.FAWAZ -> sampledFawaz(code, range, today)
            Kind.USD -> flat(BigDecimal.ONE, range, now)
        }
    }

    private suspend fun sampledFawaz(code: String, range: ChartRange, today: LocalDate): List<PricePoint> {
        val wantedStart = today.minusDays(span(range).toDays())
        val start = maxOf(wantedStart, FAWAZ_ARCHIVE_START)
        val days = ChronoUnit.DAYS.between(start, today).coerceAtLeast(0)
        val n = minOf(MAX_FAWAZ_REQUESTS.toLong(), days + 1).toInt()
        val dates = (0 until n).map { i ->
            if (n == 1) today else start.plusDays(Math.round(i.toDouble() * days / (n - 1)))
        }.distinct()
        val points = coroutineScope {
            dates.map { d ->
                async {
                    attempt { fawaz.on(d) }.getOrNull()?.unitsPerUsd?.get(code)?.let { inv(it) }
                        ?.let { PricePoint(d.atStartOfDay().toInstant(ZoneOffset.UTC), it) }
                }
            }.awaitAll().filterNotNull()
        }
        if (points.isEmpty()) throw IOException("No fawazahmed0 history for $code")
        return points.sortedBy { it.time }
    }

    private fun flat(value: BigDecimal, range: ChartRange, now: Instant) =
        listOf(PricePoint(now.minus(span(range)), value), PricePoint(now, value))

    private fun inv(units: BigDecimal?): BigDecimal? =
        units?.takeIf { it.signum() > 0 }?.let { BigDecimal.ONE.divide(it, MathContext.DECIMAL64) }

    companion object {
        const val MAX_POINTS = 500
        const val MAX_FAWAZ_REQUESTS = 30
        val FAWAZ_ARCHIVE_START: LocalDate = LocalDate.parse("2024-03-02")
        val ECB_MAX_START: LocalDate = LocalDate.parse("1999-01-04")

        fun span(r: ChartRange): Duration = when (r) {
            ChartRange.D1 -> Duration.ofDays(1)
            ChartRange.W1 -> Duration.ofDays(7)
            ChartRange.M1 -> Duration.ofDays(30)
            ChartRange.M3 -> Duration.ofDays(90)
            ChartRange.Y1 -> Duration.ofDays(365)
            ChartRange.Y5 -> Duration.ofDays(1826)
            ChartRange.MAX -> Duration.ofDays(365L * 30)
        }

        fun ttl(r: ChartRange): Duration = when (r) {
            ChartRange.D1 -> Duration.ofMinutes(5)
            ChartRange.W1, ChartRange.M1 -> Duration.ofHours(1)
            else -> Duration.ofHours(24)
        }
    }
}
