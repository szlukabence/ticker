package dev.ticker.converter.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Schedules the 30-minute background refresh only while at least one widget is on a home screen. */
class TickerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TickerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RatesRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RatesRefreshWorker.cancel(context)
    }
}
