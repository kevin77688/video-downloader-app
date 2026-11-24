package dev.threadsdl.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.threadsdl.app.R
import dev.threadsdl.app.core.DownloadBus
import dev.threadsdl.app.core.MediaFolder
import dev.threadsdl.app.core.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(folder: Uri?, onChooseFolder: () -> Unit) {
    val context = LocalContext.current
    val completed by DownloadBus.completed.collectAsState()
    var reload by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<MediaFolder.Entry?>(null) }

    val entries by produceState<List<MediaFolder.Entry>?>(null, folder, completed, reload) {
        value = if (folder == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { runCatching { MediaFolder.list(context, folder) }.getOrDefault(emptyList()) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
            Text("Gallery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val list = entries
            if (folder != null && list != null) {
                Text(
                    "${list.size} item${if (list.size == 1) "" else "s"} · ${MediaFolder.displayPath(folder)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val list = entries
        when {
            folder == null -> EmptyState("No download folder yet", "Choose folder", onChooseFolder)
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState("Nothing downloaded yet", null, null)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(list, key = { it.uri.toString() }) { entry ->
                    Thumbnail(
                        entry = entry,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(entry.uri, entry.mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = { toDelete = entry },
                    )
                }
            }
        }
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete file?") },
            text = { Text(entry.name) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { MediaFolder.delete(context, entry.uri) }
                        .onFailure { Toast.makeText(context, "Couldn't delete file", Toast.LENGTH_SHORT).show() }
                    toDelete = null
                    reload++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Thumbnail(entry: MediaFolder.Entry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, entry.uri) {
        value = withContext(Dispatchers.IO) { Thumbs.load(context, entry.uri, entry.isVideo, 320)?.asImageBitmap() }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        bitmap?.let {
            Image(it, contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (entry.isVideo) {
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(6.dp),
            )
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
