package dev.ticker.converter.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.SeriesMath
import dev.ticker.converter.core.model.ChartRange
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.core.model.SavedPair
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.repo.PairHistory
import dev.ticker.converter.data.repo.RatesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

data class SavedPairRow(val pair: SavedPair, val label: String, val changePct: Double?)

data class ChartsUiState(
    val base: Currency,
    val quote: Currency,
    val range: ChartRange,
    val available: Set<ChartRange>,
    val points: List<PricePoint> = emptyList(),
    val current: String? = null,
    val changeText: String? = null,
    val positive: Boolean = true,
    val high: String? = null,
    val low: String? = null,
    val avg: String? = null,
    val loading: Boolean = true,
    val stale: Boolean = false,
    val error: Boolean = false,
    val isSaved: Boolean = false,
    val saved: List<SavedPairRow> = emptyList(),
)

class ChartsViewModel(
    private val history: PairHistory,
    private val rates: RatesRepository,
    private val settings: SettingsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow<ChartsUiState?>(null)
    val state: StateFlow<ChartsUiState> = _state.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), placeholder())

    private var savedPairs: List<SavedPair> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            savedPairs = s.savedPairs
            val (b, q) = defaultPair(s)
            select(b, q, ChartRange.M1, persist = false)
        }
        // First launch offline: once rates arrive, retry a chart that had nothing to show.
        viewModelScope.launch {
            rates.snapshot.filterNotNull().collect {
                val c = current()
                if (c != null && c.error && c.points.isEmpty()) onRetry()
            }
        }
    }

    fun onSwap() = current()?.let { select(it.quote.code, it.base.code, it.range) }
    fun onPickBase(code: String) = current()?.let { select(code, it.quote.code, it.range) }
    fun onPickQuote(code: String) = current()?.let { select(it.base.code, code, it.range) }
    fun onRange(r: ChartRange) = current()?.let { select(it.base.code, it.quote.code, r, persist = false) }
    fun onSelectSaved(p: SavedPair) = current()?.let { select(p.base, p.quote, it.range) }
    fun onRetry() = current()?.let { select(it.base.code, it.quote.code, it.range, persist = false) }

    fun onToggleSave() {
        val s = current() ?: return
        val pair = SavedPair(s.base.code, s.quote.code)
        savedPairs = if (pair in savedPairs) savedPairs - pair else savedPairs + pair
        val updated = savedPairs
        viewModelScope.launch { settings.update { it.copy(savedPairs = updated) } }
        _state.value = s.copy(isSaved = pair in savedPairs)
        refreshSaved(s.range)
    }

    private fun current() = _state.value

    private fun select(baseCode: String, quoteCode: String, range: ChartRange, persist: Boolean = true) {
        val base = catalog.get(baseCode) ?: return
        val quote = catalog.get(quoteCode) ?: return
        if (persist) viewModelScope.launch { settings.update { it.copy(chartBase = base.code, chartQuote = quote.code) } }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val snap = rates.snapshot.value
            if (snap == null) {
                _state.value = ChartsUiState(
                    base = base, quote = quote, range = range, available = ChartRange.entries.toSet(),
                    loading = false, error = true,
                    isSaved = SavedPair(base.code, quote.code) in savedPairs, saved = current()?.saved.orEmpty(),
                )
                return@launch
            }
            val available = history.availableRanges(base.code, quote.code, snap)
            val r = if (range in available) range else ChartRange.M1
            val latest = Converter.convert(BigDecimal.ONE, base.code, quote.code, snap)
            val shell = ChartsUiState(
                base = base, quote = quote, range = r, available = available,
                current = latest?.let { formatter.format(it, quote) },
                loading = true,
                isSaved = SavedPair(base.code, quote.code) in savedPairs,
                saved = current()?.saved.orEmpty(),
            )
            _state.value = shell
            _state.value = try {
                val series = history.pairSeries(base.code, quote.code, r, snap)
                val stats = SeriesMath.stats(series.points)
                shell.copy(
                    points = series.points,
                    loading = false,
                    stale = series.stale,
                    error = series.points.isEmpty(),
                    high = stats?.high?.let { formatter.format(it, quote) },
                    low = stats?.low?.let { formatter.format(it, quote) },
                    avg = stats?.avg?.let { formatter.format(it, quote) },
                    positive = (stats?.change?.signum() ?: 0) >= 0,
                    changeText = stats?.let { changeText(it, quote, r) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                shell.copy(loading = false, error = true)
            }
            refreshSaved(r)
        }
    }

    private fun refreshSaved(range: ChartRange) {
        val pairs = savedPairs
        viewModelScope.launch {
            val snap = rates.snapshot.value ?: return@launch
            val rows = pairs.map { p ->
                val pct = runCatching { SeriesMath.stats(history.pairSeries(p.base, p.quote, range, snap).points)?.changePct }
                    .getOrNull()
                SavedPairRow(p, "${p.base}/${p.quote}", pct)
            }
            _state.value = _state.value?.copy(saved = rows)
        }
    }

    private fun changeText(s: SeriesMath.Stats, quote: Currency, r: ChartRange): String {
        val up = s.change.signum() >= 0
        val abs = formatter.format(s.change.abs(), quote)
        val pct = String.format(Locale.ROOT, "%.2f", kotlin.math.abs(s.changePct))
        return if (up) "▲ +$abs (+$pct%) · ${r.label}" else "▼ −$abs (−$pct%) · ${r.label}"
    }

    private fun defaultPair(s: Settings): Pair<String, String> {
        val b = s.chartBase?.takeIf { catalog.get(it) != null }
        val q = s.chartQuote?.takeIf { catalog.get(it) != null }
        if (b != null && q != null) return b to q
        val favs = s.favorites.filter { catalog.get(it) != null }
        return when {
            favs.size >= 2 -> favs[0] to favs[1]
            favs.size == 1 -> favs[0] to (if (favs[0] == "USD") "EUR" else "USD")
            else -> "EUR" to "USD"
        }
    }

    private fun placeholder(): ChartsUiState {
        val usd = catalog.get("USD") ?: Currency("USD", "US Dollar", dev.ticker.converter.core.model.CurrencyKind.FIAT, "🇺🇸", 2)
        return ChartsUiState(usd, usd, ChartRange.M1, emptySet())
    }
}
