package com.evan.pulse.data.cache

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheCleanerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var tmpFile: File

    @Before
    fun setUp() {
        cacheDir = folder.newFolder("cache")
        tmpFile = File(folder.root, "app_data.json.tmp")
    }

    @Test
    fun measure_of_empty_list_is_zero() {
        assertEquals(CacheStats(0, 0L), CacheCleaner.measure(emptyList()))
    }

    @Test
    fun measure_of_missing_paths_is_zero() {
        val missing = File(folder.root, "no-such-file")
        assertEquals(CacheStats(0, 0L), CacheCleaner.measure(listOf(missing)))
    }

    @Test
    fun measure_counts_files_recursively() {
        val nested = File(cacheDir, "sub/deep/file.txt")
        nested.parentFile?.mkdirs()
        nested.writeText("abcd")
        File(cacheDir, "top.bin").writeText("xy")
        tmpFile.writeText("012345")

        assertEquals(CacheStats(3, 12L), CacheCleaner.measure(listOf(cacheDir, tmpFile)))
    }

    @Test
    fun measure_ignores_empty_directories() {
        File(cacheDir, "empty/nested").mkdirs()
        assertEquals(CacheStats(0, 0L), CacheCleaner.measure(listOf(cacheDir)))
    }

    @Test
    fun measure_ignores_empty_files_in_count_of_bytes() {
        File(cacheDir, "empty.txt").writeText("")
        assertEquals(CacheStats(1, 0L), CacheCleaner.measure(listOf(cacheDir)))
    }

    @Test
    fun formatBytes_switches_units_at_binary_boundaries() {
        assertEquals("512 B", CacheCleaner.formatBytes(512))
        assertEquals("1 B", CacheCleaner.formatBytes(1))
        assertEquals(
            String.format(Locale.getDefault(), "%.1f KB", 1536 / 1024.0),
            CacheCleaner.formatBytes(1536)
        )
        assertEquals(
            String.format(Locale.getDefault(), "%.1f MB", 2 * 1024 * 1024 / (1024.0 * 1024.0)),
            CacheCleaner.formatBytes(2L * 1024 * 1024)
        )
    }

    @Test
    fun formatBytes_keeps_negative_and_zero_as_bytes() {
        assertEquals("0 B", CacheCleaner.formatBytes(0))
        assertEquals("-1 B", CacheCleaner.formatBytes(-1))
    }
}
