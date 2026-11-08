package dev.ticker.converter.ui.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.CameraHandoff
import dev.ticker.converter.HandoffQueue
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.math.Receipt
import dev.ticker.converter.core.math.ReceiptParser
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.repo.RatesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal

data class ReceiptRow(val name: String, val original: String, val converted: String, val included: Boolean)

data class ReceiptUiState(
    val reading: Boolean = true,
    val failed: Boolean = false,
    val from: Currency? = null,
    val to: Currency? = null,
    val rows: List<ReceiptRow> = emptyList(),
    val sumOriginal: String = "",
    val sumConverted: String = "",
    val printedTotal: String? = null,
    val mismatch: Boolean = false,
    val feeLabel: String? = null,
)

/** Receipt screen: every line and the total converted; tap a line to leave it out (extras spec §5). */
class ReceiptViewModel(
    private val rates: RatesRepository,
    settings: SettingsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
    private val handoff: HandoffQueue,
    from: String,
    to: String,
) : ViewModel() {

    private data class Inner(
        val from: String,
        val to: String,
        val receipt: Receipt? = null,
        val excluded: Set<Int> = emptySet(),
        val failed: Boolean = false,
    )

    private val inner = MutableStateFlow(Inner(from, to))

    val state: StateFlow<ReceiptUiState> = combine(inner, rates.snapshot, settings.settings) { i, snap, s -> render(i, snap, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReceiptUiState())

    /** Begins a new receipt: clears the previous one and takes the Camera's current currencies. */
    fun start(from: String, to: String) { inner.value = Inner(from, to) }

    fun onLines(lines: List<String>) {
        val r = ReceiptParser.parse(lines)
        inner.value = inner.value.copy(receipt = r, excluded = emptySet(), failed = r.items.isEmpty())
    }

    fun onFailed() { inner.value = inner.value.copy(receipt = Receipt(emptyList(), null), failed = true) }

    fun onToggle(index: Int) {
        val ex = inner.value.excluded
        inner.value = inner.value.copy(excluded = if (index in ex) ex - index else ex + index)
    }

    fun onPickFrom(code: String) {
        val i = inner.value
        inner.value = if (code == i.to) i.copy(from = code, to = i.from) else i.copy(from = code)
    }

    fun onPickTo(code: String) {
        val i = inner.value
        inner.value = if (code == i.from) i.copy(to = code, from = i.to) else i.copy(to = code)
    }

    /** Sends the sum of the included lines to Convert; false when there is nothing to send. */
    fun openTotal(): Boolean {
        val i = inner.value
        val sum = includedSum(i) ?: return false
        return handoff.send(CameraHandoff(i.from, sum))
    }

    private fun includedSum(i: Inner): BigDecimal? = i.receipt?.items
        ?.filterIndexed { idx, _ -> idx !in i.excluded }
        ?.fold(BigDecimal.ZERO) { a, b -> a + b.amount }
        ?.takeIf { it.signum() > 0 }

    private fun render(i: Inner, snap: RateSnapshot?, s: Settings): ReceiptUiState {
        val from = catalog.get(i.from)
        val to = catalog.get(i.to)
        val r = i.receipt ?: return ReceiptUiState(reading = !i.failed, failed = i.failed, from = from, to = to)
        fun orig(v: BigDecimal) = from?.let { formatter.format(v, it) } ?: v.toPlainString()
        fun conv(v: BigDecimal): String {
            if (from == null || to == null || snap == null) return "—"
            val c = Converter.convert(v, from.code, to.code, snap)?.let { if (s.feeOn) Fee.apply(it, s.feeBps) else it } ?: return "—"
            return formatter.format(c, to)
        }
        val sum = r.items.filterIndexed { idx, _ -> idx !in i.excluded }.fold(BigDecimal.ZERO) { a, b -> a + b.amount }
        val all = r.items.fold(BigDecimal.ZERO) { a, b -> a + b.amount }
        return ReceiptUiState(
            reading = false,
            failed = i.failed,
            from = from,
            to = to,
            rows = r.items.mapIndexed { idx, it -> ReceiptRow(it.name, orig(it.amount), conv(it.amount), idx !in i.excluded) },
            sumOriginal = orig(sum),
            sumConverted = conv(sum),
            printedTotal = r.printedTotal?.let(::orig),
            mismatch = r.printedTotal != null && (all - r.printedTotal).abs() > TOLERANCE,
            feeLabel = if (s.feeOn) Fee.label(s.feeBps) else null,
        )
    }

    private companion object { val TOLERANCE = BigDecimal("0.01") }
}
