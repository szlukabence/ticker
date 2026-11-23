package dev.ticker.converter.ui.components

import dev.ticker.converter.ui.text.resolve
import dev.ticker.converter.ui.text.UiText
import dev.ticker.converter.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ticker.converter.core.model.SyncStatus
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.glow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Exact badge copy from the spec. */
fun syncBadgeText(status: SyncStatus, now: Instant, zone: ZoneId): UiText = when (status) {
    is SyncStatus.Live -> UiText.Res(R.string.sync_live, listOf(TIME.format(status.at.atZone(zone))))
    is SyncStatus.Partial -> UiText.Res(R.string.sync_partial, listOf(TIME.format(status.at.atZone(zone))))
    is SyncStatus.Offline -> UiText.Res(R.string.sync_offline, listOf(relative(Duration.between(status.lastSync, now))))
    SyncStatus.NoData -> UiText.Res(R.string.sync_no_data)
    SyncStatus.Syncing -> UiText.Res(R.string.sync_syncing)
}

private fun relative(d: Duration): UiText = when {
    d.toMinutes() < 1 -> UiText.Res(R.string.sync_just_now)
    d.toHours() < 1 -> UiText.Plural(R.plurals.sync_minutes_ago, d.toMinutes().toInt())
    d.toDays() < 1 -> UiText.Plural(R.plurals.sync_hours_ago, d.toHours().toInt())
    else -> UiText.Plural(R.plurals.sync_days_ago, d.toDays().toInt())
}

@Composable
fun SyncBadge(status: SyncStatus, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(status) {
        while (true) { now = Instant.now(); delay(30_000) }
    }
    val color = when (status) {
        is SyncStatus.Live -> TickerColors.Accent
        is SyncStatus.Partial, is SyncStatus.Offline -> TickerColors.Warning
        else -> TickerColors.Dim
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.glow(color, alpha = 0.8f, blur = 6.dp, corner = 4.dp).size(7.dp).background(color, CircleShape))
        Text(syncBadgeText(status, now, ZoneId.systemDefault()).resolve(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}
