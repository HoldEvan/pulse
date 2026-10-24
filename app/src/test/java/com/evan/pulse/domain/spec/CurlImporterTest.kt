package com.evan.pulse.domain.spec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlImporterTest {

    @Test
    fun `simple GET keeps url and method`() {
        val result = CurlImporter.parse("curl https://api.example.com/users")
        assertEquals("GET", result.snapshot.method)
        assertEquals("https://api.example.com/users", result.snapshot.url)
        assertEquals("users", result.name)
    }

    @Test
    fun `headers method and json body are parsed`() {
        val result = CurlImporter.parse(
            """curl -X POST 'https://api.example.com/users' \
               -H 'Content-Type: application/json' \
               -H 'Authorization: Bearer t' \
               -d '{"name":"ann"}'"""
        )
        assertEquals("POST", result.snapshot.method)
        assertEquals("JSON", result.snapshot.bodyType)
        assertEquals("""{"name":"ann"}""", result.snapshot.bodyText)
        assertTrue(result.snapshot.headers.any { it.key == "Authorization" && it.value == "Bearer t" })
    }

    @Test
    fun `query string is split into editable params`() {
        val result = CurlImporter.parse("curl 'https://h/api?page=2&q=a%20b'")
        assertEquals("https://h/api", result.snapshot.url)
        assertEquals(listOf("page" to "2", "q" to "a b"), result.snapshot.params.map { it.key to it.value })
    }

    @Test
    fun `data implies POST when no method is given`() {
        val result = CurlImporter.parse("curl https://h/api -d 'a=1'")
        assertEquals("POST", result.snapshot.method)
        assertEquals("RAW", result.snapshot.bodyType)
        assertEquals("a=1", result.snapshot.bodyText)
    }

    @Test
    fun `form fields and basic auth are recognised`() {
        val result = CurlImporter.parse(
            "curl -u alice:secret https://h/api -F 'name=ann' -F 'avatar=@/tmp/a.png'"
        )
        assertEquals("POST", result.snapshot.method)
        assertEquals("FORM_DATA", result.snapshot.bodyType)
        assertEquals("BASIC", result.snapshot.authType)
        assertEquals("alice", result.snapshot.basicUsername)
        assertEquals("secret", result.snapshot.basicPassword)
        assertTrue(result.snapshot.formFields.any { it.name == "avatar" && it.isFile })
    }

    @Test
    fun `urlencoded data becomes form urlencoded body`() {
        val result = CurlImporter.parse(
            "curl https://h/api --data-urlencode 'q=hello world' --data-urlencode 'page=1'"
        )
        assertEquals("FORM_URLENCODED", result.snapshot.bodyType)
        assertEquals("q=hello world&page=1", result.snapshot.bodyText)
    }

    @Test
    fun `follow redirects and insecure flags map to settings`() {
        val result = CurlImporter.parse("curl -L -k --max-redirs 3 https://h/api")
        assertTrue(result.snapshot.followRedirects)
        assertTrue(result.snapshot.insecureTls)
        assertEquals(3, result.snapshot.maxRedirects)
    }

    @Test
    fun `defaults keep redirect following off like curl`() {
        val result = CurlImporter.parse("curl https://h/api")
        assertFalse(result.snapshot.followRedirects)
        assertEquals(10, result.snapshot.maxRedirects)
    }

    @Test
    fun `get flag moves data into the query`() {
        val result = CurlImporter.parse("curl -G https://h/api -d 'q=hi'")
        assertEquals("GET", result.snapshot.method)
        assertEquals("NONE", result.snapshot.bodyType)
        assertEquals(listOf("q" to "hi"), result.snapshot.params.map { it.key to it.value })
    }

    @Test
    fun `cookie flag becomes a header`() {
        val result = CurlImporter.parse("curl -b 'sid=abc; theme=dark' https://h/api")
        assertTrue(result.snapshot.headers.any { it.key == "Cookie" && it.value == "sid=abc; theme=dark" })
    }

    @Test
    fun `tokenizer handles quotes and line continuations`() {
        val tokens = CurlImporter.tokenize(
            "curl -H 'X: a b' \\\n  -d \"{\\\"k\\\":\\\"v\\\"}\" https://h"
        )
        assertEquals(listOf("curl", "-H", "X: a b", "-d", "{\"k\":\"v\"}", "https://h"), tokens)
    }

    @Test
    fun `blank input and non curl commands are rejected`() {
        val empty = runCatching { CurlImporter.parse("   ") }.exceptionOrNull()
        assertTrue(empty is SpecImportException)
        assertEquals(SpecError.EMPTY, (empty as SpecImportException).error)

        val wrong = runCatching { CurlImporter.parse("wget https://h") }.exceptionOrNull()
        assertTrue(wrong is SpecImportException)
        assertEquals(SpecError.NOT_RECOGNIZED, (wrong as SpecImportException).error)
    }

    @Test
    fun `command without url is rejected`() {
        val error = runCatching { CurlImporter.parse("curl -X POST") }.exceptionOrNull()
        assertTrue(error is SpecImportException)
        assertEquals(SpecError.NOT_RECOGNIZED, (error as SpecImportException).error)
    }
}
