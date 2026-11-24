package io.github.kevin77688.uvd.extractor

/**
 * A supported site. [id] is stable: it is written into every downloaded file name
 * (`<id>_yyyyMMdd_HHmmss.ext`) and read back by the gallery filter, so never change it.
 */
enum class Platform(val id: String, val displayName: String, val brandColor: Long) {
    THREADS("threads", "Threads", 0xFF6E6E6E),
    YOUTUBE("youtube", "YouTube", 0xFFE62117),
    BILIBILI("bilibili", "Bilibili", 0xFF00A1D6),
    INSTAGRAM("instagram", "Instagram", 0xFFE1306C),
    FACEBOOK("facebook", "Facebook", 0xFF1877F2),
    ;

    companion object {
        fun fromId(id: String): Platform? = entries.firstOrNull { it.id == id }
    }
}
