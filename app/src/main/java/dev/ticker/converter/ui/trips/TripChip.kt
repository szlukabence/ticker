package dev.ticker.converter.ui.trips

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ticker.converter.R
import dev.ticker.converter.trips.Reason
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.theme.TickerColors

/** `+ Trip` when no trip is running, `● NAME` while one is. Long names are cut with an ellipsis. */
@Composable
fun TripChip(trip: TripChipUi?, onNew: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (trip == null) stringResource(R.string.trip_chip_new) else stringResource(R.string.trip_chip_active, trip.name)
    val description = if (trip == null) stringResource(R.string.trip_chip_new_desc) else stringResource(R.string.trip_chip_open_desc, trip.name)
    NeonPillButton(
        label, if (trip == null) onNew else onOpen,
        modifier.widthIn(max = 140.dp).semantics { contentDescription = description },
        maxLines = 1, compact = true,
    )
}

/** `+ Add`; disabled, with the reason as its description, when the entry can't be made. */
@Composable
fun TripAddButton(blocked: Reason?, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val description = when (blocked) {
        null -> stringResource(R.string.trip_add_desc)
        Reason.NO_RATES -> stringResource(R.string.trip_add_blocked_no_rates)
        Reason.NO_RATE_FOR_PAIR -> stringResource(R.string.trip_add_blocked_no_pair)
        Reason.ZERO_AMOUNT -> stringResource(R.string.trip_add_blocked_zero)
    }
    NeonPillButton(
        stringResource(R.string.trip_add), onAdd, modifier.semantics { contentDescription = description },
        enabled = blocked == null, maxLines = 1, compact = true,
    )
}

/** "Saved to NAME · Undo", sliding in and out; no motion when the phone's animations are off. */
@Composable
fun SavedLine(note: SavedNote?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(note) }
    if (note != null) shown = note
    val still = animationsOff()
    AnimatedVisibility(
        visible = note != null, modifier = modifier,
        enter = if (still) EnterTransition.None else expandVertically() + fadeIn(),
        exit = if (still) ExitTransition.None else shrinkVertically() + fadeOut(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.trip_saved, shown?.tripName.orEmpty()), Modifier.weight(1f),
                color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(start = 8.dp))
            Text(
                stringResource(R.string.trip_undo),
                Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onUndo).padding(horizontal = 8.dp).wrapContentHeightCentered(),
                color = TickerColors.Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun Modifier.wrapContentHeightCentered(): Modifier = this.then(Modifier.padding(vertical = 10.dp))

/** True when the phone's animator scale is 0 (Remove animations), so slides should not play. */
@Composable
private fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}
