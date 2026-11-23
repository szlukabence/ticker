package dev.ticker.converter.ui.trips

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds the saved line for [showMs]. A new [show] replaces the note and restarts the clock. */
class SavedNoteTimer(private val scope: CoroutineScope, private val showMs: Long = 4_000) {
    private val _note = MutableStateFlow<SavedNote?>(null)
    val note: StateFlow<SavedNote?> = _note.asStateFlow()
    private var job: Job? = null

    fun show(n: SavedNote) {
        job?.cancel()
        _note.value = n
        job = scope.launch {
            delay(showMs)
            _note.value = null
        }
    }

    fun clear() {
        job?.cancel()
        _note.value = null
    }
}
