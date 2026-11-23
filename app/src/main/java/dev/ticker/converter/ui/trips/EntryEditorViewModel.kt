package dev.ticker.converter.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.R
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.core.systemLocale
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.EntryFactory
import dev.ticker.converter.trips.EntryResult
import dev.ticker.converter.trips.EntrySource
import dev.ticker.converter.trips.RateOrigin
import dev.ticker.converter.trips.Reason
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.trips.TripsRepository
import dev.ticker.converter.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class EntryEditorState(
    val isNew: Boolean,
    val amountText: String = "",
    val code: String = "",
    val payment: Payment = Payment.CARD,
    val note: String = "",
    val at: Instant,
    val bankText: String = "",
    /** "17 083 HUF": what the saved rate and fee made of it. Empty for a new entry. */
    val estimateText: String = "",
    /** "1 EUR = 396.02 HUF". */
    val rateText: String = "",
    /** "ECB · 18 Nov 09:41". */
    val rateFromText: UiText? = null,
    /** "+37 HUF": the bank's figure against the estimate. */
    val diffText: String? = null,
    val error: UiText? = null,
    val confirmDelete: Boolean = false,
)

/** A new manual entry when [editing] is null, else the correction screen for one saved entry. */
class EntryEditorViewModel(
    private val trips: TripsRepository,
    private val rates: RatesRepository,
    private val settings: SettingsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
    private val tripId: String,
    private val editing: Entry?,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        EntryEditorState(
            isNew = editing == null,
            amountText = editing?.amount?.toPlainString().orEmpty(),
            code = editing?.code.orEmpty(),
            payment = editing?.payment ?: Payment.CARD,
            note = editing?.note.orEmpty(),
            at = editing?.at ?: clock.instant(),
            bankText = editing?.actualHome?.toPlainString().orEmpty(),
        ),
    )
    val state: StateFlow<EntryEditorState> = _state.asStateFlow()

    private var homeCode: String = ""

    init {
        viewModelScope.launch {
            val trip = trips.trips.first().firstOrNull { it.id == tripId } ?: return@launch
            homeCode = trip.homeCode
            val s = settings.settings.first()
            _state.update { st ->
                var n = st
                if (editing == null) n = n.copy(code = s.activeCode ?: trip.homeCode, payment = s.lastPayment)
                if (editing != null) n = n.copy(
                    estimateText = money(editing.homeAmount, home()) + " " + homeCode,
                    rateText = "1 ${editing.code} = ${money(editing.rateToHome, home())} $homeCode",
                    rateFromText = rateFrom(editing),
                )
                n.copy(diffText = diff(n.bankText))
            }
        }
    }

    fun onAmount(v: String) = _state.update { it.copy(amountText = v, error = null) }
    fun onCode(code: String) = _state.update { if (catalog.get(code) == null) it else it.copy(code = code, error = null) }
    fun onPayment(p: Payment) = _state.update { it.copy(payment = p) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }
    fun onDate(d: LocalDate) = _state.update { st ->
        val time = st.at.atZone(zone).toLocalTime()
        st.copy(at = d.atTime(time).atZone(zone).toInstant())
    }
    fun onBank(v: String) = _state.update { it.copy(bankText = v, error = null, diffText = diff(v)) }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    suspend fun delete() {
        if (!_state.value.confirmDelete) return
        editing?.let { trips.deleteEntry(it.id) }
    }

    /** True when saved. Otherwise [EntryEditorState.error] says why. */
    suspend fun save(): Boolean {
        val s = _state.value
        val actual = if (s.bankText.isBlank()) null else parseAmount(s.bankText) ?: return fail(R.string.entry_error_bank)
        val note = s.note.trim().ifEmpty { null }
        val saved: Entry = if (editing != null) {
            editing.copy(note = note, payment = s.payment, at = s.at, actualHome = actual).also { trips.updateEntry(it) }
        } else {
            val amount = parseAmount(s.amountText) ?: return fail(R.string.entry_error_amount)
            val trip = trips.trips.first().firstOrNull { it.id == tripId } ?: return false
            val prefs = settings.settings.first()
            when (val r = EntryFactory.fromConversion(
                amount, s.code, trip, catalog.get(trip.homeCode)?.decimals ?: 2, rates.snapshot.value,
                prefs.feeOn, prefs.feeBps, s.payment, EntrySource.MANUAL, s.at, note,
            )) {
                is EntryResult.Unavailable -> return fail(
                    when (r.reason) {
                        Reason.NO_RATES -> R.string.trip_add_blocked_no_rates
                        Reason.NO_RATE_FOR_PAIR -> R.string.trip_add_blocked_no_pair
                        Reason.ZERO_AMOUNT -> R.string.entry_error_amount
                    },
                )
                is EntryResult.Ok -> r.entry.copy(actualHome = actual).also { trips.add(it) }
            }
        }
        settings.update { it.copy(lastPayment = saved.payment) }
        return true
    }

    private fun fail(res: Int): Boolean {
        _state.update { it.copy(error = UiText.Res(res)) }
        return false
    }

    private fun home(): Currency? = catalog.get(homeCode)

    private fun money(v: BigDecimal, c: Currency?): String = c?.let { formatter.format(v, it) } ?: v.toPlainString()

    /** Bank charge minus the estimate, signed; null without both. */
    private fun diff(bankText: String): String? {
        val e = editing ?: return null
        val actual = parseAmount(bankText) ?: return null
        val d = actual.subtract(e.homeAmount)
        val sign = if (d.signum() < 0) "−" else "+"
        return sign + money(d.abs(), home()) + " " + homeCode
    }

    private fun rateFrom(e: Entry): UiText {
        val time = DateTimeFormatter.ofPattern("d MMM HH:mm", systemLocale()).format(e.rateAt.atZone(zone))
        return when (e.rateSource) {
            RateOrigin.ECB -> UiText.Raw("ECB · $time")
            RateOrigin.FAWAZ -> UiText.Raw("Fawaz · $time")
            RateOrigin.MIXED -> UiText.Res(R.string.entry_rate_mixed, listOf(time))
        }
    }
}
