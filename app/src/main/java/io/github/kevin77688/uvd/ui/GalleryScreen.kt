package io.github.kevin77688.uvd.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.download.DownloadBus
import io.github.kevin77688.uvd.extractor.Extractors
import io.github.kevin77688.uvd.extractor.Platform
import io.github.kevin77688.uvd.storage.MediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class GallerySort(val label: Int) {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    PLATFORM(R.string.sort_platform),
}

/** Filter value: a platform id, [FILTER_ALL], or [FILTER_OTHER] (files without a known platform). */
private const val FILTER_ALL = "all"
private const val FILTER_OTHER = "other"

@Composable
fun GalleryScreen(folder: Uri?, onChooseFolder: () -> Unit) {
    val context = LocalContext.current
    val completed by DownloadBus.completed.collectAsState()
    var reload by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<MediaFolder.Entry?>(null) }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var sort by rememberSaveable { mutableStateOf(GallerySort.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }

    val entries by produceState<List<MediaFolder.Entry>?>(null, folder, completed, reload) {
        value = if (folder == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { runCatching { MediaFolder.list(context, folder) }.getOrDefault(emptyList()) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_gallery), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                val list = entries
                if (folder != null && list != null) {
                    Text(
                        pluralStringResource(R.plurals.gallery_items, list.size, list.size) +
                            " · " + MediaFolder.displayPath(context, folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (folder != null) {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        GallerySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.label)) },
                                leadingIcon = { RadioButton(selected = sort == option, onClick = null) },
                                onClick = {
                                    sort = option
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        val list = entries
        when {
            folder == null -> EmptyState(stringResource(R.string.folder_none), stringResource(R.string.action_choose_folder), onChooseFolder)
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                FilterRow(list, filter) { filter = it }
                val visible = list.filter { matches(it, filter) }
                if (visible.isEmpty()) {
                    EmptyState(stringResource(R.string.gallery_empty), null, null)
                } else {
                    MediaGrid(
                        entries = visible,
                        sort = sort,
                        showBadges = filter == FILTER_ALL,
                        onOpen = { entry ->
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(entry.uri, entry.mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.msg_no_viewer, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { toDelete = it },
                    )
                }
            }
        }
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, entry.name)) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { MediaFolder.delete(context, entry.uri) }
                        .onFailure { Toast.makeText(context, R.string.msg_delete_failed, Toast.LENGTH_SHORT).show() }
                    toDelete = null
                    reload++
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun matches(entry: MediaFolder.Entry, filter: String): Boolean = when (filter) {
    FILTER_ALL -> true
    FILTER_OTHER -> entry.platform == null
    else -> entry.platform?.id == filter
}

/** "All" + every supported platform (with counts, so the list doubles as "what's supported") + "Other". */
@Composable
private fun FilterRow(entries: List<MediaFolder.Entry>, selected: String, onSelect: (String) -> Unit) {
    val counts = remember(entries) { entries.groupingBy { it.platform }.eachCount() }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == FILTER_ALL,
            onClick = { onSelect(FILTER_ALL) },
            label = { Text(stringResource(R.string.filter_all_count, entries.size)) },
        )
        Extractors.platforms.forEach { platform ->
            val count = counts[platform] ?: 0
            FilterChip(
                selected = selected == platform.id,
                onClick = { onSelect(platform.id) },
                label = { Text("${platform.displayName} $count") },
                leadingIcon = { Box(Modifier.size(10.dp).background(platform.color, CircleShape)) },
            )
        }
        val other = counts[null] ?: 0
        if (other > 0) {
            FilterChip(
                selected = selected == FILTER_OTHER,
                onClick = { onSelect(FILTER_OTHER) },
                label = { Text(stringResource(R.string.filter_other_count, other)) },
            )
        }
    }
}

@Composable
private fun MediaGrid(
    entries: List<MediaFolder.Entry>,
    sort: GallerySort,
    showBadges: Boolean,
    onOpen: (MediaFolder.Entry) -> Unit,
    onDelete: (MediaFolder.Entry) -> Unit,
) {
    // MediaFolder.list() is already newest-first.
    val sections: List<Pair<Platform?, List<MediaFolder.Entry>>> = remember(entries, sort) {
        when (sort) {
            GallerySort.NEWEST -> listOf(null to entries)
            GallerySort.OLDEST -> listOf(null to entries.reversed())
            GallerySort.PLATFORM -> {
                val order = Extractors.platforms
                entries.groupBy { it.platform }.entries
                    .sortedBy { (p, _) -> p?.let { order.indexOf(it) } ?: Int.MAX_VALUE }
                    .map { (p, list) -> p to list }
            }
        }
    }
    val grouped = sort == GallerySort.PLATFORM
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        sections.forEach { (platform, list) ->
            if (grouped) {
                item(key = "header-${platform?.id ?: FILTER_OTHER}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "${platform?.displayName ?: stringResource(R.string.filter_other)} · ${list.size}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
            }
            items(list, key = { it.uri.toString() }) { entry ->
                Thumbnail(entry, showBadge = showBadges && !grouped, onClick = { onOpen(entry) }, onDelete = { onDelete(entry) })
            }
        }
    }
}

@Composable
private fun Thumbnail(entry: MediaFolder.Entry, showBadge: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, entry.uri) {
        value = if (entry.isAudio) {
            null
        } else {
            withContext(Dispatchers.IO) { Thumbs.load(context, entry.uri, entry.isVideo, 320)?.asImageBitmap() }
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onDelete),
    ) {
        bitmap?.let {
            Image(it, contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when {
            entry.isAudio -> Icon(
                painterResource(R.drawable.ic_music),
                contentDescription = entry.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
            )
            entry.isVideo -> Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.video),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(6.dp),
            )
        }
        // Small delete button (long-press does the same); both ask for confirmation first.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.action_delete),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        if (showBadge) {
            entry.platform?.let {
                PlatformBadge(
                    it,
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, action: String?, onAction: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_gallery),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
