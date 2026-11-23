package dev.ticker.converter.ui.trips

/** The trip chip on Convert and Receipt: the active trip's ID and name. */
data class TripChipUi(val id: String, val name: String)

/** The "Saved to NAME · Undo" line. [entryId] is exactly the entry Undo removes. */
data class SavedNote(val entryId: String, val tripName: String)
