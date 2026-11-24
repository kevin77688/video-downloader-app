package dev.threadsdl.app.core

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

object AppSettings {
    private const val KEY_THEME = "theme"
    private const val KEY_FOLDER = "folder"
    private const val KEY_THREADS = "threads"
    const val DEFAULT_THREADS = 4

    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    private val _folder = MutableStateFlow<Uri?>(null)
    private val _threads = MutableStateFlow(DEFAULT_THREADS)
    val theme: StateFlow<ThemeMode> = _theme
    val folder: StateFlow<Uri?> = _folder
    val threads: StateFlow<Int> = _threads

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        _folder.value = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        _threads.value = prefs.getInt(KEY_THREADS, DEFAULT_THREADS)
            .coerceIn(SegmentedDownloader.MIN_THREADS, SegmentedDownloader.MAX_THREADS)
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setThreads(count: Int) {
        val n = count.coerceIn(SegmentedDownloader.MIN_THREADS, SegmentedDownloader.MAX_THREADS)
        if (n == _threads.value) return
        _threads.value = n
        prefs.edit().putInt(KEY_THREADS, n).apply()
    }

    /** Persists a SAF tree grant and releases the previous folder's grant. */
    fun setFolder(context: Context, uri: Uri) {
        val cr = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        cr.takePersistableUriPermission(uri, flags)
        val old = _folder.value
        if (old != null && old != uri) runCatching { cr.releasePersistableUriPermission(old, flags) }
        _folder.value = uri
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
    }

    /** True when a folder is chosen and we still hold write access to it. */
    fun hasValidFolder(context: Context): Boolean {
        val uri = _folder.value ?: return false
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }
}
