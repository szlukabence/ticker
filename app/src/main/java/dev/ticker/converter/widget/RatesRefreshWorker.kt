package dev.ticker.converter.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.ticker.converter.TickerApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Fetches fresh rates in the background and redraws the widgets (extras spec §3). */
class RatesRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as TickerApp).container
        container.rates.loadCached()
        container.rates.refreshAll()
        TickerWidget().updateAll(applicationContext)
        return Result.success() // a failed fetch keeps the cached rates; the next period tries again
    }

    companion object {
        private const val NAME = "rates-refresh"

        fun schedule(context: Context) {
            // No network constraint: offline, the fetch fails fast but the redraw still turns the
            // "updated" time amber once the rates are over 2 hours old.
            val request = PeriodicWorkRequestBuilder<RatesRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
