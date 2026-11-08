package dev.ticker.converter.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.CameraHandoff
import dev.ticker.converter.HandoffQueue
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.math.SelectionParser
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.core.model.SyncStatus
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.repo.RatesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class SelectionRow(val currency: Currency, val amount: String)

data class SelectionUiState(
    val amount: BigDecimal? = null,
    val amountText: String = "",
    val from: Currency? = null,
    val rows: List<SelectionRow> = emptyList(),
    val feeLabel: String? = null,
    val status: SyncStatus = SyncStatus.Syncing,
)

/** Backs the "Convert" card shown over other apps for selected text (extras spec §4). */
class SelectionViewModel(
    text: String,
    private val rates: RatesRepository,
    settings: SettingsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
    private val handoff: HandoffQueue,
    refreshOnStart: Boolean = true,
) : ViewModel() {

    private val parsed = SelectionParser.parse(text) { catalog.get(it) != null }
    private val picked = MutableStateFlow<String?>(null)

    val state: StateFlow<SelectionUiState> =
        combine(settings.settings, rates.snapshot, rates.status, picked) { s, snap, status, p -> render(s, snap, status, p) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SelectionUiState())

    init {
        viewModelScope.launch {
            rates.loadCached()
            if (refreshOnStart) {
                rates.refreshAll()
            }
        }
    }

    fun onPickFrom(code: String) { picked.value = code }

    /** Hands the amount to Convert; false when the selection had no amount. */
    fun openInTicker(): Boolean {
        val s = state.value
        val amount = s.amount ?: return false
        val from = s.from ?: return false
        return handoff.send(CameraHandoff(from.code, amount))
    }

    private fun render(s: Settings, snap: RateSnapshot?, status: SyncStatus, picked: String?): SelectionUiState {
        val from = listOfNotNull(picked, parsed.currencyCode, s.cameraFrom, s.favorites.firstOrNull(), "USD")
            .firstNotNullOfOrNull(catalog::get)
        val amount = parsed.amount
        val rows = if (amount == null || from == null) emptyList() else s.favorites
            .filter { it != from.code }
            .mapNotNull(catalog::get)
            .take(MAX_ROWS)
            .map { c ->
                val v = snap?.let { Converter.convert(amount, from.code, c.code, it) }
                    ?.let { if (s.feeOn) Fee.apply(it, s.feeBps) else it }
                SelectionRow(c, v?.let { formatter.format(it, c) } ?: "—")
            }
        return SelectionUiState(
            amount = amount,
            amountText = amount?.let { asRead(it, from) }.orEmpty(),
            from = from,
            rows = rows,
            feeLabel = if (s.feeOn) Fee.label(s.feeBps) else null,
            status = status,
        )
    }

    /** The number with as many decimals as it was written with: "1 290", "12.99". */
    private fun asRead(v: BigDecimal, c: Currency?): String {
        val scale = v.stripTrailingZeros().scale().coerceIn(0, 8)
        val base = c ?: Currency("XXX", "", CurrencyKind.FIAT, "", scale)
        return formatter.format(v, base.copy(kind = CurrencyKind.FIAT, decimals = scale))
    }

    private companion object { const val MAX_ROWS = 6 }
}
