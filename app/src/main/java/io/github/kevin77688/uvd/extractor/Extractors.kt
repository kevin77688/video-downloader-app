package io.github.kevin77688.uvd.extractor

import io.github.kevin77688.uvd.extractor.bilibili.BilibiliExtractor
import io.github.kevin77688.uvd.extractor.facebook.FacebookExtractor
import io.github.kevin77688.uvd.extractor.instagram.InstagramExtractor
import io.github.kevin77688.uvd.extractor.threads.ThreadsExtractor
import io.github.kevin77688.uvd.extractor.youtube.YouTubeExtractor

/** Registry of every supported site. Order matters only when two extractors accept the same URL. */
object Extractors {
    val all: List<Extractor> = listOf(
        ThreadsExtractor,
        YouTubeExtractor,
        BilibiliExtractor,
        InstagramExtractor,
        FacebookExtractor,
    )

    val platforms: List<Platform> get() = all.map { it.platform }.distinct()

    /** A supported link found in some text, already normalized by its extractor. */
    data class Match(val url: String, val extractor: Extractor) {
        val platform: Platform get() = extractor.platform
    }

    /** First supported link in [text] (share text, clipboard, user input), or null. */
    fun find(text: CharSequence?): Match? {
        for (url in LinkFinder.urls(text)) {
            for (e in all) e.accept(url)?.let { return Match(it, e) }
        }
        return null
    }
}
