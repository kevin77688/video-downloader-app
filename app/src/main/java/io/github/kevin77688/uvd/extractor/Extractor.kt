package io.github.kevin77688.uvd.extractor

enum class MediaType { IMAGE, VIDEO, AUDIO }

/** One HTTP resource plus any headers the CDN insists on (e.g. Referer). */
data class MediaSource(val url: String, val headers: Map<String, String> = emptyMap())

/**
 * One file to save.
 * When [audio] is set, [source] is a video-only stream and both are merged into one file
 * (DASH-style sites). [extension] is a hint used when the server's MIME type is unhelpful.
 */
data class MediaItem(
    val type: MediaType,
    val source: MediaSource,
    val audio: MediaSource? = null,
    val extension: String? = null,
)

/** Result of reading one link: everything downloadable behind it, in display order. */
data class Extraction(val platform: Platform, val title: String?, val items: List<MediaItem>)

/**
 * Reads one site. Implementations live in `extractor/<platform>/` and nothing else in the app
 * knows about a site's URLs or page structure — when a site changes, only its extractor changes.
 * Register new extractors in [Extractors.all].
 */
interface Extractor {
    val platform: Platform

    /** Normalized form of [url] when this extractor handles it, else null. Must not do network I/O. */
    fun accept(url: String): String?

    /** Network-bound; call off the main thread. Throws `UserException` for user-facing failures. */
    suspend fun extract(url: String): Extraction
}
