package io.github.kevin77688.uvd.extractor.instagram

import android.util.Log
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.Http
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.extractor.Extraction
import io.github.kevin77688.uvd.extractor.Extractor
import io.github.kevin77688.uvd.extractor.LinkFinder
import io.github.kevin77688.uvd.extractor.MediaItem
import io.github.kevin77688.uvd.extractor.MediaSource
import io.github.kevin77688.uvd.extractor.MediaType
import io.github.kevin77688.uvd.extractor.Platform
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Public Instagram posts, reels and carousels (no login), parsed from the JSON embedded in the post page.
 *
 * The page of a public post embeds the post object (`code`, `media_type`, `image_versions2`,
 * `video_versions`, `carousel_media`) when it is requested like a desktop browser navigation.
 * Videos use the progressive MP4 in `video_versions` (H.264 + AAC, 720p observed); the 1080p DASH
 * streams are VP9, which can't be merged into MP4 on every supported Android version.
 * Unofficial: if Instagram changes its page structure, this is the only file that needs fixing.
 */
object InstagramExtractor : Extractor {
    override val platform = Platform.INSTAGRAM

    private const val TAG = "UVD"
    private const val MEDIA_TYPE_CAROUSEL = 8

    /** `/p/CODE`, `/reel/CODE`, `/reels/CODE`, `/tv/CODE`, optionally after a username. */
    private val postPath = Regex("""^/(?:[\w.]+/)?(?:p|reels?|tv)/([\w-]{8,48})(?:/|$)""")

    /** Links from the app's share button; they redirect to the post. */
    private val sharePath = Regex("""^/share/(?:(?:p|reels?)/)?[\w-]{5,48}(?:/|$)""")

    override fun accept(url: String): String? {
        if (!LinkFinder.hostIs(url, "instagram.com", "instagr.am")) return null
        val path = LinkFinder.path(url)
        sharePath.find(path)?.let { return "https://www.instagram.com" + it.value.removeSuffix("/") + "/" }
        return postPath.find(path)?.let { "https://www.instagram.com/p/${it.groupValues[1]}/" }
    }

    override suspend fun extract(url: String): Extraction {
        val page = Http.retrying { Http.getPage(url, Http.BROWSER_NAVIGATION, Http.DESKTOP_UA) }
        val finalPath = LinkFinder.path(page.url)
        if (finalPath.startsWith("/accounts/login") || finalPath.startsWith("/challenge")) {
            Log.i(TAG, "instagram: login wall at $finalPath")
            throw UserException(R.string.err_rate_limited, platform.displayName)
        }
        val code = postPath.find(finalPath)?.groupValues?.get(1)
            ?: postPath.find(LinkFinder.path(url))?.groupValues?.get(1)
            ?: throw UserException(R.string.err_short_link)
        val post = findPost(page.body, code)
            ?: throw UserException(R.string.err_post_unreadable, platform.displayName)
        val items = mediaItems(post)
        Log.i(TAG, "instagram: media_type=${post.optInt("media_type")}, ${items.size} item(s)")
        if (items.isEmpty()) throw UserException(R.string.err_no_media)
        val user = post.optJSONObject("user")?.optString("username")?.takeIf { it.isNotEmpty() }
        return Extraction(platform, title = user?.let { "@$it" }, items = items)
    }

    /**
     * The post object for [code]. The page holds the full object plus stubs (e.g. a carousel with
     * `carousel_media_count` but no `carousel_media`), so take the candidate with the most items.
     */
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
        return candidates
            .filterNot { it.optInt("media_type") == MEDIA_TYPE_CAROUSEL && (it.optJSONArray("carousel_media")?.length() ?: 0) == 0 }
            .maxByOrNull { mediaItems(it).size }
            ?.takeIf { mediaItems(it).isNotEmpty() }
    }

    private val scriptRegex = Regex("""<script type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /** Recursively gathers objects for post [code] — the page also holds other posts and suggestions. */
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

    /** media_type: 1 image, 2 video, 8 carousel (items in carousel_media, each an image or a video). */
    private fun mediaItems(post: JSONObject): List<MediaItem> {
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

    /**
     * The original image: the largest candidate when sizes are given; otherwise the first one, which
     * Instagram lists as the full-size image (smaller ones carry a size such as `s1080x1080` in the URL).
     */
    private fun bestImage(node: JSONObject): String? {
        val array = node.optJSONObject("image_versions2")?.optJSONArray("candidates") ?: return null
        val candidates = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .filter { it.optString("url").isNotEmpty() }
        val sized = candidates.filter { it.optLong("width") > 0 && it.optLong("height") > 0 }
        val best = sized.maxByOrNull { it.optLong("width") * it.optLong("height") } ?: candidates.firstOrNull()
        return best?.optString("url")
    }
}
