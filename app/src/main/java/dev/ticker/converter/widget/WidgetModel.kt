package dev.ticker.converter.widget

import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.ui.components.changeArrowText
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WidgetRow(val code: String, val glyph: String, val amount: String, val change: String?, val down: Boolean)

/** What the home-screen widget shows (extras spec §3). */
sealed interface WidgetUi {
    data object NoRates : WidgetUi
    data object NeedFavourites : WidgetUi
    data class Rates(
        val baseCode: String,
        val baseGlyph: String,
        val rows: List<WidgetRow>,
        val updated: String,
        val stale: Boolean,
        val feeLabel: String?,
    ) : WidgetUi
}

object WidgetModel {
    private val STALE_AFTER: Duration = Duration.ofHours(2)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Everything the widget shows depends on; equal keys mean no redraw is needed. */
    fun redrawKey(s: Settings, snap: RateSnapshot?): Any = listOf(s.favorites, s.feeOn, s.feeBps, snap?.fetchedAt)

    /** 1 unit of the first favourite in each other favourite; all rows, the widget trims to its size. */
    fun build(
        s: Settings,
        snap: RateSnapshot?,
        now: Instant,
        zone: ZoneId,
        catalog: CurrencyCatalog,
        formatter: AmountFormatter,
    ): WidgetUi {
        if (snap == null) return WidgetUi.NoRates
        val favs = s.favorites.mapNotNull(catalog::get)
        if (favs.size < 2) return WidgetUi.NeedFavourites
        val base = favs.first()
        val rows = favs.drop(1).map { c ->
            val v = Converter.convert(BigDecimal.ONE, base.code, c.code, snap)?.let { if (s.feeOn) Fee.apply(it, s.feeBps) else it }
            val pct = Converter.dailyChangePct(c.code, base.code, snap)
            WidgetRow(
                code = c.code,
                glyph = c.glyph,
                amount = v?.let { formatter.format(it, c) } ?: "—",
                change = pct?.let(::changeArrowText),
                down = pct != null && pct < 0,
            )
        }
        return WidgetUi.Rates(
            baseCode = base.code,
            baseGlyph = base.glyph,
            rows = rows,
            updated = TIME.withZone(zone).format(snap.fetchedAt),
            stale = Duration.between(snap.fetchedAt, now) > STALE_AFTER,
            feeLabel = if (s.feeOn) Fee.label(s.feeBps) else null,
        )
    }
}
