package io.github.kevin77688.uvd.extractor.facebook

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
import org.json.JSONException
import org.json.JSONTokener

/**
 * Public Facebook videos and reels (no login).
 *
 * The video page, requested like a desktop browser navigation, embeds the video object with
 * `browser_native_hd_url` / `browser_native_sd_url` (progressive MP4 with audio, 720p observed)
 * followed by the video `id`. Share links (fb.watch, /share/v/, /share/r/) are fetched as given and
 * redirect to the video page. Photo posts are not supported.
 * Unofficial: if Facebook changes its page structure, this is the only file that needs fixing.
 */
object FacebookExtractor : Extractor {
    override val platform = Platform.FACEBOOK

    private const val TAG = "UVD"
    private const val WATCH = "https://www.facebook.com/watch/?v="
    private const val ID = """(\d{6,25})"""

    private val pageVideoPath = Regex("""^/[^/]+/videos/(?:[^/]+/)*$ID(?:/|$)""")
    private val reelPath = Regex("""^/reel/$ID(?:/|$)""")
    private val watchPath = Regex("""^/(?:watch/?|video\.php)$""")
    private val sharePath = Regex("""^/share/[vr]/[\w-]{5,40}(?:/|$)""")
    private val shortPath = Regex("""^/([\w-]{5,40})/?$""")

    /** A JSON string literal or null, as it appears in the page. */
    private const val JSON_VALUE = """(null|"(?:[^"\\]|\\.)*")"""
    private val hdRegex = Regex(""""browser_native_hd_url":$JSON_VALUE""")
    private val sdRegex = Regex(""""browser_native_sd_url":$JSON_VALUE""")
    private val idRegex = Regex(""""id":"(\d+)"""")
    private val legacyRegex = Regex(""""(?:playable_url_quality_hd|playable_url)":("(?:[^"\\]|\\.)*")""")

    override fun accept(url: String): String? {
        val path = LinkFinder.path(url)
        if (LinkFinder.hostIs(url, "fb.watch")) {
            return shortPath.find(path)?.let { "https://fb.watch/${it.groupValues[1]}/" }
        }
        if (!LinkFinder.hostIs(url, "facebook.com")) return null
        return videoId(url)?.let { if (reelPath.containsMatchIn(path)) "https://www.facebook.com/reel/$it" else WATCH + it }
            ?: sharePath.find(path)?.let { "https://www.facebook.com" + it.value.removeSuffix("/") + "/" }
    }

    /** Video id from a facebook.com video, watch or reel URL; null for share links. */
    private fun videoId(url: String): String? {
        val path = LinkFinder.path(url)
        reelPath.find(path)?.let { return it.groupValues[1] }
        pageVideoPath.find(path)?.let { return it.groupValues[1] }
        if (watchPath.matches(path)) return LinkFinder.queryParam(url, "v")?.takeIf { it.matches(Regex(ID)) }
        return null
    }

    override suspend fun extract(url: String): Extraction {
        val page = Http.retrying { Http.getPage(url, Http.BROWSER_NAVIGATION, Http.DESKTOP_UA) }
        val finalPath = LinkFinder.path(page.url)
        if (finalPath.startsWith("/login") || finalPath.startsWith("/checkpoint")) {
            Log.i(TAG, "facebook: login wall at $finalPath")
            throw UserException(R.string.err_rate_limited, platform.displayName)
        }
        val wanted = videoId(url) ?: videoId(page.url)
        val video = pickVideo(page.body, wanted)
            ?: throw UserException(R.string.err_post_unreadable, platform.displayName)
        return Extraction(platform, title = null, items = listOf(MediaItem(MediaType.VIDEO, MediaSource(video), extension = "mp4")))
    }

    private class Candidate(val id: String?, val hd: String?, val sd: String?)

    /** HD (else SD) URL of video [wanted] when the page names it, else of the first video on the page. */
    private fun pickVideo(html: String, wanted: String?): String? {
        val candidates = hdRegex.findAll(html).map { hd ->
            // Observed layout: ..."browser_native_sd_url":…,"browser_native_hd_url":…,"id":"<video id>"
            val sd = sdRegex.findAll(html.substring(maxOf(0, hd.range.first - 4000), hd.range.first)).lastOrNull()
            val id = idRegex.find(html.substring(hd.range.last + 1, minOf(html.length, hd.range.last + 200)))
            Candidate(id?.groupValues?.get(1), jsonString(hd.groupValues[1]), sd?.let { jsonString(it.groupValues[1]) })
        }.filter { it.hd != null || it.sd != null }.toList()
        val chosen = candidates.firstOrNull { wanted != null && it.id == wanted } ?: candidates.firstOrNull()
        Log.i(TAG, "facebook: ${candidates.size} video(s) on page, wanted=$wanted, chosen=${chosen?.id}, hd=${chosen?.hd != null}")
        chosen?.let { return it.hd ?: it.sd }
        // Older page layout.
        return legacyRegex.find(html)?.let { jsonString(it.groupValues[1]) }
    }

    /** Decodes a JSON string literal (handles \/ and \u escapes); null for `null` or bad input. */
    private fun jsonString(literal: String): String? {
        if (literal == "null") return null
        return try {
            (JSONTokener(literal).nextValue() as? String)?.takeIf { it.startsWith("http") }
        } catch (_: JSONException) {
            null
        }
    }
}
