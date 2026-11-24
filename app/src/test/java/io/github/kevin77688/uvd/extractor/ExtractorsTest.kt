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
    fun `instagram posts, reels and tv links normalize to post urls`() {
        val canonical = "https://www.instagram.com/p/AbC-dEf_GhI/"
        assertMatch("https://www.instagram.com/p/AbC-dEf_GhI/?igsh=MWxyZ2Fk&img_index=1", Platform.INSTAGRAM, canonical)
        assertMatch("https://instagram.com/reel/AbC-dEf_GhI", Platform.INSTAGRAM, canonical)
        assertMatch("https://www.instagram.com/reels/AbC-dEf_GhI/", Platform.INSTAGRAM, canonical)
        assertMatch("https://www.instagram.com/tv/AbC-dEf_GhI/", Platform.INSTAGRAM, canonical)
        assertMatch("https://www.instagram.com/some.user/p/AbC-dEf_GhI/", Platform.INSTAGRAM, canonical)
        assertMatch("https://www.instagram.com/some_user/reel/AbC-dEf_GhI/?utm_source=ig_web_copy_link", Platform.INSTAGRAM, canonical)
        assertMatch("https://instagr.am/p/AbC-dEf_GhI", Platform.INSTAGRAM, canonical)
        assertMatch("Check this out! https://www.instagram.com/reel/AbC-dEf_GhI/?igsh=x", Platform.INSTAGRAM, canonical)
    }

    @Test
    fun `instagram share links are kept for redirect`() {
        assertMatch("https://www.instagram.com/share/AbCd1234Ef", Platform.INSTAGRAM, "https://www.instagram.com/share/AbCd1234Ef/")
        assertMatch("https://www.instagram.com/share/reel/AbCd1234Ef/?x=1", Platform.INSTAGRAM, "https://www.instagram.com/share/reel/AbCd1234Ef/")
    }

    @Test
    fun `instagram profiles, stories and audio pages are ignored`() {
        assertNull(Extractors.find("https://www.instagram.com/some.user/"))
        assertNull(Extractors.find("https://www.instagram.com/some.user/reels/"))
        assertNull(Extractors.find("https://www.instagram.com/stories/some.user/1234567890123456789/"))
        assertNull(Extractors.find("https://www.instagram.com/reels/audio/1234567890123456/"))
        assertNull(Extractors.find("https://www.instagram.com/explore/"))
    }

    @Test
    fun `facebook video links normalize to watch urls`() {
        val canonical = "https://www.facebook.com/watch/?v=1234567890123456"
        assertMatch("https://www.facebook.com/SomePage/videos/some-video-title/1234567890123456/", Platform.FACEBOOK, canonical)
        assertMatch("https://www.facebook.com/SomePage/videos/1234567890123456", Platform.FACEBOOK, canonical)
        assertMatch("https://www.facebook.com/watch/?v=1234567890123456&ref=sharing", Platform.FACEBOOK, canonical)
        assertMatch("https://m.facebook.com/watch?v=1234567890123456", Platform.FACEBOOK, canonical)
        assertMatch("https://web.facebook.com/video.php?v=1234567890123456", Platform.FACEBOOK, canonical)
    }

    @Test
    fun `facebook reels and share links`() {
        assertMatch("https://www.facebook.com/reel/1234567890123456?mibextid=abc", Platform.FACEBOOK, "https://www.facebook.com/reel/1234567890123456")
        assertMatch("https://fb.watch/aBcD3eFgH1/?mibextid=x", Platform.FACEBOOK, "https://fb.watch/aBcD3eFgH1/")
        assertMatch("https://www.facebook.com/share/v/1AbCdEfGh2/", Platform.FACEBOOK, "https://www.facebook.com/share/v/1AbCdEfGh2/")
        assertMatch("https://www.facebook.com/share/r/1AbCdEfGh2", Platform.FACEBOOK, "https://www.facebook.com/share/r/1AbCdEfGh2/")
    }

    @Test
    fun `facebook pages, posts and photos are ignored`() {
        assertNull(Extractors.find("https://www.facebook.com/SomePage"))
        assertNull(Extractors.find("https://www.facebook.com/SomePage/posts/1234567890123456"))
        assertNull(Extractors.find("https://www.facebook.com/photo/?fbid=1234567890123456"))
        assertNull(Extractors.find("https://www.facebook.com/share/p/1AbCdEfGh2/"))
        assertNull(Extractors.find("https://www.facebook.com/watch/?v=notanid"))
        assertNull(Extractors.find("https://www.facebook.com/SomePage/videos/"))
    }

    @Test
    fun `unsupported links are ignored`() {
        assertNull(Extractors.find("https://example.com/video.mp4"))
        assertNull(Extractors.find("https://www.threads.com/@user"))
    }
}
