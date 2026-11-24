package io.github.kevin77688.uvd.extractor.youtube

import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.extractor.Extraction
import io.github.kevin77688.uvd.extractor.Extractor
import io.github.kevin77688.uvd.extractor.LinkFinder
import io.github.kevin77688.uvd.extractor.MediaItem
import io.github.kevin77688.uvd.extractor.MediaSource
import io.github.kevin77688.uvd.extractor.MediaType
import io.github.kevin77688.uvd.extractor.Platform
import io.github.kevin77688.uvd.extractor.newpipe.NewPipeBridge
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * YouTube videos, Shorts and youtu.be links, via NewPipeExtractor (handles stream URL deciphering).
 * Picks the best H.264 video-only stream + best original-language AAC audio (merged into one MP4,
 * up to 1080p, plays everywhere), falling back to the best muxed progressive stream.
 */
object YouTubeExtractor : Extractor {
    override val platform = Platform.YOUTUBE

    private val idRegex = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val pathId = Regex("""^/(?:shorts|live|embed|v|e)/([A-Za-z0-9_-]{11})""")

    override fun accept(url: String): String? {
        val host = LinkFinder.host(url) ?: return null
        val path = "/" + url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
        val id = when {
            host == "youtu.be" -> path.removePrefix("/").substringBefore('/')
            host == "youtube.com" || host == "music.youtube.com" || host == "youtube-nocookie.com" ->
                if (path == "/watch") LinkFinder.queryParam(url, "v") else pathId.find(path)?.groupValues?.get(1)
            else -> null
        } ?: return null
        return if (idRegex.matches(id)) "https://www.youtube.com/watch?v=$id" else null
    }

    override suspend fun extract(url: String): Extraction {
        val info = NewPipeBridge.call(platform) { StreamInfo.getInfo(ServiceList.YouTube, url) }
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            throw UserException(R.string.err_live)
        }

        val video = info.videoOnlyStreams.filter(::isDownloadable)
            .filter { it.format == MediaFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc1") }
            .maxWithOrNull(compareBy<VideoStream>({ pixels(it) }, { it.fps }, { it.bitrate }))
        val audio = info.audioStreams.filter(::isDownloadable)
            .filter { it.format == MediaFormat.M4A && isOriginalTrack(it) }
            .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
        val muxed = info.videoStreams.filter(::isDownloadable)
            .maxWithOrNull(compareBy<VideoStream>({ pixels(it) }, { it.bitrate }))

        val item = when {
            video != null && audio != null && pixels(video) > (muxed?.let(::pixels) ?: 0) ->
                MediaItem(MediaType.VIDEO, source(video), audio = source(audio), extension = "mp4")
            muxed != null ->
                MediaItem(MediaType.VIDEO, source(muxed), extension = muxed.format?.suffix ?: "mp4")
            audio != null ->
                MediaItem(MediaType.AUDIO, source(audio), extension = "m4a")
            else -> throw UserException(R.string.err_extract_failed, platform.displayName)
        }
        return Extraction(platform, title = info.name, items = listOf(item))
    }

    private fun isDownloadable(s: Stream) = s.isUrl && s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    private fun pixels(s: VideoStream): Int = if (s.width > 0 && s.height > 0) s.width * s.height else s.height

    private fun isOriginalTrack(s: AudioStream): Boolean =
        s.audioTrackType == null || s.audioTrackType == AudioTrackType.ORIGINAL

    /** googlevideo URLs check that the request looks like the client the URL was issued for. */
    private fun source(s: Stream): MediaSource {
        val url = s.content
        val headers = when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
                mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
            YoutubeParsingHelper.isWebStreamingUrl(url) -> mapOf(
                "User-Agent" to NewPipeBridge.USER_AGENT,
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/",
            )
            else -> mapOf("User-Agent" to NewPipeBridge.USER_AGENT)
        }
        return MediaSource(url, headers)
    }
}
