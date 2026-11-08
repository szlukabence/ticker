package dev.ticker.converter

import androidx.glance.appwidget.updateAll
import dev.ticker.converter.widget.TickerWidget
import dev.ticker.converter.widget.WidgetModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class TickerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep home-screen widgets in step with the app: fee, favourites and fresh rates redraw them
        // (Glance sessions end shortly after an update, so live collection inside the widget isn't enough).
        container.appScope.launch {
            combine(container.settings.settings, container.rates.snapshot) { s, snap -> WidgetModel.redrawKey(s, snap) }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { runCatching { TickerWidget().updateAll(this@TickerApp) } }
        }
        // Fresh data every time the app comes to the foreground (spec §5.3).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.appScope.launch {
                    container.rates.loadCached()
                    container.rates.refreshAll()
                }
            }
        })
    }
}
