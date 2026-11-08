package dev.ticker.converter.ui.components

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
fun syncBadgeText(status: SyncStatus, now: Instant, zone: ZoneId): String = when (status) {
    is SyncStatus.Live -> "SYNCED ${TIME.format(status.at.atZone(zone))}"
    is SyncStatus.Partial -> "PARTIAL · SYNC ${TIME.format(status.at.atZone(zone))}"
    is SyncStatus.Offline -> "OFFLINE · last sync ${relative(Duration.between(status.lastSync, now))}"
    SyncStatus.NoData -> "NO DATA"
    SyncStatus.Syncing -> "SYNCING…"
}

private fun relative(d: Duration): String = when {
    d.toMinutes() < 1 -> "just now"
    d.toHours() < 1 -> "${d.toMinutes()}m ago"
    d.toDays() < 1 -> "${d.toHours()}h ago"
    else -> "${d.toDays()}d ago"
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
        Text(syncBadgeText(status, now, ZoneId.systemDefault()), style = MaterialTheme.typography.labelMedium, color = color)
    }
}
