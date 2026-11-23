package dev.ticker.converter.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.R
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.data.local.SettingsRepository
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.EntrySource
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.trips.TripCsv
import dev.ticker.converter.trips.TripMath
import dev.ticker.converter.trips.TripsRepository
import dev.ticker.converter.ui.text.UiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class EntryRowUi(
    val id: String,
    /** The note, or a default label by where the entry came from. */
    val label: UiText,
    /** "42.50 EUR". */
    val original: String,
    val payment: Payment,
    /** In the trip's home currency; the real bank charge when it is set. */
    val counted: String,
    /** Shown when the amount is the bank's real charge. */
    val bankTag: UiText?,
)

data class DayUi(val date: LocalDate, val rows: List<EntryRowUi>)

data class TripUiState(
    val trip: Trip? = null,
    val totalText: String = "",
    /** "≈ 369.54 EUR" in the first other favourite; null when there is none or no rates. */
    val totalAltText: String? = null,
    val averageText: String = "",
    val count: Int = 0,
    val budgetFraction: Float? = null,
    val budgetLeftText: UiText? = null,
    val overBudget: Boolean = false,
    val days: List<DayUi> = emptyList(),
)

class TripViewModel(
    private val trips: TripsRepository,
    rates: RatesRepository,
    settings: SettingsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
    private val tripId: String,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    val state: StateFlow<TripUiState> =
        combine(trips.trips, trips.entries(tripId), rates.snapshot, settings.settings) { all, entries, snap, s ->
            val trip = all.firstOrNull { it.id == tripId } ?: return@combine TripUiState()
            build(trip, entries, snap, s.favorites)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    fun onEnd() {
        viewModelScope.launch { trips.end(tripId) }
    }

    /** File name and content for Save CSV; null when the trip is gone. */
    suspend fun csv(): Pair<String, String>? {
        val trip = trips.trips.first().firstOrNull { it.id == tripId } ?: return null
        val entries = trips.entries(tripId).first()
        return fileName(trip) to TripCsv.write(trip, entries, zone)
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    private fun fileName(trip: Trip): String {
        val slug = trip.name.lowercase(Locale.ROOT).map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-').ifEmpty { "trip" }
        return slug + "-" + DateTimeFormatter.ISO_LOCAL_DATE.format(today()) + ".csv"
    }

    private fun money(v: BigDecimal, c: Currency?): String = c?.let { formatter.format(v, it) } ?: v.toPlainString()

    private fun build(trip: Trip, entries: List<Entry>, snap: dev.ticker.converter.core.model.RateSnapshot?, favorites: List<String>): TripUiState {
        val home = catalog.get(trip.homeCode)
        val total = TripMath.total(entries)
        val average = TripMath.dailyAverage(trip, entries, today(), zone, home?.decimals ?: 2)
        val left = TripMath.budgetLeft(trip, total)
        val alt = favorites.firstOrNull { it != trip.homeCode }?.let { code ->
            val other = catalog.get(code)
            val v = snap?.let { Converter.convert(total, trip.homeCode, code, it) }
            if (other != null && v != null) "≈ " + formatter.format(v, other) + " " + code else null
        }
        return TripUiState(
            trip = trip,
            totalText = money(total, home),
            totalAltText = alt,
            averageText = money(average, home),
            count = entries.size,
            budgetFraction = TripMath.budgetFraction(trip, total),
            budgetLeftText = left?.let {
                val amount = money(it.abs(), home) + " " + trip.homeCode
                if (it.signum() < 0) UiText.Res(R.string.trip_budget_over, listOf(amount)) else UiText.Res(R.string.trip_budget_left, listOf(amount))
            },
            overBudget = left != null && left.signum() < 0,
            days = TripMath.byDay(entries, zone).map { (date, list) -> DayUi(date, list.map { row(it, home) }) },
        )
    }

    private fun row(e: Entry, home: Currency?) = EntryRowUi(
        id = e.id,
        label = e.note?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Res(
            when (e.source) {
                EntrySource.CONVERT -> R.string.trip_entry_default_convert
                EntrySource.RECEIPT -> R.string.trip_entry_default_receipt
                EntrySource.MANUAL -> R.string.trip_entry_default_manual
            },
        ),
        original = money(e.amount, catalog.get(e.code)) + " " + e.code,
        payment = e.payment,
        counted = money(TripMath.counted(e), home),
        bankTag = if (e.actualHome != null) UiText.Res(R.string.trip_bank_tag) else null,
    )
}
