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
    fun `youtube links normalize to watch urls`() {
        val canonical = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertMatch("https://youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEfGhIjKlMnOp", Platform.YOUTUBE, canonical)
        assertMatch("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ", Platform.YOUTUBE, canonical)
        assertMatch("https://youtu.be/dQw4w9WgXcQ?si=xyz", Platform.YOUTUBE, canonical)
        assertMatch("https://www.youtube.com/shorts/dQw4w9WgXcQ", Platform.YOUTUBE, canonical)
        assertMatch("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD", Platform.YOUTUBE, canonical)
        assertMatch("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", Platform.YOUTUBE, canonical)
        assertMatch("youtu.be/dQw4w9WgXcQ", Platform.YOUTUBE, canonical)
    }

    @Test
    fun `youtube channels and playlists are not single videos`() {
        assertNull(Extractors.find("https://www.youtube.com/@SomeChannel"))
        assertNull(Extractors.find("https://www.youtube.com/playlist?list=PL123"))
        assertNull(Extractors.find("https://www.youtube.com/watch?v=short"))
    }

    @Test
    fun `bilibili links normalize to video urls`() {
        val canonical = "https://www.bilibili.com/video/BV1aB2cD3eF4/"
        assertMatch("https://www.bilibili.com/video/BV1aB2cD3eF4/?share_source=copy_web&vd_source=abc", Platform.BILIBILI, canonical)
        assertMatch("https://m.bilibili.com/video/BV1aB2cD3eF4", Platform.BILIBILI, canonical)
        assertMatch("https://www.bilibili.com/zh-Hans/video/BV1aB2cD3eF4/", Platform.BILIBILI, canonical)
        assertMatch("https://www.bilibili.com/en/video/BV1aB2cD3eF4", Platform.BILIBILI, canonical)
        assertMatch("https://www.bilibili.com/video/BV1aB2cD3eF4?p=1", Platform.BILIBILI, canonical)
        assertMatch("https://www.bilibili.com/video/BV1aB2cD3eF4?p=3&t=10", Platform.BILIBILI, "$canonical?p=3")
        assertMatch("https://www.bilibili.com/video/av170001", Platform.BILIBILI, "https://www.bilibili.com/video/av170001/")
    }

    @Test
    fun `bilibili short links are found in share text`() {
        assertMatch("【某个标题】（本片由某某制作） https://b23.tv/aB3xYz9", Platform.BILIBILI, "https://b23.tv/aB3xYz9")
        assertMatch("https://bili2233.cn/aB3xYz9?share_medium=android", Platform.BILIBILI, "https://bili2233.cn/aB3xYz9")
    }

    @Test
    fun `bilibili non-video pages are ignored`() {
        assertNull(Extractors.find("https://space.bilibili.com/12345"))
        assertNull(Extractors.find("https://www.bilibili.com/"))
        assertNull(Extractors.find("https://www.bilibili.com/video/BVshort"))
    }

    @Test
    fun `unsupported links are ignored`() {
        assertNull(Extractors.find("https://example.com/video.mp4"))
        assertNull(Extractors.find("https://www.threads.com/@user"))
    }
}
