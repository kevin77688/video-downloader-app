package io.github.kevin77688.uvd.ui

import android.content.ClipboardManager
import android.content.Context
import io.github.kevin77688.uvd.extractor.Extractors

/**
 * Reads the clipboard only when its content changed (by ClipDescription timestamp), so the
 * system "pasted from clipboard" toast isn't shown on every return to the app.
 * The clipboard is only readable while our window has focus — call from a focus-gained callback.
 */
class ClipboardWatcher(private val context: Context) {
    private val cm = context.getSystemService(ClipboardManager::class.java)
    private var lastTimestamp = Long.MIN_VALUE
    private var lastMatch: Extractors.Match? = null

    /** The supported link currently on the clipboard, if any. */
    fun latestSupportedLink(): Extractors.Match? {
        val desc = cm.primaryClipDescription ?: return null
        if (desc.timestamp != lastTimestamp) {
            lastTimestamp = desc.timestamp
            lastMatch = if (desc.hasMimeType("text/*")) Extractors.find(readText()) else null
        }
        return lastMatch
    }

    fun readText(): String? = cm.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}
