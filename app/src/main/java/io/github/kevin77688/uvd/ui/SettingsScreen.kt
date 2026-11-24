package io.github.kevin77688.uvd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.download.SegmentedDownloader
import io.github.kevin77688.uvd.extractor.Extractors
import io.github.kevin77688.uvd.settings.AppSettings
import io.github.kevin77688.uvd.settings.ThemeMode
import io.github.kevin77688.uvd.storage.MediaFolder
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onChooseFolder: () -> Unit) {
    val context = LocalContext.current
    val theme by AppSettings.theme.collectAsState()
    val folder by AppSettings.folder.collectAsState()
    val threads by AppSettings.threads.collectAsState()
    val validFolder = folder?.let { AppSettings.validFolder(context) }
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        SectionTitle(stringResource(R.string.settings_appearance))
        Card(shape = RoundedCornerShape(16.dp)) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = theme == mode, onClick = { AppSettings.setTheme(mode) }, role = Role.RadioButton)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme == mode, onClick = null)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(mode.label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        SectionTitle(stringResource(R.string.settings_download))
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_folder), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        validFolder?.let { MediaFolder.displayPath(context, it) } ?: stringResource(R.string.settings_folder_not_set),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChooseFolder) {
                    Text(stringResource(if (validFolder != null) R.string.action_change else R.string.action_choose))
                }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_threads), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$threads", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = threads.toFloat(),
                    onValueChange = { AppSettings.setThreads(it.roundToInt()) },
                    valueRange = SegmentedDownloader.MIN_THREADS.toFloat()..SegmentedDownloader.MAX_THREADS.toFloat(),
                    steps = SegmentedDownloader.MAX_THREADS - SegmentedDownloader.MIN_THREADS - 1,
                )
                Text(
                    stringResource(R.string.settings_threads_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_file_name), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_file_name_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_about))
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(36.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_version, version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.settings_supported, Extractors.platforms.joinToString(", ") { it.displayName }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
