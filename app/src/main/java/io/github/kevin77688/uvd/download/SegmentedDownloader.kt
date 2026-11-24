package io.github.kevin77688.uvd.download

import io.github.kevin77688.uvd.core.Http
import io.github.kevin77688.uvd.core.HttpStatusException

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * IDM-style multi-connection downloader.
 *
 * Stability first:
 * - Each segment resumes from its last written byte with HTTP Range after any I/O error
 *   (exponential backoff, retry budget resets whenever bytes arrive).
 * - Servers without Range support fall back to a single stream, retried from scratch.
 * - The final file size is verified against the server's reported length.
 */
object SegmentedDownloader {
    const val MIN_THREADS = 1
    const val MAX_THREADS = 8
    private const val MIN_SEGMENT_BYTES = 512L * 1024
    private const val BUFFER = 64 * 1024
    const val TAG = "UVD"

    private class Probe(val total: Long, val rangeSupported: Boolean, val mime: String?)

    /** Downloads [url] into [dest] (sending [headers] on every request); returns the server's MIME type if known. */
    suspend fun download(
        url: String,
        dest: File,
        threads: Int,
        headers: Map<String, String> = emptyMap(),
        onProgress: (done: Long, total: Long) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val probe = Http.retrying { probe(url, headers) }
        val done = AtomicLong(0)
        val reporter = launch {
            while (isActive) {
                onProgress(done.get(), probe.total)
                delay(250)
            }
        }
        try {
            if (probe.rangeSupported && probe.total > 0) {
                RandomAccessFile(dest, "rw").use { it.setLength(probe.total) }
                val maxBySize = (probe.total / MIN_SEGMENT_BYTES).toInt().coerceAtLeast(1)
                val n = threads.coerceIn(MIN_THREADS, MAX_THREADS).coerceAtMost(maxBySize)
                Log.i(TAG, "segmented download: ${probe.total} bytes, $n segments")
                val size = probe.total / n
                (0 until n).map { i ->
                    val start = i * size
                    val end = if (i == n - 1) probe.total - 1 else start + size - 1
                    async { downloadRange(url, headers, dest, start, end, done) }
                }.awaitAll()
                // The file was pre-allocated, so verify the bytes actually received instead of its length.
                if (done.get() != probe.total || dest.length() != probe.total) {
                    throw IOException("Incomplete download: ${done.get()} of ${probe.total} bytes")
                }
            } else {
                Log.i(TAG, "single-stream download (range unsupported), ${probe.total} bytes")
                Http.retrying {
                    done.set(0)
                    downloadWhole(url, headers, dest, done)
                }
            }
        } finally {
            reporter.cancel()
        }
        onProgress(done.get(), probe.total)
        probe.mime
    }

    private fun probe(url: String, headers: Map<String, String>): Probe {
        val conn = Http.open(url, headers).apply {
            setRequestProperty("Range", "bytes=0-0")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val mime = conn.contentType?.substringBefore(';')?.trim()
            return when (val status = conn.responseCode) {
                206 -> {
                    val total = conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                    Probe(total, total > 0, mime)
                }
                200 -> Probe(conn.contentLengthLong, false, mime)
                else -> throw HttpStatusException(status)
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadRange(
        url: String,
        headers: Map<String, String>,
        dest: File,
        start: Long,
        end: Long,
        done: AtomicLong,
    ) {
        var pos = start
        var attempt = 0
        while (pos <= end) {
            currentCoroutineContext().ensureActive()
            try {
                val conn = Http.open(url, headers).apply {
                    setRequestProperty("Range", "bytes=$pos-$end")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                try {
                    val status = conn.responseCode
                    if (status != 206) throw HttpStatusException(status)
                    RandomAccessFile(dest, "rw").use { raf ->
                        raf.seek(pos)
                        conn.inputStream.use { input ->
                            val buf = ByteArray(BUFFER)
                            while (pos <= end) {
                                val want = minOf(buf.size.toLong(), end - pos + 1).toInt()
                                val n = input.read(buf, 0, want)
                                if (n < 0) break
                                raf.write(buf, 0, n)
                                pos += n
                                done.addAndGet(n.toLong())
                                attempt = 0
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (pos <= end) throw IOException("Connection closed early")
            } catch (e: IOException) {
                if (e is HttpStatusException && !e.retryable) throw e
                if (++attempt > Http.MAX_RETRIES) throw e
                delay(Http.backoffMs(attempt))
            }
        }
    }

    private fun downloadWhole(url: String, headers: Map<String, String>, dest: File, done: AtomicLong) {
        val conn = Http.open(url, headers).apply { setRequestProperty("Accept-Encoding", "identity") }
        try {
            val status = conn.responseCode
            if (status != 200) throw HttpStatusException(status)
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done.addAndGet(n.toLong())
                    }
                }
            }
            val expected = conn.contentLengthLong
            if (expected > 0 && dest.length() != expected) throw IOException("Incomplete download")
        } finally {
            conn.disconnect()
        }
    }
}
