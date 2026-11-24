package dev.threadsdl.app.core

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

    private fun videoFrame(context: Context, uri: Uri, sizePx: Int): Bitmap? =
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
                ?: r.frameAtTime
        }
}
