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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * IDM-style multi-connection downloader.
 *
 * Speed: the file is cut into small chunks in a shared queue and every connection keeps taking
 * the next one, so all connections stay busy until the very end (no slow "last segment" tail).
 * Several downloads can share one [Semaphore] so that video, audio and multi-item posts run in
 * parallel without opening more than the configured number of connections in total.
 *
 * Stability first:
 * - A chunk resumes from its last written byte with HTTP Range after any I/O error
 *   (exponential backoff; the retry budget resets whenever bytes arrive).
 * - Each HTTP request asks for at most [MAX_CHUNK_BYTES]; video CDNs throttle or cut long
 *   ranges, and short requests make resuming cheap.
 * - Servers without Range support fall back to a single stream, retried from scratch.
 * - The final file size is verified against the server's reported length.
 */
object SegmentedDownloader {
    const val MIN_THREADS = 1
    const val MAX_THREADS = 8
    private const val MIN_CHUNK_BYTES = 512L * 1024
    private const val MAX_CHUNK_BYTES = 8L * 1024 * 1024
    /** Aim for a few chunks per connection so the queue balances well. */
    private const val CHUNKS_PER_CONNECTION = 4
    private const val BUFFER = 64 * 1024
    const val TAG = "UVD"

    private class Probe(val total: Long, val rangeSupported: Boolean, val mime: String?)

    /**
     * Downloads [url] into [dest] (sending [headers] on every request) with up to [threads]
     * connections, each request holding a permit of [connections] when given.
     * [done] receives every received byte; [onTotal] the size once known (or -1).
     * Returns the server's MIME type if known.
     */
    suspend fun download(
        url: String,
        dest: File,
        threads: Int,
        headers: Map<String, String> = emptyMap(),
        connections: Semaphore? = null,
        done: AtomicLong = AtomicLong(0),
        onTotal: (Long) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val probe = Http.retrying { probe(url, headers) }
        onTotal(probe.total)
        val start = done.get()
        val startedAt = System.nanoTime()
        if (probe.rangeSupported && probe.total > 0) {
            RandomAccessFile(dest, "rw").use { it.setLength(probe.total) }
            val workers = threads.coerceIn(MIN_THREADS, MAX_THREADS)
            val chunkSize = (probe.total / (workers * CHUNKS_PER_CONNECTION)).coerceIn(MIN_CHUNK_BYTES, MAX_CHUNK_BYTES)
            val queue = ConcurrentLinkedQueue<LongRange>()
            var pos = 0L
            while (pos < probe.total) {
                val end = minOf(probe.total, pos + chunkSize) - 1
                queue.add(pos..end)
                pos = end + 1
            }
            val n = workers.coerceAtMost(queue.size)
            Log.i(TAG, "segmented download: ${probe.total} bytes, ${queue.size} chunks, $n connections")
            (0 until n).map {
                async {
                    while (true) {
                        val chunk = queue.poll() ?: break
                        downloadChunk(url, headers, dest, chunk, done, connections)
                    }
                }
            }.awaitAll()
            // The file was pre-allocated, so verify the bytes actually received instead of its length.
            val received = done.get() - start
            if (received != probe.total || dest.length() != probe.total) {
                throw IOException("Incomplete download: $received of ${probe.total} bytes")
            }
        } else {
            Log.i(TAG, "single-stream download (range unsupported), ${probe.total} bytes")
            Http.retrying {
                done.set(start)
                connections.limit { downloadWhole(url, headers, dest, done) }
            }
        }
        val seconds = (System.nanoTime() - startedAt) / 1e9
        val bytes = done.get() - start
        Log.i(TAG, String.format(Locale.US, "downloaded %d bytes in %.1f s (%.1f MB/s)", bytes, seconds, bytes / 1048576.0 / seconds.coerceAtLeast(0.001)))
        probe.mime
    }

    private suspend inline fun <T> Semaphore?.limit(block: () -> T): T = if (this == null) block() else withPermit(block)

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

    /** Downloads one chunk, resuming after errors. The permit is released while backing off. */
    private suspend fun downloadChunk(
        url: String,
        headers: Map<String, String>,
        dest: File,
        chunk: LongRange,
        done: AtomicLong,
        connections: Semaphore?,
    ) {
        var pos = chunk.first
        var attempt = 0
        while (pos <= chunk.last) {
            currentCoroutineContext().ensureActive()
            try {
                connections.limit { pos = readRange(url, headers, dest, pos, chunk.last, done) { attempt = 0 } }
                if (pos <= chunk.last) throw IOException("Connection closed early")
            } catch (e: IOException) {
                Log.w(TAG, "chunk ${chunk.first}-${chunk.last} at $pos: $e (attempt ${attempt + 1})")
                if (e is HttpStatusException && !e.retryable) throw e
                if (++attempt > Http.MAX_RETRIES) throw e
                delay(Http.backoffMs(attempt))
            }
        }
    }

    /** One Range request for [from]..[to]; returns the next position to fetch (> [to] when complete). */
    private fun readRange(
        url: String,
        headers: Map<String, String>,
        dest: File,
        from: Long,
        to: Long,
        done: AtomicLong,
        onBytes: () -> Unit,
    ): Long {
        var pos = from
        val conn = Http.open(url, headers).apply {
            setRequestProperty("Range", "bytes=$from-$to")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val status = conn.responseCode
            if (status != 206) throw HttpStatusException(status)
            RandomAccessFile(dest, "rw").use { raf ->
                raf.seek(pos)
                conn.inputStream.use { input ->
                    val buf = ByteArray(BUFFER)
                    while (pos <= to) {
                        val want = minOf(buf.size.toLong(), to - pos + 1).toInt()
                        val n = input.read(buf, 0, want)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        pos += n
                        done.addAndGet(n.toLong())
                        onBytes()
                    }
                }
            }
        } catch (e: IOException) {
            // Keep the progress made so far: the caller resumes from the returned position.
            if (pos > from) return pos
            throw e
        } finally {
            conn.disconnect()
        }
        return pos
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
