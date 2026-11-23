package dev.ticker.converter.ui.about

import dev.ticker.converter.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import dev.ticker.converter.BuildConfig
import dev.ticker.converter.ui.components.FullScreenDialog
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.dotGrid
import dev.ticker.converter.ui.theme.tile

/** Version, licence and warranty notice, data-source credits, privacy summary and third-party licences. */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    var reading by remember { mutableStateOf<Component?>(null) }
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uri.openUri(url) } }

    FullScreenDialog(onDismissRequest = onClose) {
        CompositionLocalProvider(LocalContentColor provides TickerColors.Text) {
            Column(Modifier.fillMaxSize().dotGrid().safeDrawingPadding()) {
                val shown = reading
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (shown == null) R.string.about_title else R.string.about_licence), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    if (shown == null) {
                        NeonIconButton(TickerIcons.Close, stringResource(R.string.common_close), onClose, tint = TickerColors.Text)
                    } else {
                        NeonIconButton(TickerIcons.Close, stringResource(R.string.about_back), { reading = null }, tint = TickerColors.Text)
                    }
                }
                if (shown != null) {
                    LicenceText(shown)
                } else {
                    AboutList(open) { reading = it }
                }
            }
        }
    }
}

@Composable
private fun AboutList(open: (String) -> Unit, onLicence: (Component) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Section {
                Text("TICKER", style = MaterialTheme.typography.headlineSmall.merge(Neon.glowText()), color = TickerColors.Accent)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = TickerColors.Dim, style = MaterialTheme.typography.bodyMedium)
                Body(AboutContent.COPYRIGHT)
                Body(stringResource(R.string.about_legal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonPillButton(stringResource(R.string.about_source_code), { open(AboutContent.SOURCE_URL) })
                    NeonPillButton(stringResource(R.string.about_licence), { onLicence(AboutContent.components.first()) })
                }
            }
        }
        item {
            Section {
                Heading(stringResource(R.string.about_rates))
                Body(stringResource(R.string.about_disclaimer))
                for (src in AboutContent.dataSources) {
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.about_open_source, src.name)) { open(src.url) }.padding(vertical = 4.dp)) {
                        Text(src.name, color = TickerColors.Accent, style = MaterialTheme.typography.titleSmall)
                        Body(stringResource(src.detail))
                    }
                }
            }
        }
        item {
            Section {
                Heading(stringResource(R.string.about_privacy_title))
                Body(stringResource(R.string.about_privacy))
                NeonPillButton(stringResource(R.string.about_privacy_policy), { open(AboutContent.PRIVACY_URL) })
            }
        }
        item { Heading(stringResource(R.string.about_oss_licences), Modifier.padding(start = 6.dp, top = 8.dp)) }
        items(AboutContent.components.drop(1)) { c ->
            Row(
                Modifier.fillMaxWidth().tile().clickable(role = Role.Button, onClickLabel = stringResource(R.string.about_show_licence)) { onLicence(c) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    if (c.note.isNotEmpty()) Text(c.note, color = TickerColors.Dim, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(10.dp))
                Text(c.licence, color = TickerColors.Accent, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LicenceText(c: Component) {
    val context = LocalContext.current
    val missing = stringResource(R.string.about_licence_missing, c.licenceAsset)
    val text = remember(c) {
        runCatching { context.assets.open(c.licenceAsset).bufferedReader().use { it.readText() } }
            .getOrDefault(missing)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(c.name, color = TickerColors.Accent, style = MaterialTheme.typography.titleMedium)
        if (c.note.isNotEmpty()) Text(c.note, color = TickerColors.Dim, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.padding(top = 10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp))
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().tile().padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = TickerColors.Dim, style = MaterialTheme.typography.titleSmall)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp))
