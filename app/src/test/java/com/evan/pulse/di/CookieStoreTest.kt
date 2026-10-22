package com.evan.pulse.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieStoreTest {

    private fun store(saves: MutableList<Int> = mutableListOf()): CookieStore {
        val handle = PersistenceHandle()
        var count = 0
        handle.action = { count++; saves.add(count) }
        return CookieStore(handle)
    }

    private val cookie = StoredCookie(
        name = "token",
        value = "abc",
        domain = "api.example.com",
        path = "/"
    )

    @Test
    fun `exact host match`() {
        val s = store()
        s.upsert(1L, cookie)
        assertEquals(listOf(cookie), s.forHost(1L, "api.example.com"))
        assertEquals(listOf(cookie), s.forHost(1L, "API.EXAMPLE.COM"))
    }

    @Test
    fun `parent domain match requires dot boundary`() {
        val s = store()
        s.upsert(1L, cookie.copy(domain = "example.com"))
        assertEquals(listOf(cookie.copy(domain = "example.com")), s.forHost(1L, "api.example.com"))
        // 相邻前缀不算父域：evilexample.com 不该命中 example.com 的 Cookie
        assertTrue(s.forHost(1L, "evilexample.com").isEmpty())
    }

    @Test
    fun `exact match takes precedence over parent domain`() {
        val s = store()
        s.upsert(1L, cookie.copy(domain = "example.com", value = "parent"))
        s.upsert(1L, cookie.copy(value = "exact"))
        val hostCookies = s.forHost(1L, "api.example.com")
        assertEquals("exact", hostCookies.first { it.domain == "api.example.com" }.value)
        assertEquals("parent", hostCookies.first { it.domain == "example.com" }.value)
    }

    @Test
    fun `projects are isolated`() {
        val s = store()
        s.upsert(1L, cookie)
        assertTrue(s.forHost(2L, "api.example.com").isEmpty())
        assertTrue(s.active(2L).isEmpty())
    }

    @Test
    fun `upsert overrides same name and path only`() {
        val s = store()
        s.upsert(1L, cookie)
        s.upsert(1L, cookie.copy(value = "new"))
        assertEquals(1, s.active(1L)["api.example.com"]?.size)
        assertEquals("new", s.forHost(1L, "api.example.com").single().value)
        // 同名不同 path 视为不同 Cookie，两者共存
        s.upsert(1L, cookie.copy(path = "/v2"))
        assertEquals(2, s.active(1L)["api.example.com"]?.size)
    }

    @Test
    fun `expired cookies are filtered`() {
        val s = store()
        s.upsert(1L, cookie.copy(expiresAtMs = 1L))
        assertTrue(s.active(1L).isEmpty())
        assertTrue(s.forHost(1L, "api.example.com").isEmpty())
        // 数据仍保留，供管理页展示
        assertEquals(1, s.snapshot().projects[1L]?.get("api.example.com")?.size)
    }

    @Test
    fun `remove deletes named cookie and prunes empty domain`() {
        val s = store()
        s.upsert(1L, cookie)
        s.upsert(1L, cookie.copy(name = "sid"))
        s.remove(1L, "api.example.com", "token")
        assertEquals(listOf("sid"), s.active(1L)["api.example.com"]?.map { it.name })
        s.remove(1L, "api.example.com", "sid")
        assertNull(s.snapshot().projects[1L]?.get("api.example.com"))
    }

    @Test
    fun `clearHost and clearProject`() {
        val s = store()
        s.upsert(1L, cookie)
        s.upsert(1L, cookie.copy(domain = "other.com"))
        s.clearHost(1L, "api.example.com")
        assertTrue(s.active(1L)["api.example.com"].isNullOrEmpty())
        s.clearProject(1L)
        assertTrue(s.active(1L).isEmpty())
        // 不存在的项目清空是安全空操作
        s.clearProject(99L)
    }

    @Test
    fun `mutations persist`() {
        val saves = mutableListOf<Int>()
        val s = store(saves)
        val before = saves.size
        s.upsert(1L, cookie)
        s.remove(1L, "api.example.com", "token")
        s.clearProject(1L)
        assertEquals(before + 3, saves.size)
    }

    @Test
    fun `snapshot restore round trip`() {
        val s = store()
        s.upsert(1L, cookie)
        val snapshot = s.snapshot()
        val restored = store()
        restored.restore(snapshot)
        assertEquals(listOf(cookie), restored.forHost(1L, "api.example.com"))
        assertFalse(restored.forHost(1L, "api.example.com").isEmpty())
    }

    @Test
    fun `blank name or domain rejected`() {
        val s = store()
        s.upsert(1L, cookie.copy(name = " "))
        s.upsert(1L, cookie.copy(domain = ""))
        assertTrue(s.active(1L).isEmpty())
    }
}
