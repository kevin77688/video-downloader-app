package dev.threadsdl.app.core

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One full job: link → media list → segmented download → save into the SAF folder. */
object PostDownloader {

    /** Returns the number of files saved. [onStatus] progress is 0..1 or null when unknown. */
    suspend fun run(context: Context, link: String, onStatus: (String, Float?) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val tree = AppSettings.folder.value?.takeIf { AppSettings.hasValidFolder(context) }
                ?: throw DownloadException("Please choose a download folder first.")

            onStatus("Fetching post…", null)
            val post = ThreadsExtractor.extract(link)
            if (post.items.isEmpty()) throw DownloadException("This post has no images or videos.")

            // File name = download time; carousel items get _1, _2, …
            val base = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val total = post.items.size
            val threads = AppSettings.threads.value

            post.items.forEachIndexed { index, item ->
                val label = if (total > 1) "${index + 1}/$total" else ""
                val tmp = File(context.cacheDir, "dl_${System.nanoTime()}.part")
                try {
                    val serverMime = SegmentedDownloader.download(item.url, tmp, threads) { done, len ->
                        val part = if (len > 0) done.toFloat() / len else 0f
                        val text = buildString {
                            append("Downloading ")
                            append(if (item.isVideo) "video" else "image")
                            if (label.isNotEmpty()) append(" $label")
                            if (len > 0) append(" · ${formatSize(done)} / ${formatSize(len)}")
                        }
                        onStatus(text, if (len > 0) (index + part) / total else null)
                    }
                    val mime = normalizeMime(serverMime, item.isVideo)
                    val name = base + (if (total > 1) "_${index + 1}" else "") + "." + extensionFor(mime, item)
                    onStatus("Saving $name", (index + 1f) / total)
                    val doc = MediaFolder.createFile(context, tree, mime, name)
                    try {
                        val out = context.contentResolver.openOutputStream(doc, "w")
                            ?: throw DownloadException("Can't write to the download folder.")
                        out.use { o -> tmp.inputStream().use { it.copyTo(o, 256 * 1024) } }
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc) }
                        throw e
                    }
                    MediaFolder.realPath(doc)?.let {
                        MediaScannerConnection.scanFile(context, arrayOf(it), arrayOf(mime), null)
                    }
                } finally {
                    tmp.delete()
                }
            }
            total
        }

    private fun normalizeMime(server: String?, isVideo: Boolean): String = when {
        server != null && (server.startsWith("image/") || server.startsWith("video/")) -> server
        isVideo -> "video/mp4"
        else -> "image/jpeg"
    }

    private fun extensionFor(mime: String, item: MediaItem): String = when (mime) {
        "image/jpeg" -> "jpg"
        "video/mp4" -> "mp4"
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: item.url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..4 }
            ?: if (item.isVideo) "mp4" else "jpg"
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
