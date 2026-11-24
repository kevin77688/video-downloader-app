package io.github.kevin77688.uvd.extractor

/**
 * A supported site. [id] is stable: it is written into every downloaded file name
 * (`<id>_yyyyMMdd_HHmmss.ext`) and read back by the gallery filter, so never change it.
 */
enum class Platform(val id: String, val displayName: String, val brandColor: Long) {
    THREADS("threads", "Threads", 0xFF6E6E6E),
    ;

    companion object {
        fun fromId(id: String): Platform? = entries.firstOrNull { it.id == id }
    }
}
