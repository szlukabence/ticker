package dev.ticker.converter

import android.app.Application
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.DataStoreSettingsRepository
import dev.ticker.converter.data.local.RoomHistoryStore
import dev.ticker.converter.data.local.RoomSnapshotStore
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.local.TickerDatabase
import dev.ticker.converter.data.remote.HttpFawazSource
import dev.ticker.converter.data.remote.Network
import dev.ticker.converter.data.remote.RetrofitEcbSource
import dev.ticker.converter.data.repo.HistoryRepository
import dev.ticker.converter.data.repo.RatesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.math.BigDecimal
import java.time.Clock
import java.util.Locale

/** A converted amount handed from the Camera tab to Convert. */
data class CameraHandoff(val code: String, val amount: BigDecimal)

/** Manual dependency container (one per process). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = TickerDatabase.create(app)
    private val clock = Clock.systemUTC()

    private val ecb = RetrofitEcbSource(Network.frankfurter())
    private val fawaz = HttpFawazSource(Network.okHttp(), Network.json())

    val catalog: CurrencyCatalog = CurrencyCatalog.fromAssets(app)
    val settings: SettingsRepository = DataStoreSettingsRepository(app)
    val rates = RatesRepository(ecb, fawaz, RoomSnapshotStore(db.snapshotDao()), catalog, clock)
    val history = HistoryRepository(ecb, fawaz, RoomHistoryStore(db.historyDao()), catalog, clock)
    val formatter: AmountFormatter = AmountFormatter.forLocale(Locale.getDefault())
    val decimalSeparator: Char = java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
    val handoff = HandoffQueue()
}
