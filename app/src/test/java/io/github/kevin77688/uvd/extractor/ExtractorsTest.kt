package io.github.kevin77688.uvd.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Offline checks that each extractor claims (and normalizes) the right links. */
class ExtractorsTest {

    private fun assertMatch(text: String, platform: Platform, url: String) {
        val match = Extractors.find(text)
        assertEquals("platform for $text", platform, match?.platform)
        assertEquals("normalized url for $text", url, match?.url)
    }

    @Test
    fun `threads post links drop query parameters`() {
        assertMatch(
            "https://www.threads.com/@user.name/post/AbCdEfGh123?xmt=abc&slof=1",
            Platform.THREADS,
            "https://www.threads.com/@user.name/post/AbCdEfGh123",
        )
        assertMatch("https://threads.net/@u/post/C0de-1", Platform.THREADS, "https://threads.net/@u/post/C0de-1")
    }

    @Test
    fun `threads share links are accepted`() {
        assertMatch("check this https://www.threads.com/share/AbC-123 !", Platform.THREADS, "https://www.threads.com/share/AbC-123")
    }

    @Test
    fun `unsupported links are ignored`() {
        assertNull(Extractors.find("https://example.com/video.mp4"))
        assertNull(Extractors.find("https://www.threads.com/@user"))
    }
}
