package io.github.kevin77688.uvd.core

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Non-2xx response. 4xx (except 408/429) is permanent and not retried. */
class HttpStatusException(val code: Int) : IOException("Server returned HTTP $code") {
    val retryable: Boolean get() = code == 408 || code == 429 || code >= 500
}

/** Minimal HTTP helpers on top of [HttpURLConnection] (no HTTP library, to keep the APK small). */
object Http {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/141.0.0.0 Mobile Safari/537.36"
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/141.0.0.0 Safari/537.36"
    const val MAX_RETRIES = 5
    private const val TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5

    fun open(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = BROWSER_UA,
        followRedirects: Boolean = true,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = followRedirects
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("User-Agent", userAgent)
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }

    /** GETs [url] and returns the body; throws [HttpStatusException] unless the server answers 200. */
    fun getText(url: String, headers: Map<String, String> = emptyMap(), userAgent: String = BROWSER_UA): String {
        val conn = open(url, headers, userAgent)
        try {
            val status = conn.responseCode
            if (status != 200) throw HttpStatusException(status)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Request headers of a top-level page load in a desktop browser (some sites only embed data then). */
    val BROWSER_NAVIGATION = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
    )

    /** A fetched page: the URL it ended at after redirects, and its body. */
    class Page(val url: String, val body: String)

    /** GETs [url] following redirects; throws [HttpStatusException] unless the final answer is 200. */
    fun getPage(url: String, headers: Map<String, String> = emptyMap(), userAgent: String = BROWSER_UA): Page {
        val conn = open(url, headers, userAgent)
        try {
            val status = conn.responseCode
            if (status != 200) throw HttpStatusException(status)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return Page(conn.url.toString(), body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Follows redirects one hop at a time until [done] accepts a URL (or a non-redirect answer
     * arrives) and returns that URL. Used to expand share/short links without downloading pages.
     */
    fun resolveRedirects(url: String, userAgent: String, done: (String) -> Boolean): String {
        var current = url
        repeat(MAX_REDIRECTS) {
            if (done(current)) return current
            val conn = open(current, userAgent = userAgent, followRedirects = false)
            try {
                val status = conn.responseCode
                if (status !in 300..399) {
                    if (status >= 400) throw HttpStatusException(status)
                    return current
                }
                val location = conn.getHeaderField("Location") ?: return current
                current = URL(URL(current), location).toString()
            } finally {
                conn.disconnect()
            }
        }
        return current
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
