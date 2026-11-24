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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** One full job: link → extractor → parallel segmented downloads (→ merge) → save into the SAF folder. */
object DownloadJob {
    /** Items of a multi-item post downloaded at the same time (they still share the connection limit). */
    private const val PARALLEL_ITEMS = 4
    private const val REPORT_INTERVAL_MS = 250L

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
            val threads = AppSettings.threads.value
            // Every request of this job (video, audio, all items) shares the configured connection count.
            val connections = Semaphore(threads)
            val itemSlots = Semaphore(PARALLEL_ITEMS)
            val progress = List(total) { ItemProgress() }
            val reporter = launch {
                while (isActive) {
                    report(context, progress, onStatus)
                    delay(REPORT_INTERVAL_MS)
                }
            }
            try {
                extraction.items.mapIndexed { index, item ->
                    async {
                        itemSlots.withPermit {
                            downloadItem(context, tree, base, index, total, item, threads, connections, progress[index])
                        }
                    }
                }.awaitAll()
            } finally {
                reporter.cancel()
            }
            total
        }

    private suspend fun downloadItem(
        context: Context,
        tree: Uri,
        base: String,
        index: Int,
        total: Int,
        item: MediaItem,
        threads: Int,
        connections: Semaphore,
        progress: ItemProgress,
    ) {
        progress.label = context.getString(labelFor(item.type))
        val tmp = File(context.cacheDir, "dl_${index}_${System.nanoTime()}.part")
        try {
            val serverMime = fetch(context, item, tmp, threads, connections, progress)
            val mime = normalizeMime(serverMime, item)
            val name = FileNames.name(base, index, total, extensionFor(mime, item))
            progress.label = context.getString(R.string.status_saving, name)
            save(context, tree, tmp, mime, name)
            progress.finished = true
        } finally {
            tmp.delete()
        }
    }

    /** Downloads one item into [dest]; returns the server's MIME type when it is known. */
    private suspend fun fetch(
        context: Context,
        item: MediaItem,
        dest: File,
        threads: Int,
        connections: Semaphore,
        progress: ItemProgress,
    ): String? {
        val audio = item.audio
        if (audio == null) {
            progress.expectStreams(1)
            return SegmentedDownloader.download(
                item.source.url, dest, threads, item.source.headers, connections, progress.newStream(), progress::onTotal,
            )
        }
        // Separate video and audio streams (DASH): download both at the same time, then merge into one MP4.
        progress.expectStreams(2)
        val videoTmp = File(context.cacheDir, "v_${System.nanoTime()}.part")
        val audioTmp = File(context.cacheDir, "a_${System.nanoTime()}.part")
        try {
            coroutineScope {
                listOf(
                    async {
                        SegmentedDownloader.download(
                            item.source.url, videoTmp, threads, item.source.headers, connections, progress.newStream(), progress::onTotal,
                        )
                    },
                    async {
                        SegmentedDownloader.download(
                            audio.url, audioTmp, threads, audio.headers, connections, progress.newStream(), progress::onTotal,
                        )
                    },
                ).awaitAll()
            }
            progress.label = context.getString(R.string.status_merging)
            MediaMerger.merge(videoTmp, audioTmp, dest)
            return "video/mp4"
        } finally {
            videoTmp.delete()
            audioTmp.delete()
        }
    }

    /** Thread-safe progress of one item, summed over its streams (video + audio). */
    private class ItemProgress {
        @Volatile var label: String? = null
        @Volatile var finished = false
        private val streams = CopyOnWriteArrayList<AtomicLong>()
        private val expectedBytes = AtomicLong(0)
        private val reported = AtomicInteger(0)
        @Volatile private var expected = 1
        @Volatile private var sizeUnknown = false

        fun expectStreams(n: Int) {
            expected = n
        }

        fun newStream(): AtomicLong = AtomicLong(0).also { streams.add(it) }

        fun onTotal(bytes: Long) {
            if (bytes > 0) expectedBytes.addAndGet(bytes) else sizeUnknown = true
            reported.incrementAndGet()
        }

        val done: Long get() = streams.sumOf { it.get() }

        /** Total bytes once every stream reported its size, else -1. */
        val total: Long get() = if (!sizeUnknown && reported.get() >= expected) expectedBytes.get() else -1

        val fraction: Float?
            get() {
                if (finished) return 1f
                val t = total
                return if (t > 0) (done.toFloat() / t).coerceAtMost(1f) else null
            }
    }

    private fun report(context: Context, items: List<ItemProgress>, onStatus: (String, Float?) -> Unit) {
        val active = items.firstOrNull { !it.finished && it.label != null } ?: items.lastOrNull { it.label != null }
        val done = items.sumOf { if (it.total > 0) it.done else 0L }
        val len = items.sumOf { it.total.coerceAtLeast(0) }
        val text = buildString {
            append(active?.label ?: context.getString(R.string.status_preparing))
            if (items.size > 1) {
                append(" · ")
                append(context.getString(R.string.status_items_done, items.count { it.finished }, items.size))
            }
            if (len > 0) append(" · ${formatSize(done)} / ${formatSize(len)}")
        }
        val fractions = items.map { it.fraction }
        val fraction = if (fractions.all { it == null }) null else fractions.sumOf { (it ?: 0f).toDouble() }.toFloat() / items.size
        onStatus(text, fraction)
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
