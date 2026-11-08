package dev.ticker.converter.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.CameraHandoff
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.core.model.CameraMode
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.ocr.OcrToken
import dev.ticker.converter.ocr.PriceCandidatePicker
import dev.ticker.converter.ocr.ReadingStabilizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

data class CameraUiState(
    val from: Currency,
    val to: Currency,
    val reading: BigDecimal? = null,
    val readingText: String? = null,
    val converted: String? = null,
    val hint: Boolean = true,
    /** PRICE mode only: the shutter locked the current reading. */
    val frozen: Boolean = false,
    val mode: CameraMode = CameraMode.PRICE,
    val torch: Boolean = false,
    /** "2%" while the card fee is on, else null. */
    val feeLabel: String? = null,
)

class CameraViewModel(
    private val rates: RatesRepository,
    private val settings: SettingsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
    private val sendHandoff: (CameraHandoff) -> Boolean,
) : ViewModel() {

    private data class Inner(
        val from: Currency,
        val to: Currency,
        val reading: BigDecimal? = null,
        val frozen: Boolean = false,
        val torch: Boolean = false,
        val mode: CameraMode = CameraMode.PRICE,
    )

    private val inner = MutableStateFlow<Inner?>(null)
    private val stabilizer = ReadingStabilizer()

    val state: StateFlow<CameraUiState> = combine(inner.filterNotNull(), rates.snapshot, settings.settings) { i, snap, s -> render(i, snap, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), placeholder())

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            val localFiat = catalog.defaultFavorites(Locale.getDefault()).firstOrNull()?.takeIf(catalog::isFiat)
            val from = listOfNotNull(s.cameraFrom, localFiat, "USD").firstNotNullOf(catalog::get)
            val to = (listOfNotNull(s.cameraTo) + s.favorites + listOf("EUR", "USD")).filter { it != from.code }.firstNotNullOf(catalog::get)
            inner.value = Inner(from, to, mode = s.cameraMode)
        }
    }

    fun onTokens(tokens: List<OcrToken>) = update { i ->
        if (i.frozen || i.mode != CameraMode.PRICE) i else i.copy(reading = stabilizer.offer(PriceCandidatePicker.pick(tokens)))
    }

    /**
     * The shutter. PRICE: locks the reading so the phone can be lowered; pressed again it clears it
     * and reads afresh. RECEIPT: returns true and the screen takes the photo.
     */
    fun onShutter(): Boolean {
        val i = inner.value ?: return false
        if (i.mode == CameraMode.RECEIPT) return true
        when {
            i.frozen -> { stabilizer.reset(); inner.value = i.copy(frozen = false, reading = null) }
            i.reading != null -> inner.value = i.copy(frozen = true)
        }
        return false
    }

    fun onSetMode(mode: CameraMode) = update { i ->
        if (i.mode == mode) return@update i
        stabilizer.reset()
        viewModelScope.launch { settings.update { it.copy(cameraMode = mode) } }
        i.copy(mode = mode, frozen = false, reading = null)
    }
    fun onToggleTorch() = update { it.copy(torch = !it.torch) }

    fun onSwap() = update { i -> i.copy(from = i.to, to = i.from).also(::persistPair) }

    fun onPickFrom(code: String) = update { i ->
        val c = catalog.get(code) ?: return@update i
        val to = if (i.to.code == c.code) i.from else i.to
        i.copy(from = c, to = to).also(::persistPair)
    }

    fun onPickTo(code: String) = update { i ->
        val c = catalog.get(code) ?: return@update i
        (if (c.code == i.from.code) i.copy(from = i.to, to = c) else i.copy(to = c)).also(::persistPair)
    }

    /** Sends the current reading to Convert; true if there was one (the screen then switches tab). */
    fun onResultTapped(): Boolean {
        val i = inner.value ?: return false
        val r = i.reading ?: return false
        return sendHandoff(CameraHandoff(i.from.code, r))
    }

    private fun update(f: (Inner) -> Inner) { inner.value?.let { inner.value = f(it) } }

    /** Both sides are saved, so the pair is the same after the app restarts (an update restarts it). */
    private fun persistPair(i: Inner) {
        viewModelScope.launch { settings.update { it.copy(cameraFrom = i.from.code, cameraTo = i.to.code) } }
    }

    private fun render(i: Inner, snap: RateSnapshot?, s: Settings): CameraUiState {
        fun conv(v: BigDecimal) = snap?.let { Converter.convert(v, i.from.code, i.to.code, it) }
            ?.let { if (s.feeOn) Fee.apply(it, s.feeBps) else it }
            ?.let { formatter.format(it, i.to) }
        return CameraUiState(
            from = i.from,
            to = i.to,
            reading = i.reading,
            readingText = i.reading?.let { "${asRead(it, i.from)} ${i.from.code} · tap to open in Convert" },
            converted = i.reading?.let(::conv),
            hint = i.reading == null,
            frozen = i.frozen,
            mode = i.mode,
            torch = i.torch,
            feeLabel = if (s.feeOn) Fee.label(s.feeBps) else null,
        )
    }

    /** The number as it appeared on the tag: "1 290", "12.99". */
    private fun asRead(v: BigDecimal, c: Currency): String {
        val scale = v.stripTrailingZeros().scale().coerceIn(0, 8)
        return formatter.format(v, c.copy(kind = CurrencyKind.FIAT, decimals = scale))
    }

    private fun placeholder(): CameraUiState {
        val usd = catalog.get("USD") ?: Currency("USD", "US Dollar", CurrencyKind.FIAT, "🇺🇸", 2)
        val eur = catalog.get("EUR") ?: usd
        return CameraUiState(usd, eur)
    }
}
