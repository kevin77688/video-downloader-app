package io.github.kevin77688.uvd.storage

import io.github.kevin77688.uvd.extractor.Platform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File naming: `<platform>_<yyyyMMdd_HHmmss>[_n].<ext>`, using the download time.
 * Posts with several items add `_1`, `_2`, …. The platform prefix drives the gallery filter.
 */
object FileNames {
    private val legacyName = Regex("""^\d{8}_\d{6}""")

    /** Shared base name for every file of one download. */
    fun base(platform: Platform, time: Date = Date()): String =
        platform.id + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(time)

    fun name(base: String, index: Int, total: Int, extension: String): String =
        base + (if (total > 1) "_${index + 1}" else "") + "." + extension

    /**
     * Platform a file came from, read from its name prefix. Un-prefixed names
     * (`20261120_153012.jpg`, from Threads Downloader 1.x) are Threads. Unknown files: null.
     */
    fun platformOf(fileName: String): Platform? {
        Platform.fromId(fileName.substringBefore('_').lowercase())?.let { return it }
        return if (legacyName.containsMatchIn(fileName)) Platform.THREADS else null
    }
}
