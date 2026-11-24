package dev.threadsdl.app.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URL

data class MediaItem(val isVideo: Boolean, val url: String)
data class ThreadsPost(val user: String, val code: String, val items: List<MediaItem>)

/**
 * Extracts media URLs from a public Threads post by parsing the JSON embedded in the post page.
 * Unofficial: if Threads changes its page structure, this is the only class that needs fixing.
 * See PLAN.md "Phase 1 Spike" for how each step was verified.
 */
object ThreadsExtractor {
    private const val MEDIA_TYPE_TEXT = 19
    private val scriptRegex = Regex("""<script type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    suspend fun extract(link: String): ThreadsPost {
        val (user, code) = LinkParser.parsePost(link) ?: Net.retrying { resolveShortLink(link) }
        val html = Net.retrying { fetchPostHtml("https://www.threads.com/@$user/post/$code") }
        val post = findPost(html, code)
            ?: throw DownloadException("Couldn't read this post. It may be private, deleted, or Threads changed its page.")
        return ThreadsPost(user, code, mediaItems(post))
    }

    /** Share links answer non-browser clients with a 302 to the canonical post URL. */
    private fun resolveShortLink(link: String): Pair<String, String> {
        var url = link
        repeat(5) {
            val conn = Net.open(url, userAgent = "ThreadsDL/1.0", followRedirects = false)
            try {
                val status = conn.responseCode
                if (status !in 300..399) {
                    if (status >= 400) throw HttpStatusException(status)
                    throw DownloadException("Couldn't resolve this share link.")
                }
                val location = conn.getHeaderField("Location")
                    ?: throw DownloadException("Couldn't resolve this share link.")
                val next = URL(URL(url), location).toString()
                LinkParser.parsePost(next)?.let { return it }
                url = next
            } finally {
                conn.disconnect()
            }
        }
        throw DownloadException("Too many redirects while resolving the link.")
    }

    /** The page only embeds post JSON when the request looks like a real browser navigation. */
    private fun fetchPostHtml(url: String): String {
        val conn = Net.open(url).apply {
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            setRequestProperty("Sec-Fetch-Mode", "navigate")
            setRequestProperty("Sec-Fetch-Dest", "document")
            setRequestProperty("Sec-Fetch-Site", "none")
            setRequestProperty("Sec-Fetch-User", "?1")
            setRequestProperty("Upgrade-Insecure-Requests", "1")
        }
        try {
            val status = conn.responseCode
            if (status != 200) throw HttpStatusException(status)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun findPost(html: String, code: String): JSONObject? {
        val needle = "\"$code\""
        val candidates = mutableListOf<JSONObject>()
        for (match in scriptRegex.findAll(html)) {
            val body = match.groupValues[1]
            if (!body.contains(needle)) continue
            val root = try {
                JSONTokener(body).nextValue()
            } catch (_: JSONException) {
                continue
            }
            collect(root, code, candidates)
        }
        // The page also contains stubs of the same post; prefer the one that carries media.
        return candidates.firstOrNull { mediaItems(it).isNotEmpty() } ?: candidates.firstOrNull()
    }

    private fun collect(node: Any?, code: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                if (node.optString("code") == code && node.has("media_type")) out += node
                val keys = node.keys()
                while (keys.hasNext()) collect(node.opt(keys.next()), code, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), code, out)
        }
    }

    private fun mediaItems(post: JSONObject): List<MediaItem> {
        if (post.optInt("media_type") == MEDIA_TYPE_TEXT) return emptyList()
        val carousel = post.optJSONArray("carousel_media")
        val nodes = if (carousel != null && carousel.length() > 0) {
            (0 until carousel.length()).mapNotNull { carousel.optJSONObject(it) }
        } else {
            listOf(post)
        }
        return nodes.mapNotNull { node ->
            val video = node.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url").orEmpty()
            when {
                video.isNotEmpty() -> MediaItem(isVideo = true, url = video)
                else -> bestImage(node)?.let { MediaItem(isVideo = false, url = it) }
            }
        }
    }

    private fun bestImage(node: JSONObject): String? {
        val candidates = node.optJSONObject("image_versions2")?.optJSONArray("candidates") ?: return null
        var best: JSONObject? = null
        var bestArea = -1L
        for (i in 0 until candidates.length()) {
            val c = candidates.optJSONObject(i) ?: continue
            val area = c.optLong("width") * c.optLong("height")
            if (area > bestArea && c.optString("url").isNotEmpty()) {
                best = c
                bestArea = area
            }
        }
        return best?.optString("url")
    }
}
