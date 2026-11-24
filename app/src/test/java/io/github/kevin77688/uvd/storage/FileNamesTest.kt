package io.github.kevin77688.uvd.storage

import io.github.kevin77688.uvd.extractor.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class FileNamesTest {

    private val time = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 24, 15, 30, 12) }.time

    @Test
    fun `base name is platform plus download time`() {
        assertEquals("threads_20261124_153012", FileNames.base(Platform.THREADS, time))
    }

    @Test
    fun `multi item posts get a 1-based counter`() {
        val base = FileNames.base(Platform.THREADS, time)
        assertEquals("threads_20261124_153012.mp4", FileNames.name(base, 0, 1, "mp4"))
        assertEquals("threads_20261124_153012_2.jpg", FileNames.name(base, 1, 3, "jpg"))
    }

    @Test
    fun `platform is read back from the prefix`() {
        for (p in Platform.entries) {
            assertEquals(p, FileNames.platformOf(FileNames.name(FileNames.base(p, time), 0, 1, "mp4")))
        }
    }

    @Test
    fun `legacy un-prefixed names count as threads, others are unknown`() {
        assertEquals(Platform.THREADS, FileNames.platformOf("20261120_153012_1.jpg"))
        assertNull(FileNames.platformOf("IMG_0001.jpg"))
        assertNull(FileNames.platformOf("holiday.mp4"))
    }
}
