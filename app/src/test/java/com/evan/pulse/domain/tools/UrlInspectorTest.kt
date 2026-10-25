package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInspectorTest {

    @Test
    fun fullUrlIsSplitIntoEveryPart() {
        val parsed = UrlInspector.parse(
            "https://alice:s3cret@api.example.com:8443/v1/users/7?page=2&size=20#profile"
        )
        assertEquals("https", parsed.scheme)
        assertEquals("alice:s3cret", parsed.userInfo)
        assertEquals("alice", parsed.user)
        assertEquals("s3cret", parsed.password)
        assertEquals("api.example.com", parsed.host)
        assertEquals(8443, parsed.port)
        assertEquals("/v1/users/7", parsed.path)
        assertEquals(2, parsed.params.size)
        assertEquals(QueryParam("page", "2"), parsed.params[0])
        assertEquals(QueryParam("size", "20"), parsed.params[1])
        assertEquals("profile", parsed.fragment)
        assertNull(parsed.error)
        assertFalse(parsed.schemeInferred)
    }

    @Test
    fun defaultsAreAppliedForMinimalUrl() {
        val parsed = UrlInspector.parse("https://example.com")
        assertEquals("example.com", parsed.host)
        assertEquals("", parsed.path)
        assertNull(parsed.port)
        assertTrue(parsed.params.isEmpty())
        assertNull(parsed.error)
    }

    @Test
    fun missingSchemeIsInferredAsHttp() {
        val parsed = UrlInspector.parse("example.com/a?x=1")
        assertEquals("http", parsed.scheme)
        assertTrue(parsed.schemeInferred)
        assertEquals("example.com", parsed.host)
        assertEquals("/a", parsed.path)
        assertEquals("http://example.com/a?x=1", parsed.rebuild())
    }

    @Test
    fun ipv6LiteralKeepsBracketsAndPort() {
        val parsed = UrlInspector.parse("http://[2001:db8::1]:8080/ping")
        assertEquals("[2001:db8::1]", parsed.host)
        assertEquals(8080, parsed.port)
        assertEquals("/ping", parsed.path)
        assertEquals("http://[2001:db8::1]:8080/ping", parsed.rebuild())
    }

    @Test
    fun ipv6LiteralWithoutPortIsAccepted() {
        val parsed = UrlInspector.parse("http://[::1]/status")
        assertEquals("[::1]", parsed.host)
        assertNull(parsed.port)
        assertNull(parsed.error)
    }

    @Test
    fun unclosedIpv6BracketReportsInvalid() {
        assertEquals("invalid", UrlInspector.parse("http://[::1/ping").error)
    }

    @Test
    fun opaqueSchemeKeepsRestAsPath() {
        val parsed = UrlInspector.parse("mailto:dev@example.com")
        assertEquals("mailto", parsed.scheme)
        assertEquals("dev@example.com", parsed.path)
        assertEquals("", parsed.host)
        assertNull(parsed.error)
    }

    @Test
    fun outOfRangePortIsReported() {
        assertEquals("port", UrlInspector.parse("http://example.com:70000/").error)
        assertEquals("port", UrlInspector.parse("http://example.com:0/").error)
    }

    @Test
    fun nonNumericPortIsReported() {
        val parsed = UrlInspector.parse("http://example.com:abc/")
        assertEquals("port", parsed.error)
        assertNull(parsed.port)
        assertEquals("example.com", parsed.host)
    }

    @Test
    fun emptyHostIsReported() {
        assertEquals("host", UrlInspector.parse("http:///path").error)
    }

    @Test
    fun blankInputIsReported() {
        assertEquals("empty", UrlInspector.parse("   ").error)
    }

    @Test
    fun queryParamsKeepOrderAndRepeats() {
        val parsed = UrlInspector.parse("http://h/p?tag=a&tag=b&flag&empty=")
        assertEquals(
            listOf(
                QueryParam("tag", "a"),
                QueryParam("tag", "b"),
                QueryParam("flag", ""),
                QueryParam("empty", "")
            ),
            parsed.params
        )
        assertTrue(parsed.hasDuplicateParams)
    }

    @Test
    fun paramsArePercentDecoded() {
        val parsed = UrlInspector.parse("http://h/p?q=a%20b&c=x+y&cn=%E4%B8%AD%E6%96%87")
        assertEquals("a b", parsed.params[0].decodedValue)
        assertEquals("x y", parsed.params[1].decodedValue)
        assertEquals("中文", parsed.params[2].decodedValue)
    }

    @Test
    fun invalidPercentSequenceIsKeptAsIs() {
        assertEquals("100%zz", UrlInspector.decode("100%zz"))
        assertEquals("%", UrlInspector.decode("%"))
    }

    @Test
    fun fragmentWithoutQueryIsSplit() {
        val parsed = UrlInspector.parse("https://example.com/doc#section-2")
        assertEquals("/doc", parsed.path)
        assertTrue(parsed.params.isEmpty())
        assertEquals("section-2", parsed.fragment)
        assertEquals("https://example.com/doc#section-2", parsed.rebuild())
    }

    @Test
    fun userInfoWithoutPasswordIsSupported() {
        val parsed = UrlInspector.parse("ftp://user@files.example.com/pub")
        assertEquals("user", parsed.user)
        assertEquals("", parsed.password)
        assertTrue(parsed.hasUserInfo)
        assertEquals("files.example.com", parsed.host)
    }

    @Test
    fun rebuildMatchesInputForLosslessUrl() {
        val raw = "https://alice:pw@h.example:9443/a/b?x=1&y=%20&z#frag"
        assertEquals(raw, UrlInspector.parse(raw).rebuild())
    }

    @Test
    fun schemeIsLowercasedButHostKeepsCase() {
        val parsed = UrlInspector.parse("HTTPS://Example.COM/Path")
        assertEquals("https", parsed.scheme)
        assertEquals("Example.COM", parsed.host)
        assertEquals("/Path", parsed.path)
    }

    @Test
    fun spacesInPathAreTolerated() {
        val parsed = UrlInspector.parse("https://example.com/a b/c")
        assertEquals("example.com", parsed.host)
        assertEquals("/a b/c", parsed.path)
        assertNull(parsed.error)
    }
}
