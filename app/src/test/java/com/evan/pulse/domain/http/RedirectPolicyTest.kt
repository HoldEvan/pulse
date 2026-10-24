package com.evan.pulse.domain.http

import com.evan.pulse.domain.model.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectPolicyTest {

    @Test
    fun `only 3xx can be followed`() {
        assertTrue(RedirectPolicy.canFollow(301))
        assertTrue(RedirectPolicy.canFollow(308))
        assertFalse(RedirectPolicy.canFollow(200))
        assertFalse(RedirectPolicy.canFollow(404))
    }

    @Test
    fun `relative location resolves against the current url`() {
        val target = RedirectPolicy.next(
            "https://api.example.com/v1/users",
            "/v2/users",
            HttpMethod.GET,
            302
        )
        assertEquals("https://api.example.com/v2/users", target?.url)
    }

    @Test
    fun `303 always switches to GET and drops the body`() {
        val target = RedirectPolicy.next(
            "https://h/a",
            "https://h/b",
            HttpMethod.PUT,
            303
        )
        assertEquals(HttpMethod.GET, target?.method)
        assertEquals(true, target?.dropBody)
    }

    @Test
    fun `301 and 302 only downgrade POST`() {
        val post = RedirectPolicy.next("https://h/a", "/b", HttpMethod.POST, 301)
        assertEquals(HttpMethod.GET, post?.method)
        assertEquals(true, post?.dropBody)

        val put = RedirectPolicy.next("https://h/a", "/b", HttpMethod.PUT, 302)
        assertEquals(HttpMethod.PUT, put?.method)
        assertEquals(false, put?.dropBody)
    }

    @Test
    fun `307 and 308 keep method and body`() {
        val target = RedirectPolicy.next("https://h/a", "/b", HttpMethod.POST, 307)
        assertEquals(HttpMethod.POST, target?.method)
        assertEquals(false, target?.dropBody)
        assertEquals(false, target?.dropAuthorization)
    }

    @Test
    fun `cross host redirects drop authorization`() {
        val sameHost = RedirectPolicy.next("https://h/a", "https://h/b", HttpMethod.GET, 302)
        assertEquals(false, sameHost?.dropAuthorization)

        val otherHost = RedirectPolicy.next("https://h/a", "https://evil.example/b", HttpMethod.GET, 302)
        assertEquals(true, otherHost?.dropAuthorization)

        val otherScheme = RedirectPolicy.next("https://h/a", "http://h/b", HttpMethod.GET, 302)
        assertEquals(true, otherScheme?.dropAuthorization)
    }

    @Test
    fun `non http schemes and blank locations are rejected`() {
        assertNull(RedirectPolicy.next("https://h/a", "ftp://h/b", HttpMethod.GET, 302))
        assertNull(RedirectPolicy.next("https://h/a", "", HttpMethod.GET, 302))
    }
}
