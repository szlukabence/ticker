package dev.ticker.converter.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.R
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.trips.TripBackup
import dev.ticker.converter.trips.TripMath
import dev.ticker.converter.trips.TripsRepository
import dev.ticker.converter.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TripRowUi(val id: String, val name: String, val datesText: String, val totalText: String, val active: Boolean)

data class TripsUiState(
    val rows: List<TripRowUi> = emptyList(),
    val message: UiText? = null,
    val confirmDeleteId: String? = null,
    /** Name of the trip waiting for delete confirmation. */
    val confirmDeleteName: String? = null,
)

class TripsViewModel(
    private val trips: TripsRepository,
    private val catalog: CurrencyCatalog,
    private val formatter: AmountFormatter,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {
    private val message = MutableStateFlow<UiText?>(null)
    private val confirmId = MutableStateFlow<String?>(null)

    val state: StateFlow<TripsUiState> =
        combine(trips.trips, trips.allEntries, message, confirmId) { all, entries, msg, confirm ->
            val byTrip = entries.groupBy { it.tripId }
            TripsUiState(
                rows = all.map { t -> row(t, TripMath.total(byTrip[t.id].orEmpty())) },
                message = msg,
                confirmDeleteId = confirm?.takeIf { id -> all.any { it.id == id } },
                confirmDeleteName = all.firstOrNull { it.id == confirm }?.name,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    /** File name and content for Back up all trips. */
    suspend fun backup(): Pair<String, String> {
        val s = trips.exportAll()
        val day = DateTimeFormatter.ISO_LOCAL_DATE.format(clock.instant().atZone(zone))
        return "ticker-trips-$day.json" to TripBackup.encode(s.trips, s.entries, clock.instant())
    }

    fun onBackupSaved() { message.value = UiText.Res(R.string.trips_backup_saved) }
    fun onFileError() { message.value = UiText.Res(R.string.trip_file_error) }
    fun clearMessage() { message.value = null }

    /** Adds what is new from a backup file's [text]; says what happened in [TripsUiState.message]. */
    fun restore(text: String) {
        viewModelScope.launch {
            message.value = when (val d = TripBackup.decode(text)) {
                is TripBackup.Decoded.Error -> UiText.Res(
                    when (d.kind) {
                        TripBackup.Kind.NOT_A_BACKUP -> R.string.trips_restore_error_not_a_backup
                        TripBackup.Kind.NEWER_VERSION -> R.string.trips_restore_error_newer_version
                        TripBackup.Kind.CORRUPT -> R.string.trips_restore_error_corrupt
                    },
                )
                is TripBackup.Decoded.Ok -> {
                    val n = trips.restore(d.snapshot)
                    if (n.trips == 0 && n.entries == 0) UiText.Res(R.string.trips_restore_nothing)
                    else UiText.Res(
                        R.string.trips_restored,
                        listOf(UiText.Plural(R.plurals.trips_count, n.trips), UiText.Plural(R.plurals.entries_count, n.entries)),
                    )
                }
            }
        }
    }

    fun requestDelete(id: String) { confirmId.value = id }
    fun cancelDelete() { confirmId.value = null }

    fun confirmDelete() {
        val id = confirmId.value ?: return
        confirmId.value = null
        viewModelScope.launch { trips.deleteTrip(id) }
    }

    private fun row(t: Trip, total: java.math.BigDecimal): TripRowUi {
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val start = fmt.format(t.startedAt.atZone(zone))
        val dates = t.endedAt?.let { "$start – ${fmt.format(it.atZone(zone))}" } ?: start
        val home = catalog.get(t.homeCode)
        val amount = home?.let { formatter.format(total, it) } ?: total.toPlainString()
        return TripRowUi(t.id, t.name, dates, "$amount ${t.homeCode}", t.active)
    }
}
