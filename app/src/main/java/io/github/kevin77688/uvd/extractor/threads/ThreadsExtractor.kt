package io.github.kevin77688.uvd.extractor.threads

import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.Http
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.extractor.Extraction
import io.github.kevin77688.uvd.extractor.Extractor
import io.github.kevin77688.uvd.extractor.MediaItem
import io.github.kevin77688.uvd.extractor.MediaSource
import io.github.kevin77688.uvd.extractor.MediaType
import io.github.kevin77688.uvd.extractor.Platform
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Public Threads posts (threads.com / threads.net), parsed from the JSON embedded in the post page.
 * Unofficial: if Threads changes its page structure, this is the only file that needs fixing.
 * See PLAN.md "Threads spike" for how each step was verified.
 */
object ThreadsExtractor : Extractor {
    override val platform = Platform.THREADS

    private const val MEDIA_TYPE_TEXT = 19
    private val linkRegex = Regex(
        """^https?://(?:www\.)?threads\.(?:com|net)/(?:@[\w.]+/post/[\w-]+|share/[\w-]+|t/[\w-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val postRegex = Regex("""threads\.(?:com|net)/@([\w.]+)/post/([\w-]+)""", RegexOption.IGNORE_CASE)
    private val scriptRegex = Regex("""<script type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /** Drops the query string (tracking parameters) and anything after the post code. */
    override fun accept(url: String): String? = linkRegex.find(url)?.value

    override suspend fun extract(url: String): Extraction {
        val (user, code) = parsePost(url) ?: Http.retrying { resolveShareLink(url) }
        val html = Http.retrying { Http.getText("https://www.threads.com/@$user/post/$code", BROWSER_NAVIGATION) }
        val post = findPost(html, code) ?: throw UserException(R.string.err_threads_unreadable)
        return Extraction(platform, title = "@$user", items = mediaItems(post))
    }

    /** (username, post code) for canonical post URLs; null for share / short links. */
    private fun parsePost(url: String): Pair<String, String>? =
        postRegex.find(url)?.let { it.groupValues[1] to it.groupValues[2] }

    /** Share links answer non-browser clients with a 302 to the canonical post URL. */
    private fun resolveShareLink(url: String): Pair<String, String> {
        val resolved = Http.resolveRedirects(url, userAgent = "UVD/1.0") { parsePost(it) != null }
        return parsePost(resolved) ?: throw UserException(R.string.err_short_link)
    }

    /** The page only embeds post JSON when the request looks like a real browser navigation. */
    private val BROWSER_NAVIGATION = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "zh-TW,zh;q=0.9,en;q=0.8",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
    )

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

    /** Recursively gathers objects for post [code] — the page also holds replies and suggestions. */
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

    /**
     * The post's own media; for a text post that shows another post inline (for example a shared
     * Instagram reel, in `text_post_app_info.linked_inline_media`), that post's media.
     */
    private fun mediaItems(post: JSONObject): List<MediaItem> = ownMedia(post).ifEmpty {
        post.optJSONObject("text_post_app_info")?.optJSONObject("linked_inline_media")?.let(::ownMedia).orEmpty()
    }

    /** media_type: 1 image, 2 video, 8 carousel (see carousel_media), 19 text only. */
    private fun ownMedia(post: JSONObject): List<MediaItem> {
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
                video.isNotEmpty() -> MediaItem(MediaType.VIDEO, MediaSource(video), extension = "mp4")
                else -> bestImage(node)?.let { MediaItem(MediaType.IMAGE, MediaSource(it), extension = "jpg") }
            }
        }
    }

    /** Largest candidate by pixel area (the original upload). */
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
