package io.github.kevin77688.uvd.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkFinderTest {

    @Test
    fun `finds url inside CJK share text`() {
        val text = "【影片标题】（作者亲自制作） https://b23.tv/aB3xYz9"
        assertEquals(listOf("https://b23.tv/aB3xYz9"), LinkFinder.urls(text))
    }

    @Test
    fun `stops at CJK punctuation directly after url`() {
        assertEquals(listOf("https://example.com/a"), LinkFinder.urls("看這個https://example.com/a，很好笑"))
    }

    @Test
    fun `trims trailing sentence punctuation and unbalanced brackets`() {
        assertEquals(listOf("https://example.com/x"), LinkFinder.urls("(see https://example.com/x)."))
        assertEquals(listOf("https://en.wikipedia.org/wiki/A_(b)"), LinkFinder.urls("https://en.wikipedia.org/wiki/A_(b)"))
    }

    @Test
    fun `finds several urls in order`() {
        assertEquals(
            listOf("https://a.com/1", "https://b.com/2"),
            LinkFinder.urls("one https://a.com/1 two https://b.com/2"),
        )
    }

    @Test
    fun `falls back to scheme-less links`() {
        assertEquals(listOf("https://youtu.be/abc"), LinkFinder.urls("youtu.be/abc"))
        assertTrue(LinkFinder.urls("no links here").isEmpty())
        assertTrue(LinkFinder.urls(null).isEmpty())
    }

    @Test
    fun `host strips www and mobile prefixes`() {
        assertEquals("youtube.com", LinkFinder.host("https://www.youtube.com/watch?v=x"))
        assertEquals("youtube.com", LinkFinder.host("https://m.youtube.com/watch?v=x"))
        assertEquals("bilibili.com", LinkFinder.host("https://www.bilibili.com:443/video/BV1"))
        assertNull(LinkFinder.host("not a url"))
    }

    @Test
    fun `hostIs matches domain and subdomains only`() {
        assertTrue(LinkFinder.hostIs("https://music.youtube.com/x", "youtube.com"))
        assertTrue(LinkFinder.hostIs("https://youtube.com/x", "youtube.com"))
        assertFalse(LinkFinder.hostIs("https://notyoutube.com/x", "youtube.com"))
    }

    @Test
    fun `queryParam reads a single parameter`() {
        val url = "https://youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEfGhIjKlMnOp#t=3"
        assertEquals("dQw4w9WgXcQ", LinkFinder.queryParam(url, "v"))
        assertEquals("AbCdEfGhIjKlMnOp", LinkFinder.queryParam(url, "si"))
        assertNull(LinkFinder.queryParam(url, "list"))
    }
}
