package com.evan.pulse.data.backup

import com.evan.pulse.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoBackupTest {

    private fun settings(
        enabled: Boolean = true,
        url: String = "https://dav.example.com",
        path: String = "pulse",
    ) = AppSettings(
        autoBackupEnabled = enabled,
        webdavUrl = url,
        webdavPath = path,
        webdavUsername = "u",
        webdavPassword = "p"
    )

    @Test
    fun `disabled auto backup yields no config`() {
        assertNull(AutoBackupRunner.webDavConfigOf(settings(enabled = false)))
    }

    @Test
    fun `blank address yields no config even when enabled`() {
        assertNull(AutoBackupRunner.webDavConfigOf(settings(url = "   ")))
    }

    @Test
    fun `enabled settings map to a webdav config`() {
        val config = AutoBackupRunner.webDavConfigOf(settings())
        assertEquals("https://dav.example.com", config?.url)
        assertEquals("pulse", config?.path)
        assertEquals("u", config?.username)
        assertEquals("p", config?.password)
    }

    @Test
    fun `blank credentials become null instead of empty strings`() {
        val config = AutoBackupRunner.webDavConfigOf(
            AppSettings(autoBackupEnabled = true, webdavUrl = "https://h", webdavUsername = "  ")
        )
        assertNull(config?.username)
        assertNull(config?.password)
    }

    @Test
    fun `interval hours fall back to daily when the index is out of range`() {
        assertEquals(6, AutoBackupRunner.intervalHours(0))
        assertEquals(24, AutoBackupRunner.intervalHours(2))
        assertEquals(24, AutoBackupRunner.intervalHours(-1))
        assertEquals(24, AutoBackupRunner.intervalHours(99))
    }
}
