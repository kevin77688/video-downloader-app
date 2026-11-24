package io.github.kevin77688.uvd.ui

import android.Manifest
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.kevin77688.uvd.Extras
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.download.DownloadService
import io.github.kevin77688.uvd.extractor.Extractors
import io.github.kevin77688.uvd.settings.AppSettings
import io.github.kevin77688.uvd.storage.MediaFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class IncomingLink(val link: String, val autoDownload: Boolean, val id: Long = System.nanoTime())

private data class Tab(val label: Int, val icon: Int)

private val tabs = listOf(
    Tab(R.string.tab_download, R.drawable.ic_download),
    Tab(R.string.tab_gallery, R.drawable.ic_gallery),
    Tab(R.string.tab_settings, R.drawable.ic_settings),
)

@Composable
fun MainScreen(incoming: MutableStateFlow<IncomingLink?>, requestedTab: MutableStateFlow<Int?>) {
    val context = LocalContext.current
    // Resources from composition (not Context) so strings follow configuration changes such as the language.
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = remember { ClipboardWatcher(context) }
    val folder by AppSettings.folder.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(Extras.TAB_DOWNLOAD) }
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
                    message(resources.getString(R.string.msg_saving_to, MediaFolder.displayPath(context, uri)))
                    waiting?.let { DownloadService.start(context, it) }
                }
                .onFailure { message(resources.getString(R.string.msg_folder_denied)) }
        } else if (waiting != null) {
            message(resources.getString(R.string.msg_folder_required))
        }
    }

    fun startDownload(text: String) {
        val match = Extractors.find(text)
        when {
            match == null -> message(
                resources.getString(if (text.isBlank()) R.string.msg_paste_first else R.string.msg_unsupported_link),
            )
            AppSettings.hasValidFolder(context) -> DownloadService.start(context, match.url)
            else -> {
                pendingDownload = match.url
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
            if (focused) clipboard.latestSupportedLink()?.let { if (it.url != link) link = it.url }
        }
    }

    val inc by incoming.collectAsState()
    LaunchedEffect(inc) {
        val i = inc ?: return@LaunchedEffect
        incoming.value = null
        link = i.link
        tab = Extras.TAB_DOWNLOAD
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
            title = { Text(stringResource(R.string.folder_dialog_title)) },
            text = { Text(stringResource(R.string.folder_dialog_body, MediaFolder.DEFAULT_NAME)) },
            confirmButton = {
                TextButton(onClick = {
                    showFolderHelp = false
                    folderPicker.launch(MediaFolder.initialPickerUri())
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFolderHelp = false
                    pendingDownload = null
                }) { Text(stringResource(R.string.action_cancel)) }
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
                        label = { Text(stringResource(t.label)) },
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
            val validFolder = folder?.let { AppSettings.validFolder(context) }
            when (tab) {
                Extras.TAB_DOWNLOAD -> DownloadScreen(
                    link = link,
                    onLinkChange = { link = it },
                    onPaste = {
                        val text = clipboard.readText()
                        if (text.isNullOrBlank()) {
                            message(resources.getString(R.string.msg_clipboard_empty))
                        } else {
                            val found = Extractors.find(text)
                            link = found?.url ?: text.trim()
                            if (found == null) message(resources.getString(R.string.msg_clipboard_no_link))
                        }
                    },
                    onDownload = { startDownload(link) },
                    folderLabel = validFolder?.let { MediaFolder.displayPath(context, it) },
                    onChooseFolder = { showFolderHelp = true },
                )
                Extras.TAB_GALLERY -> GalleryScreen(folder = validFolder, onChooseFolder = { showFolderHelp = true })
                else -> SettingsScreen(onChooseFolder = { showFolderHelp = true })
            }
        }
    }
}
