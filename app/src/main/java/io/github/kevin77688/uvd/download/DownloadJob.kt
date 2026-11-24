package io.github.kevin77688.uvd.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.extractor.Extractors
import io.github.kevin77688.uvd.extractor.MediaItem
import io.github.kevin77688.uvd.extractor.MediaType
import io.github.kevin77688.uvd.settings.AppSettings
import io.github.kevin77688.uvd.storage.FileNames
import io.github.kevin77688.uvd.storage.MediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** One full job: link → extractor → segmented download (→ merge) → save into the SAF folder. */
object DownloadJob {

    /** Returns the number of files saved. [onStatus] progress is 0..1, or null when unknown. */
    suspend fun run(context: Context, match: Extractors.Match, onStatus: (String, Float?) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val tree = AppSettings.validFolder(context) ?: throw UserException(R.string.err_no_folder)

            onStatus(context.getString(R.string.status_fetching, match.platform.displayName), null)
            val extraction = match.extractor.extract(match.url)
            if (extraction.items.isEmpty()) throw UserException(R.string.err_no_media)

            // File name = platform + download time; multi-item posts get _1, _2, …
            val base = FileNames.base(extraction.platform)
            val total = extraction.items.size
            extraction.items.forEachIndexed { index, item ->
                val counter = if (total > 1) " ${index + 1}/$total" else ""
                val tmp = File(context.cacheDir, "dl_${System.nanoTime()}.part")
                try {
                    val serverMime = fetch(context, item, tmp) { done, len ->
                        val text = buildString {
                            append(context.getString(labelFor(item.type)))
                            append(counter)
                            if (len > 0) append(" · ${formatSize(done)} / ${formatSize(len)}")
                        }
                        val part = if (len > 0) done.toFloat() / len else 0f
                        onStatus(text, if (len > 0) (index + part) / total else null)
                    }
                    val mime = normalizeMime(serverMime, item)
                    val name = FileNames.name(base, index, total, extensionFor(mime, item))
                    onStatus(context.getString(R.string.status_saving, name), (index + 1f) / total)
                    save(context, tree, tmp, mime, name)
                } finally {
                    tmp.delete()
                }
            }
            total
        }

    /** Downloads one item into [dest]; returns the server's MIME type when it is known. */
    private suspend fun fetch(context: Context, item: MediaItem, dest: File, onProgress: (Long, Long) -> Unit): String? {
        val threads = AppSettings.threads.value
        if (item.audio != null) {
            // Separate video and audio streams (DASH) — merging is added together with the
            // first extractor that needs it.
            throw UserException(R.string.err_unsupported_format)
        }
        return SegmentedDownloader.download(item.source.url, dest, threads, item.source.headers, onProgress)
    }

    private fun save(context: Context, tree: Uri, file: File, mime: String, name: String) {
        val doc = MediaFolder.createFile(context, tree, mime, name)
        try {
            val out = context.contentResolver.openOutputStream(doc, "w") ?: throw UserException(R.string.err_folder_write)
            out.use { o -> file.inputStream().use { it.copyTo(o, 256 * 1024) } }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc) }
            throw e
        }
        MediaFolder.realPath(doc)?.let { MediaScannerConnection.scanFile(context, arrayOf(it), arrayOf(mime), null) }
    }

    private fun labelFor(type: MediaType): Int = when (type) {
        MediaType.IMAGE -> R.string.status_downloading_image
        MediaType.VIDEO -> R.string.status_downloading_video
        MediaType.AUDIO -> R.string.status_downloading_audio
    }

    private fun normalizeMime(server: String?, item: MediaItem): String {
        val family = when (item.type) {
            MediaType.IMAGE -> "image/"
            MediaType.VIDEO -> "video/"
            MediaType.AUDIO -> "audio/"
        }
        if (server != null && server.startsWith(family)) return server
        item.extension?.let { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it } }
        return when (item.type) {
            MediaType.IMAGE -> "image/jpeg"
            MediaType.VIDEO -> "video/mp4"
            MediaType.AUDIO -> "audio/mp4"
        }
    }

    private fun extensionFor(mime: String, item: MediaItem): String = when (mime) {
        "image/jpeg" -> "jpg"
        "video/mp4" -> "mp4"
        "audio/mp4" -> "m4a"
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: item.extension
            ?: item.source.url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..4 }
            ?: if (item.type == MediaType.IMAGE) "jpg" else "mp4"
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
