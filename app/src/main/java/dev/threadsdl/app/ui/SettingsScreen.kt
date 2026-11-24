package dev.threadsdl.app.ui

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.threadsdl.app.core.AppSettings
import dev.threadsdl.app.core.MediaFolder
import dev.threadsdl.app.core.SegmentedDownloader
import dev.threadsdl.app.core.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onChooseFolder: () -> Unit) {
    val context = LocalContext.current
    val theme by AppSettings.theme.collectAsState()
    val folder by AppSettings.folder.collectAsState()
    val threads by AppSettings.threads.collectAsState()
    val folderValid = folder != null && AppSettings.hasValidFolder(context)
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
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        SectionTitle("Appearance")
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
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        SectionTitle("Download")
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Download folder", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        folder?.takeIf { folderValid }?.let { MediaFolder.displayPath(it) } ?: "Not set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChooseFolder) { Text(if (folderValid) "Change" else "Choose") }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Download threads", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$threads", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = threads.toFloat(),
                    onValueChange = { AppSettings.setThreads(it.roundToInt()) },
                    valueRange = SegmentedDownloader.MIN_THREADS.toFloat()..SegmentedDownloader.MAX_THREADS.toFloat(),
                    steps = SegmentedDownloader.MAX_THREADS - SegmentedDownloader.MIN_THREADS - 1,
                )
                Text(
                    "Each file is split into parts downloaded in parallel. Failed parts resume and " +
                        "retry automatically. Lower this if downloads become unstable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Text("File name", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Download time, e.g. 20261120_153012.mp4\nPosts with several items add _1, _2, …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("About")
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(36.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Threads Downloader", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Version $version · public posts only",
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
