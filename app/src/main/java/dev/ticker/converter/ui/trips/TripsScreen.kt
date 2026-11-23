package dev.ticker.converter.ui.trips

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ticker.converter.AppContainer
import dev.ticker.converter.R
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.text.resolve
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

@Composable
fun TripsRoute(container: AppContainer, onBack: () -> Unit, onOpenTrip: (String) -> Unit) {
    val vm: TripsViewModel = viewModel {
        TripsViewModel(container.trips, container.catalog, container.formatter, container.clock, ZoneId.systemDefault())
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val content = pendingBackup
        pendingBackup = null
        if (uri != null && content != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
                }
                if (ok) vm.onBackupSaved() else vm.onFileError()
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull()
                }
                if (text == null) vm.onFileError() else vm.restore(text)
            }
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) { delay(4_000); vm.clearMessage() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonIconButton(TickerIcons.Back, stringResource(R.string.trip_back), onBack, tint = TickerColors.Text)
            Text(stringResource(R.string.trips_title), Modifier.weight(1f).padding(horizontal = 4.dp), style = MaterialTheme.typography.headlineSmall)
            Box {
                NeonIconButton(TickerIcons.More, stringResource(R.string.trip_more), { menu = true }, tint = TickerColors.Text)
                DropdownMenu(menu, { menu = false }, containerColor = TickerColors.Surface) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trips_menu_backup), style = MaterialTheme.typography.bodyMedium, color = TickerColors.Text) },
                        onClick = {
                            menu = false
                            scope.launch {
                                val (name, content) = vm.backup()
                                pendingBackup = content
                                saveLauncher.launch(name)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trips_menu_restore), style = MaterialTheme.typography.bodyMedium, color = TickerColors.Text) },
                        onClick = { menu = false; openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                }
            }
        }
        if (state.rows.isEmpty()) {
            Text(stringResource(R.string.trips_empty), Modifier.padding(20.dp), color = TickerColors.Dim, style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.rows, key = { it.id }) { row ->
                Row(
                    Modifier.fillMaxWidth().tile(active = row.active).clickable { onOpenTrip(row.id) }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (row.active) {
                                Text("  " + stringResource(R.string.trips_active), color = TickerColors.Accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                        Text(row.datesText, color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(row.totalText, style = TextStyle(fontFamily = MonoFamily, fontSize = 15.sp), maxLines = 1)
                    NeonIconButton(TickerIcons.Close, stringResource(R.string.trips_delete_desc, row.name), { vm.requestDelete(row.id) })
                }
            }
        }
        state.message?.let {
            Text(it.resolve(), Modifier.fillMaxWidth().padding(14.dp), color = TickerColors.Accent, style = MaterialTheme.typography.labelMedium)
        }
    }

    state.confirmDeleteName?.let { name ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            containerColor = TickerColors.Surface,
            title = { Text(stringResource(R.string.trips_delete_title)) },
            text = { Text(stringResource(R.string.trips_delete_text, name)) },
            confirmButton = { NeonPillButton(stringResource(R.string.trips_delete), vm::confirmDelete) },
            dismissButton = { NeonPillButton(stringResource(R.string.trip_cancel), vm::cancelDelete) },
        )
    }
}
