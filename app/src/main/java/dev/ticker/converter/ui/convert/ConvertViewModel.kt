package dev.ticker.converter.ui.convert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.CameraHandoff
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.math.ExpressionEvaluator
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.core.model.SyncStatus
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.ui.components.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

data class ConvertRow(val currency: Currency, val amount: String, val changePct: Double?, val missing: Boolean)

data class ConvertUiState(
    val rows: List<ConvertRow> = emptyList(),
    val activeCode: String = "",
    val expressionDisplay: String = "",
    val status: SyncStatus = SyncStatus.Syncing,
    val editing: Boolean = false,
    val decimalLabel: Char = '.',
    val loaded: Boolean = false,
    val feeOn: Boolean = false,
    val feeBps: Int = 200,
)

class ConvertViewModel(
    private val rates: RatesRepository,
    private val settings: SettingsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
    handoff: Flow<CameraHandoff>,
    private val decimalSeparator: Char,
) : ViewModel() {

    /** In-memory mirror of settings so typing never waits for disk; every change is also persisted. */
    private val local = MutableStateFlow<Settings?>(null)
    private val editing = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)

    val state: StateFlow<ConvertUiState> =
        combine(local.filterNotNull(), rates.snapshot, rates.status, editing) { s, snap, status, edit ->
            buildState(s, snap, status, edit)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConvertUiState(decimalLabel = decimalSeparator))

    init {
        viewModelScope.launch {
            local.value = settings.settings.first()
            mutate { s ->
                when {
                    !s.initialized || s.favorites.isEmpty() -> {
                        val defaults = catalog.defaultFavorites(Locale.getDefault())
                        s.copy(favorites = defaults, activeCode = defaults.first(), initialized = true)
                    }
                    s.activeCode !in s.favorites -> s.copy(activeCode = s.favorites.first())
                    else -> s
                }
            }
        }
        viewModelScope.launch {
            // A handoff can arrive before settings have loaded (cold start from "Open in Ticker");
            // applying it then would be overwritten by the load, so wait for it.
            local.filterNotNull().first()
            handoff.collect { h -> receiveHandoff(h) }
        }
    }

    // ---- events -------------------------------------------------------------------------

    fun onToggleFee() = mutate { it.copy(feeOn = !it.feeOn) }

    fun onSetFee(bps: Int) = mutate { it.copy(feeBps = bps.coerceIn(0, Fee.MAX_BPS)) }

    fun onKey(k: Key) = mutate { s ->
        val e = s.expression
        s.copy(
            expression = when (k) {
                is Key.Digit -> ExpressionEvaluator.append(e, k.d)
                Key.Dot -> ExpressionEvaluator.append(e, '.')
                is Key.Op -> ExpressionEvaluator.append(e, k.op)
                Key.Backspace -> ExpressionEvaluator.backspace(e)
                Key.Clear -> ""
            },
        )
    }

    fun onRowTapped(code: String) = mutate { s ->
        if (code == s.activeCode) s else s.copy(activeCode = code, expression = expressionFor(code, s))
    }

    fun onToggleEdit() { editing.value = !editing.value }

    /** Moves [fromCode] to where [toCode] is. Keyed by code, so extra list items (banners) can't shift it. */
    fun onMove(fromCode: String, toCode: String) = mutate { s ->
        val from = s.favorites.indexOf(fromCode)
        val to = s.favorites.indexOf(toCode)
        if (from < 0 || to < 0 || from == to) s
        else s.copy(favorites = s.favorites.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun onRemove(code: String) = mutate { s ->
        if (s.favorites.size <= 1 || code !in s.favorites) return@mutate s
        val remaining = s.favorites - code
        if (code != s.activeCode) s.copy(favorites = remaining)
        else s.copy(favorites = remaining, activeCode = remaining.first(), expression = expressionFor(remaining.first(), s))
    }

    fun onAdd(code: String) = mutate { s ->
        if (code in s.favorites) s else s.copy(favorites = s.favorites + code)
    }

    fun onRefresh() {
        viewModelScope.launch { rates.refreshAll() }
    }

    fun setVisible(v: Boolean) { visible.value = v }

    // ---- internals ----------------------------------------------------------------------

    private fun receiveHandoff(h: CameraHandoff) {
        val currency = catalog.get(h.code) ?: return
        mutate { s ->
            s.copy(
                favorites = if (h.code in s.favorites) s.favorites else listOf(h.code) + s.favorites,
                activeCode = h.code,
                expression = keypadText(h.amount, currency),
            )
        }
    }

    /** Applies [f] to the in-memory settings now and persists the same transform. */
    private fun mutate(f: (Settings) -> Settings) {
        local.value?.let { local.value = f(it) }
        viewModelScope.launch { settings.update(f) }
    }

    private fun activeValue(s: Settings): BigDecimal? =
        if (s.expression.isBlank()) BigDecimal.ZERO else ExpressionEvaluator.evaluate(s.expression)

    /** The value [code]'s row currently shows, as keypad text ("" if unknown). */
    private fun expressionFor(code: String, s: Settings): String {
        val active = s.activeCode ?: return ""
        val value = activeValue(s) ?: return ""
        val snap = rates.snapshot.value
        val converted = if (code == active) value else snap?.let { Converter.convert(value, active, code, it) } ?: return ""
        val currency = catalog.get(code) ?: return ""
        return keypadText(converted, currency)
    }

    /** Amount as keypad text. Crypto keeps 12 significant digits so switching rows doesn't lose value. */
    private fun keypadText(v: BigDecimal, c: Currency): String {
        val plain = if (c.kind == CurrencyKind.CRYPTO) {
            v.round(MathContext(12)).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
        } else {
            formatter.plain(v, c)
        }
        return if (plain.startsWith("-")) "−" + plain.drop(1) else plain
    }

    private fun buildState(s: Settings, snap: dev.ticker.converter.core.model.RateSnapshot?, status: SyncStatus, edit: Boolean): ConvertUiState {
        val active = s.activeCode ?: s.favorites.firstOrNull().orEmpty()
        val value = activeValue(s)
        val hasOp = s.expression.drop(1).any { it in ExpressionEvaluator.OPS } ||
            (s.expression.isNotEmpty() && s.expression.first() in "+×÷")
        val rows = s.favorites.mapNotNull { code ->
            val c = catalog.get(code) ?: return@mapNotNull null
            val amount: BigDecimal? = when {
                value == null -> null
                code == active -> value
                snap == null -> null
                else -> Converter.convert(value, active, code, snap)?.let { if (s.feeOn) Fee.apply(it, s.feeBps) else it }
            }
            val echoTyped = code == active && !hasOp && s.expression.isNotBlank() && amount != null
            ConvertRow(
                currency = c,
                amount = if (echoTyped) formatter.formatTyped(s.expression) else amount?.let { formatter.format(it, c) } ?: "",
                changePct = if (code == active || snap == null) null else Converter.dailyChangePct(code, active, snap),
                missing = amount == null,
            )
        }
        return ConvertUiState(
            rows = rows,
            activeCode = active,
            expressionDisplay = if (hasOp) s.expression.replace('.', decimalSeparator) else "",
            status = status,
            editing = edit,
            decimalLabel = decimalSeparator,
            loaded = true,
            feeOn = s.feeOn,
            feeBps = s.feeBps,
        )
    }
}
