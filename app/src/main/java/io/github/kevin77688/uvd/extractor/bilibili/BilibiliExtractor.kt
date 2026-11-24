package io.github.kevin77688.uvd.extractor.bilibili

import android.util.Log
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.Http
import io.github.kevin77688.uvd.core.HttpStatusException
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.extractor.Extraction
import io.github.kevin77688.uvd.extractor.Extractor
import io.github.kevin77688.uvd.extractor.LinkFinder
import io.github.kevin77688.uvd.extractor.MediaItem
import io.github.kevin77688.uvd.extractor.MediaSource
import io.github.kevin77688.uvd.extractor.MediaType
import io.github.kevin77688.uvd.extractor.Platform
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Public Bilibili videos (bilibili.com/video/BV… or av…, b23.tv short links), without login.
 *
 * 1. Short links are expanded (dropping the sharer's tracking parameters).
 * 2. The video page embeds `window.__playinfo__` (DASH streams) like a browser sees it.
 * 3. If the page is blocked or lacks it, the public web API is used instead
 *    (view → cid, then playurl, WBI-signed as the website does).
 * Streams are DASH (separate video/audio), merged into one MP4; H.264 is preferred for compatibility.
 * CDN requests need the bilibili.com Referer.
 */
object BilibiliExtractor : Extractor {
    override val platform = Platform.BILIBILI

    private const val TAG = "UVD"
    private const val REFERER = "https://www.bilibili.com/"
    private const val CODEC_AVC = 7
    /** Optional locale prefix (`/zh-Hans/video/…`, `/en/video/…`) and the old `/s/video/…` form. */
    private const val PREFIX = """^/(?:[a-z]{2}(?:-[A-Za-z]{2,4})?/)?(?:s/)?video/"""
    private val bvRegex = Regex(PREFIX + """(BV[0-9A-Za-z]{10})""")
    private val avRegex = Regex(PREFIX + """av(\d+)""", RegexOption.IGNORE_CASE)
    private val shortRegex = Regex("""^/([0-9A-Za-z]{5,12})$""")

    /** CDN downloads must look like they come from the website. */
    private val cdnHeaders = mapOf("Referer" to REFERER, "User-Agent" to Http.DESKTOP_UA)

    override fun accept(url: String): String? {
        val host = LinkFinder.host(url) ?: return null
        val path = "/" + url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
        if (host == "b23.tv" || host == "bili2233.cn") {
            return shortRegex.find(path)?.let { "https://$host/${it.groupValues[1]}" }
        }
        if (host != "bilibili.com") return null
        val id = bvRegex.find(path)?.groupValues?.get(1)
            ?: avRegex.find(path)?.groupValues?.get(1)?.let { "av$it" }
            ?: return null
        val page = LinkFinder.queryParam(url, "p")?.toIntOrNull()?.takeIf { it > 1 }
        return "https://www.bilibili.com/video/$id/" + (page?.let { "?p=$it" } ?: "")
    }

    override suspend fun extract(url: String): Extraction {
        val canonical = if (LinkFinder.hostIs(url, "b23.tv", "bili2233.cn")) expandShortLink(url) else url
        val session = Session()
        val fromPage = runCatching { fromVideoPage(session, canonical) }
            .onFailure { Log.i(TAG, "bilibili: page path failed: $it") }
            .getOrNull()
        val (title, playInfo) = fromPage ?: fromApi(session, canonical)
        val item = pickStreams(playInfo) ?: throw UserException(R.string.err_extract_failed, platform.displayName)
        return Extraction(platform, title, listOf(item))
    }

    /** b23.tv / bili2233.cn answer with a redirect to the full video URL. */
    private suspend fun expandShortLink(url: String): String {
        val resolved = Http.retrying {
            Http.resolveRedirects(url, userAgent = Http.DESKTOP_UA) { accept(it)?.contains("/video/") == true }
        }
        Log.i(TAG, "bilibili: short link -> ${resolved.substringBefore('?')}")
        return accept(resolved)?.takeIf { it.contains("/video/") } ?: throw UserException(R.string.err_short_link)
    }

    // ---------------------------------------------------------------------------------------
    // Path 1: the video page
    // ---------------------------------------------------------------------------------------

    private suspend fun fromVideoPage(session: Session, url: String): Pair<String?, JSONObject> {
        val html = Http.retrying { session.get(url, mapOf("Accept" to "text/html,*/*;q=0.8")) }
        val playInfo = embeddedJson(html, "window.__playinfo__=")?.optJSONObject("data")
            ?: error("no __playinfo__ in page")
        val state = embeddedJson(html, "window.__INITIAL_STATE__=")
        val title = state?.optJSONObject("videoData")?.optString("title")?.takeIf { it.isNotBlank() }
            ?: Regex("""<title[^>]*>([^<]+)""").find(html)?.groupValues?.get(1)
                ?.substringBefore("_哔哩哔哩")?.trim()
        Log.i(TAG, "bilibili: page path ok")
        return title to playInfo
    }

    private fun embeddedJson(html: String, marker: String): JSONObject? {
        val start = html.indexOf(marker).takeIf { it >= 0 } ?: return null
        return runCatching { JSONTokener(html.substring(start + marker.length)).nextValue() as? JSONObject }.getOrNull()
    }

    // ---------------------------------------------------------------------------------------
    // Path 2: the public web API
    // ---------------------------------------------------------------------------------------

    private suspend fun fromApi(session: Session, url: String): Pair<String?, JSONObject> {
        // A visitor id cookie (buvid3) is required, as for any first-time visitor of the website.
        runCatching {
            val spi = api(session, "https://api.bilibili.com/x/frontend/finger/spi")
            spi.optString("b_3").takeIf { it.isNotEmpty() }?.let { session.cookies["buvid3"] = it }
            spi.optString("b_4").takeIf { it.isNotEmpty() }?.let { session.cookies["buvid4"] = it }
        }.onFailure { Log.i(TAG, "bilibili: spi failed: $it") }

        val id = url.substringAfter("/video/").substringBefore('/').substringBefore('?')
        val idParam = if (id.startsWith("av")) "aid=${id.removePrefix("av")}" else "bvid=$id"
        val view = api(session, "https://api.bilibili.com/x/web-interface/view?$idParam")
        val pageNo = LinkFinder.queryParam(url, "p")?.toIntOrNull() ?: 1
        val pages = view.optJSONArray("pages")
        val cid = pages?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optInt("page") == pageNo }?.optLong("cid")
        } ?: view.optLong("cid")
        val bvid = view.optString("bvid")
        val title = view.optString("title").takeIf { it.isNotBlank() }

        val params = linkedMapOf(
            "bvid" to bvid, "cid" to cid.toString(), "qn" to "80", "fnval" to "4048", "fnver" to "0", "fourk" to "1", "try_look" to "1",
        )
        val playInfo = runCatching { api(session, "https://api.bilibili.com/x/player/wbi/playurl?" + Wbi.sign(session, params)) }
            .onFailure { Log.i(TAG, "bilibili: wbi playurl failed: $it") }
            .getOrNull()
            ?: api(session, "https://api.bilibili.com/x/player/playurl?" + Wbi.query(params))
        Log.i(TAG, "bilibili: api path ok")
        return title to playInfo
    }

    /** Calls a JSON API and returns `data`; maps Bilibili error codes to readable failures. */
    private suspend fun api(session: Session, url: String): JSONObject {
        val body = Http.retrying { session.get(url, mapOf("Accept" to "application/json")) }
        val json = JSONObject(body)
        when (val code = json.optInt("code")) {
            0 -> return json.optJSONObject("data") ?: json.optJSONObject("result") ?: JSONObject()
            -404, 62002, 62004, 62012 -> throw UserException(R.string.err_unavailable)
            -403, -10403 -> throw UserException(R.string.err_unavailable)
            -412, -352 -> throw UserException(R.string.err_rate_limited, platform.displayName)
            else -> throw UserException(R.string.err_extract_failed, platform.displayName).also {
                Log.i(TAG, "bilibili: api code $code for ${url.substringBefore('?')}")
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Stream selection
    // ---------------------------------------------------------------------------------------

    private fun pickStreams(playInfo: JSONObject): MediaItem? {
        val dash = playInfo.optJSONObject("dash")
        if (dash != null) {
            val videos = dash.optJSONArray("video").objects()
            val audios = dash.optJSONArray("audio").objects()
            // Prefer H.264 (plays and muxes everywhere), then the highest quality / bitrate.
            val video = videos.filter { it.optInt("codecid") == CODEC_AVC }.ifEmpty { videos }
                .maxWithOrNull(compareBy({ it.optInt("id") }, { it.optInt("height") }, { it.optLong("bandwidth") }))
            val audio = audios.maxByOrNull { it.optLong("bandwidth") }
            Log.i(TAG, "bilibili: offered ${videos.map { it.optInt("id") to it.optInt("codecid") }.distinct()}, accept=${playInfo.optJSONArray("accept_quality")}")
            Log.i(TAG, "bilibili: video id=${video?.optInt("id")} ${video?.optInt("height")}p codec=${video?.optInt("codecid")}, audio=${audio?.optInt("id")}")
            if (video != null && audio != null) {
                return MediaItem(MediaType.VIDEO, MediaSource(streamUrl(video), cdnHeaders), MediaSource(streamUrl(audio), cdnHeaders), "mp4")
            }
            if (video != null) return MediaItem(MediaType.VIDEO, MediaSource(streamUrl(video), cdnHeaders), extension = "mp4")
        }
        // Older progressive format: a single file only when the video isn't split into segments.
        val durl = playInfo.optJSONArray("durl").objects()
        if (durl.size == 1) {
            return MediaItem(MediaType.VIDEO, MediaSource(durl[0].optString("url"), cdnHeaders), extension = "mp4")
        }
        return null
    }

    private fun streamUrl(stream: JSONObject): String =
        stream.optString("baseUrl").ifEmpty { stream.optString("base_url") }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    // ---------------------------------------------------------------------------------------
    // HTTP session with a tiny cookie jar (visitor cookies only; never a login)
    // ---------------------------------------------------------------------------------------

    private class Session {
        val cookies = linkedMapOf<String, String>()

        fun get(url: String, extra: Map<String, String>): String {
            val headers = buildMap {
                put("Referer", REFERER)
                put("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                putAll(extra)
                if (cookies.isNotEmpty()) put("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            val conn = Http.open(url, headers, userAgent = Http.DESKTOP_UA)
            try {
                val status = conn.responseCode
                conn.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                    .flatMap { it.value }
                    .forEach { c ->
                        val pair = c.substringBefore(';')
                        val name = pair.substringBefore('=').trim()
                        if (name.isNotEmpty()) cookies[name] = pair.substringAfter('=', "").trim()
                    }
                if (status != 200) throw HttpStatusException(status)
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** WBI request signing used by the website's player API (public algorithm, no credentials). */
    private object Wbi {
        private val MIXIN = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39,
            12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63,
            57, 62, 11, 36, 20, 34, 44, 52,
        )

        suspend fun sign(session: Session, params: Map<String, String>): String {
            val nav = JSONObject(Http.retrying { session.get("https://api.bilibili.com/x/web-interface/nav", emptyMap()) })
            val img = nav.optJSONObject("data")?.optJSONObject("wbi_img") ?: error("no wbi_img")
            val raw = fileStem(img.optString("img_url")) + fileStem(img.optString("sub_url"))
            require(raw.length >= 64) { "bad wbi keys" }
            val mixin = String(CharArray(32) { raw[MIXIN[it]] })
            val signed = params + ("wts" to (System.currentTimeMillis() / 1000).toString())
            val q = query(signed.toSortedMap())
            return "$q&w_rid=${md5(q + mixin)}"
        }

        fun query(params: Map<String, String>): String = params.entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v.filterNot { it in "!'()*" })}"
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        private fun fileStem(url: String) = url.substringAfterLast('/').substringBefore('.')

        private fun md5(s: String): String =
            MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
