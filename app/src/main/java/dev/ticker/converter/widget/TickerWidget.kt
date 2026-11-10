package dev.ticker.converter.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.ticker.converter.MainActivity
import dev.ticker.converter.TickerApp
import dev.ticker.converter.ui.theme.DarkPalette
import java.time.Instant
import java.time.ZoneId

/** Home-screen widget: 1 of the top favourite in the other favourites (extras spec §3). */
class TickerWidget : GlanceAppWidget() {
    // Narrow and wide variants of each height: a bucket must fit both dimensions, so 5-column launchers'
    // 2×2 / 2×3 cells (narrower than 180 dp) still get 4 / 6 rows. Change arrows need the wide variants.
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, TALL, WIDE_SMALL, WIDE_MEDIUM, WIDE_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TickerApp).container
        container.rates.loadCached() // cache only: the widget never fetches while rendering
        provideContent {
            val settings by container.settings.settings.collectAsState(null)
            val snap by container.rates.snapshot.collectAsState()
            val ui = settings?.let {
                WidgetModel.build(it, snap, Instant.now(), ZoneId.systemDefault(), container.catalog, container.formatter)
            } ?: WidgetUi.NoRates
            Content(ui)
        }
    }

    @Composable
    private fun Content(ui: WidgetUi) {
        val size = LocalSize.current
        val maxRows = when {
            size.height >= TALL.height -> 6
            size.height >= MEDIUM.height -> 4
            else -> 2
        }
        Column(
            GlanceModifier.fillMaxSize().background(Color.Black).cornerRadius(22.dp)
                .clickable(actionStartActivity<MainActivity>()).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when (ui) {
                WidgetUi.NoRates -> Message("Open Ticker to load rates")
                WidgetUi.NeedFavourites -> Message("Add favourites in Ticker")
                is WidgetUi.Rates -> {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${ui.baseGlyph} 1 ${ui.baseCode}", style = TextStyle(color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                        Spacer(GlanceModifier.defaultWeight())
                        Text(ui.updated, style = TextStyle(color = if (ui.stale) WARNING else DIM, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
                    }
                    ui.feeLabel?.let { Text("incl. $it fee", style = TextStyle(color = DIM, fontSize = 10.sp, fontFamily = FontFamily.Monospace)) }
                    Spacer(GlanceModifier.height(4.dp))
                    ui.rows.take(maxRows).forEach { row ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.code, style = TextStyle(color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                row.amount,
                                GlanceModifier.defaultWeight(),
                                style = TextStyle(color = TEXT, fontSize = 15.sp, fontFamily = FontFamily.Monospace, textAlign = androidx.glance.text.TextAlign.End),
                                maxLines = 1,
                            )
                            if (size.width >= WIDE_SMALL.width) {
                                Spacer(GlanceModifier.width(8.dp))
                                Text(
                                    row.change ?: "",
                                    style = TextStyle(color = if (row.down) NEGATIVE else ACCENT, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TICKER", style = TextStyle(color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Text(text, style = TextStyle(color = DIM, fontSize = 12.sp, fontFamily = FontFamily.Monospace))
        }
    }

    private companion object {
        val SMALL = DpSize(110.dp, 50.dp)
        val MEDIUM = DpSize(110.dp, 110.dp)
        val TALL = DpSize(110.dp, 180.dp)
        val WIDE_SMALL = DpSize(180.dp, 50.dp)
        val WIDE_MEDIUM = DpSize(180.dp, 110.dp)
        val WIDE_TALL = DpSize(180.dp, 180.dp)
        // The widget keeps the dark Neon look whatever the app theme is.
        val ACCENT = ColorProvider(DarkPalette.accent)
        val TEXT = ColorProvider(DarkPalette.text)
        val DIM = ColorProvider(DarkPalette.dim)
        val NEGATIVE = ColorProvider(DarkPalette.negative)
        val WARNING = ColorProvider(DarkPalette.warning)
    }
}
