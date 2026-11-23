package dev.ticker.converter.ui.trips

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ticker.converter.AppContainer
import dev.ticker.converter.R
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.trips.Entry
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.text.resolve
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TripRoute(container: AppContainer, tripId: String, onBack: () -> Unit, onOpenTrips: () -> Unit) {
    val vm: TripViewModel = viewModel(key = "trip-$tripId") {
        TripViewModel(container.trips, container.rates, container.settings, container.catalog, container.formatter, tripId, container.clock, ZoneId.systemDefault())
    }
    val state by vm.state.collectAsStateWithLifecycle()
    TripScreen(state, vm, container, tripId, onBack, onOpenTrips)
}

@Composable
private fun TripScreen(state: TripUiState, vm: TripViewModel, container: AppContainer, tripId: String, onBack: () -> Unit, onOpenTrips: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var editTrip by remember { mutableStateOf(false) }
    var newEntry by remember { mutableIntStateOf(0) }
    var editingEntry by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val content = pendingCsv
        pendingCsv = null
        if (uri != null && content != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
                    }.getOrDefault(false)
                }
                message = if (ok) R.string.trip_csv_saved else R.string.trip_file_error
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(3_000); message = null }
    }

    val trip = state.trip
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonIconButton(TickerIcons.Back, stringResource(R.string.trip_back), onBack, tint = TickerColors.Text)
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(trip?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (trip != null && !trip.active) {
                    Text(stringResource(R.string.trip_ended), color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall)
                }
            }
            NeonPillButton(stringResource(R.string.trip_add_entry), { newEntry += 1 }, icon = TickerIcons.Plus, maxLines = 1, compact = true)
            Box {
                NeonIconButton(TickerIcons.More, stringResource(R.string.trip_more), { menu = true }, tint = TickerColors.Text)
                DropdownMenu(menu, { menu = false }, containerColor = TickerColors.Surface) {
                    MenuItem(R.string.trip_menu_edit) { menu = false; editTrip = true }
                    if (trip?.active == true) MenuItem(R.string.trip_menu_end) { menu = false; confirmEnd = true }
                    MenuItem(R.string.trip_menu_csv) {
                        menu = false
                        scope.launch {
                            val (name, content) = vm.csv()
                            pendingCsv = content
                            csvLauncher.launch(name)
                        }
                    }
                    MenuItem(R.string.trip_menu_all) { menu = false; onOpenTrips() }
                }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "summary") { Summary(state, trip?.homeCode.orEmpty()) }
            if (state.days.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.trip_empty), Modifier.padding(16.dp), color = TickerColors.Dim, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.days.forEach { day ->
                item(key = "d" + day.date) {
                    Text(
                        dayFormat.format(day.date),
                        Modifier.padding(start = 4.dp, top = 10.dp), color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium,
                    )
                }
                items(day.rows, key = { it.id }) { row -> EntryRow(row) { editingEntry = row.id } }
            }
        }
        message?.let {
            Text(stringResource(it), Modifier.fillMaxWidth().padding(12.dp), color = TickerColors.Accent, style = MaterialTheme.typography.labelMedium)
        }
    }

    if (confirmEnd && trip != null) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            containerColor = TickerColors.Surface,
            title = { Text(stringResource(R.string.trip_end_title)) },
            text = { Text(stringResource(R.string.trip_end_text, trip.name)) },
            confirmButton = { NeonPillButton(stringResource(R.string.trip_menu_end), { confirmEnd = false; vm.onEnd() }) },
            dismissButton = { NeonPillButton(stringResource(R.string.trip_cancel), { confirmEnd = false }) },
        )
    }
    if (editTrip && trip != null) {
        val editor: TripEditorViewModel = viewModel(key = "trip-edit-$tripId") {
            TripEditorViewModel(container.trips, container.catalog, trip.homeCode, trip)
        }
        TripEditorDialog(editor, container.catalog, onDismiss = { editTrip = false }, onDone = { editTrip = false })
    }
    if (newEntry > 0) {
        val editor: EntryEditorViewModel = viewModel(key = "entry-new-$tripId-$newEntry") {
            EntryEditorViewModel(container.trips, container.rates, container.settings, container.catalog, container.formatter, tripId, null, container.clock)
        }
        EntryEditorDialog(editor, container.catalog, onDismiss = { newEntry = 0 }, onDone = { newEntry = 0 })
    }
    editingEntry?.let { id ->
        EditExistingEntry(container, tripId, id) { editingEntry = null }
    }
}

@Composable
private fun EditExistingEntry(container: AppContainer, tripId: String, id: String, onClose: () -> Unit) {
    var entry by remember(id) { mutableStateOf<Entry?>(null) }
    LaunchedEffect(id) { entry = container.trips.exportAll().entries.firstOrNull { it.id == id } }
    val e = entry ?: return
    val editor: EntryEditorViewModel = viewModel(key = "entry-$id") {
        EntryEditorViewModel(container.trips, container.rates, container.settings, container.catalog, container.formatter, tripId, e, container.clock)
    }
    EntryEditorDialog(editor, container.catalog, onDismiss = onClose, onDone = onClose)
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = TickerColors.Text) }, onClick = onClick)
}

@Composable
private fun Summary(state: TripUiState, code: String) {
    Column(Modifier.fillMaxWidth().tile(active = true).padding(16.dp)) {
        Text(stringResource(R.string.trip_total_label), style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, letterSpacing = 1.5.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                state.totalText,
                style = TextStyle(fontFamily = MonoFamily, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = TickerColors.Accent).merge(Neon.glowText()),
                maxLines = 1,
            )
            Text(" $code", Modifier.padding(bottom = 6.dp), color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
        }
        state.totalAltText?.let { Text(it, color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text(stringResource(R.string.trip_average_label), style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim)
                Text("${state.averageText} $code", style = TextStyle(fontFamily = MonoFamily, fontSize = 15.sp))
            }
            Column {
                Text(stringResource(R.string.trip_entries_label), style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim)
                Text(state.count.toString(), style = TextStyle(fontFamily = MonoFamily, fontSize = 15.sp))
            }
        }
        state.budgetFraction?.let { fraction ->
            val color = if (state.overBudget) TickerColors.Warning else TickerColors.Accent
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Neon.Chip)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
            }
            state.budgetLeftText?.let { Text(it.resolve(), Modifier.padding(top = 6.dp), color = color, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun EntryRow(row: EntryRowUi, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tile().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.label.resolve(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val pay = stringResource(if (row.payment == Payment.CARD) R.string.trip_payment_card else R.string.trip_payment_cash)
            Text("${row.original} · $pay", style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(row.counted, style = TextStyle(fontFamily = MonoFamily, fontSize = 16.sp), maxLines = 1)
            row.bankTag?.let { Text(it.resolve(), style = MaterialTheme.typography.labelSmall, color = TickerColors.Accent, maxLines = 1) }
        }
    }
}
