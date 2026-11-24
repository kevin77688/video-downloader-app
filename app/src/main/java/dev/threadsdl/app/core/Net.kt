package dev.threadsdl.app.core

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Non-2xx response. 4xx (except 408/429) is permanent and not retried. */
class HttpStatusException(val code: Int) : IOException("Server returned HTTP $code") {
    val retryable: Boolean get() = code == 408 || code == 429 || code >= 500
}

/** User-facing failure with a readable message. */
class DownloadException(message: String) : Exception(message)

object Net {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/141.0.0.0 Mobile Safari/537.36"
    const val MAX_RETRIES = 5
    private const val TIMEOUT_MS = 20_000

    fun open(url: String, userAgent: String = BROWSER_UA, followRedirects: Boolean = true): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = followRedirects
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent)
        }

    fun backoffMs(attempt: Int): Long = (1000L shl (attempt - 1).coerceIn(0, 3))

    /** Runs [block], retrying transient I/O failures with exponential backoff. */
    suspend fun <T> retrying(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                if (e is HttpStatusException && !e.retryable) throw e
                if (++attempt > MAX_RETRIES) throw e
                delay(backoffMs(attempt))
            }
        }
    }
}
