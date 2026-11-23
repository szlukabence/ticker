package dev.ticker.converter.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ticker.converter.R
import dev.ticker.converter.data.catalog.CurrencyCatalog
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

data class TripEditorState(
    val name: String = "",
    val homeCode: String,
    val budgetText: String = "",
    /** The home currency can't change once a trip has entries. */
    val homeLocked: Boolean = false,
    val error: UiText? = null,
    /** Set when starting is refused because [conflict] is still active. */
    val conflict: Trip? = null,
)

/** New trip when [editing] is null, else edits that trip. */
class TripEditorViewModel(
    private val trips: TripsRepository,
    private val catalog: CurrencyCatalog,
    defaultHome: String,
    private val editing: Trip?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TripEditorState(editing?.name.orEmpty(), editing?.homeCode ?: defaultHome, editing?.budgetHome?.toPlainString().orEmpty()),
    )
    val state: StateFlow<TripEditorState> = _state.asStateFlow()

    val isEditing: Boolean get() = editing != null

    init {
        if (editing != null) {
            viewModelScope.launch {
                val locked = trips.entries(editing.id).first().isNotEmpty()
                _state.update { it.copy(homeLocked = locked) }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null, conflict = null) }
    fun onBudget(v: String) = _state.update { it.copy(budgetText = v, error = null) }
    fun onHome(code: String) = _state.update { if (it.homeLocked || catalog.get(code) == null) it else it.copy(homeCode = code) }

    /** The trip, or null with [TripEditorState.error] or [TripEditorState.conflict] set. */
    suspend fun save(): Trip? {
        val s = _state.value
        val name = s.name.trim()
        if (name.isEmpty()) return fail(R.string.trip_error_name)
        val budget = parseBudget(s.budgetText)
        if (budget is Budget.Bad) return fail(R.string.trip_error_budget)
        val value = (budget as Budget.Value).amount
        if (editing != null) {
            // Work from the stored trip: it may have been ended, or got entries, since the editor opened.
            val stored = trips.trips.first().firstOrNull { it.id == editing.id } ?: return fail(R.string.entry_error_trip_gone)
            val locked = trips.entries(stored.id).first().isNotEmpty()
            val t = stored.copy(name = name, homeCode = if (locked) stored.homeCode else s.homeCode, budgetHome = value)
            trips.update(t)
            return t
        }
        return when (val r = trips.start(name, s.homeCode, value)) {
            is TripsRepository.StartResult.Started -> r.trip
            is TripsRepository.StartResult.OtherActive -> { _state.update { it.copy(conflict = r.trip) }; null }
        }
    }

    /** After a [save] reported a conflict: end the running trip and start this one. */
    suspend fun endAndStart(): Trip {
        val s = _state.value
        val value = (parseBudget(s.budgetText) as? Budget.Value)?.amount
        val t = trips.endAndStart(s.name, s.homeCode, value)
        _state.update { it.copy(conflict = null) }
        return t
    }

    private fun fail(res: Int): Trip? {
        _state.update { it.copy(error = UiText.Res(res)) }
        return null
    }

    private sealed interface Budget {
        data class Value(val amount: BigDecimal?) : Budget
        data object Bad : Budget
    }

    /** Blank means no budget; `.` or `,` is the decimal mark; zero or less is an error. */
    private fun parseBudget(text: String): Budget {
        if (text.isBlank()) return Budget.Value(null)
        return parseAmount(text)?.let { Budget.Value(it) } ?: Budget.Bad
    }
}
