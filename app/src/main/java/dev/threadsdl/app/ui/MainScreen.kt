package dev.threadsdl.app.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import dev.threadsdl.app.DownloadService
import dev.threadsdl.app.R
import dev.threadsdl.app.core.AppSettings
import dev.threadsdl.app.core.LinkParser
import dev.threadsdl.app.core.MediaFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class IncomingLink(val link: String, val autoDownload: Boolean, val id: Long = System.nanoTime())

private data class Tab(val label: String, val icon: Int)

private val tabs = listOf(
    Tab("Download", R.drawable.ic_download),
    Tab("Gallery", R.drawable.ic_gallery),
    Tab("Settings", R.drawable.ic_settings),
)

/**
 * Reads the clipboard only when its content changed (by ClipDescription timestamp), so the
 * system "pasted from clipboard" toast isn't shown on every return to the app.
 */
private class ClipboardWatcher(private val context: Context) {
    private val cm = context.getSystemService(ClipboardManager::class.java)
    private var lastTimestamp = Long.MIN_VALUE
    private var lastLink: String? = null

    fun latestThreadsLink(): String? {
        val desc = cm.primaryClipDescription ?: return null
        if (desc.timestamp != lastTimestamp) {
            lastTimestamp = desc.timestamp
            lastLink = if (desc.hasMimeType("text/*")) LinkParser.find(readText()) else null
        }
        return lastLink
    }

    fun readText(): String? = cm.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}

@Composable
fun MainScreen(incoming: MutableStateFlow<IncomingLink?>, requestedTab: MutableStateFlow<Int?>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = remember { ClipboardWatcher(context) }
    val folder by AppSettings.folder.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var link by rememberSaveable { mutableStateOf("") }
    var pendingDownload by rememberSaveable { mutableStateOf<String?>(null) }
    var showFolderHelp by rememberSaveable { mutableStateOf(false) }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val waiting = pendingDownload
        pendingDownload = null
        if (uri != null) {
            runCatching { AppSettings.setFolder(context, uri) }
                .onSuccess {
                    message("Saving to ${MediaFolder.displayPath(uri)}")
                    waiting?.let { DownloadService.start(context, it) }
                }
                .onFailure { message("Couldn't get access to that folder") }
        } else if (waiting != null) {
            message("A download folder is required")
        }
    }

    fun startDownload(text: String) {
        val l = LinkParser.find(text)
        when {
            l == null -> message(if (text.isBlank()) "Paste a Threads link first" else "That's not a Threads post link")
            AppSettings.hasValidFolder(context) -> DownloadService.start(context, l)
            else -> {
                pendingDownload = l
                showFolderHelp = true
            }
        }
    }

    // Ask once for notification permission (download progress / results).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Clipboard can only be read while the window has focus: check every time focus returns
    // (app launch, coming back from background, closing a dialog/picker).
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (focused) clipboard.latestThreadsLink()?.let { if (it != link) link = it }
        }
    }

    val inc by incoming.collectAsState()
    LaunchedEffect(inc) {
        val i = inc ?: return@LaunchedEffect
        incoming.value = null
        link = i.link
        tab = 0
        if (i.autoDownload) startDownload(i.link)
    }

    val rt by requestedTab.collectAsState()
    LaunchedEffect(rt) {
        val t = rt ?: return@LaunchedEffect
        requestedTab.value = null
        tab = t
    }

    if (showFolderHelp) {
        AlertDialog(
            onDismissRequest = {
                showFolderHelp = false
                pendingDownload = null
            },
            title = { Text("Choose download folder") },
            text = {
                Text(
                    "Images and videos are saved together in one folder.\n\n" +
                        "Recommended: on the next screen create a folder named " +
                        "\"${MediaFolder.DEFAULT_NAME}\" in Internal storage, open it, " +
                        "then tap \"Use this folder\" and \"Allow\".",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFolderHelp = false
                    folderPicker.launch(MediaFolder.initialPickerUri())
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFolderHelp = false
                    pendingDownload = null
                }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            val folderValid = folder != null && AppSettings.hasValidFolder(context)
            when (tab) {
                0 -> DownloadScreen(
                    link = link,
                    onLinkChange = { link = it },
                    onPaste = {
                        val text = clipboard.readText()
                        if (text.isNullOrBlank()) {
                            message("Clipboard is empty")
                        } else {
                            val found = LinkParser.find(text)
                            link = found ?: text.trim()
                            if (found == null) message("No Threads link in clipboard")
                        }
                    },
                    onDownload = { startDownload(link) },
                    folderLabel = folder?.takeIf { folderValid }?.let { MediaFolder.displayPath(it) },
                    onChooseFolder = { showFolderHelp = true },
                )
                1 -> GalleryScreen(
                    folder = folder?.takeIf { folderValid },
                    onChooseFolder = { showFolderHelp = true },
                )
                else -> SettingsScreen(onChooseFolder = { showFolderHelp = true })
            }
        }
    }
}
