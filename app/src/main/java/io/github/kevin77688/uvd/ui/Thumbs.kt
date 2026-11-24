package io.github.kevin77688.uvd.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache

/** Tiny thumbnail loader (no image library) with an in-memory LRU cache. Call off the main thread. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun load(context: Context, uri: Uri, isVideo: Boolean, sizePx: Int): Bitmap? {
        val key = "$uri@$sizePx"
        cache.get(key)?.let { return it }
        val bitmap = runCatching {
            if (isVideo) videoFrame(context, uri, sizePx) else image(context, uri, sizePx)
        }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun image(context: Context, uri: Uri, sizePx: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val sample = minOf(info.size.width, info.size.height) / sizePx
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }

    /** Many videos fade in from black, so use the first key frame after ~1 s (or mid-way for short clips). */
    private const val PREVIEW_AT_US = 1_000_000L

    private fun videoFrame(context: Context, uri: Uri, sizePx: Int): Bitmap? =
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            val durationUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            val atUs = minOf(PREVIEW_AT_US, durationUs / 2)
            (if (atUs > 0) r.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_NEXT_SYNC, sizePx, sizePx) else null)
                ?: r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
                ?: r.frameAtTime
        }
}
