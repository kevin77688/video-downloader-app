package io.github.kevin77688.uvd.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.UserException
import java.io.File
import java.nio.ByteBuffer

/**
 * Combines a video-only file and an audio-only file (DASH-style sites) into one MP4 by copying the
 * compressed samples — no re-encoding, no FFmpeg; just the platform's MediaExtractor/MediaMuxer.
 * Supports what MP4 muxing supports on Android: H.264/H.265 (+AV1 on Android 14+) with AAC audio.
 */
object MediaMerger {
    private const val DEFAULT_BUFFER = 4 * 1024 * 1024

    fun merge(video: File, audio: File, out: File) {
        val videoEx = MediaExtractor()
        val audioEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoEx.setDataSource(video.path)
            audioEx.setDataSource(audio.path)
            val videoTrack = selectTrack(videoEx, "video/")
            val audioTrack = selectTrack(audioEx, "audio/")
            val videoFormat = videoEx.getTrackFormat(videoTrack)
            val audioFormat = audioEx.getTrackFormat(audioTrack)

            muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            }
            val outVideo = muxer.addTrack(videoFormat)
            val outAudio = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxOf(maxInputSize(videoFormat), maxInputSize(audioFormat)))
            val info = MediaCodec.BufferInfo()
            // Interleave by timestamp so the output plays smoothly from the start.
            var videoDone = false
            var audioDone = false
            while (!videoDone || !audioDone) {
                val takeVideo = !videoDone && (audioDone || videoEx.sampleTime <= audioEx.sampleTime)
                val ex = if (takeVideo) videoEx else audioEx
                if (!writeSample(ex, muxer, if (takeVideo) outVideo else outAudio, buffer, info)) {
                    if (takeVideo) videoDone = true else audioDone = true
                }
            }
            muxer.stop()
        } catch (e: UserException) {
            throw e
        } catch (e: Exception) {
            Log.w(SegmentedDownloader.TAG, "merge failed", e)
            throw UserException(R.string.err_merge)
        } finally {
            runCatching { muxer?.release() }
            videoEx.release()
            audioEx.release()
        }
    }

    /** Copies one sample; returns false at end of stream. */
    private fun writeSample(ex: MediaExtractor, muxer: MediaMuxer, track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo): Boolean {
        buffer.clear()
        val size = ex.readSampleData(buffer, 0)
        if (size < 0) return false
        info.offset = 0
        info.size = size
        info.presentationTimeUs = ex.sampleTime
        info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        muxer.writeSampleData(track, buffer, info)
        ex.advance()
        return true
    }

    private fun selectTrack(ex: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(mimePrefix)) {
                ex.selectTrack(i)
                return i
            }
        }
        throw UserException(R.string.err_merge)
    }

    private fun maxInputSize(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxOf(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), DEFAULT_BUFFER)
        } else {
            DEFAULT_BUFFER
        }
}
