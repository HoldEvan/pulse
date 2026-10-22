package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.snapshotJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HarImporterTest {

    private val untitled = "未分类"

    private fun snapshot(data: String): HttpSnapshot =
        snapshotJson.decodeFromString(HttpSnapshot.serializer(), data)

    @Test
    fun `har entries are restored and grouped by host`() {
        val raw = """
            {
              "log": {
                "version": "1.2",
                "creator": { "name": "PulseRec" },
                "entries": [
                  {
                    "request": {
                      "method": "GET",
                      "url": "https://a.example.com/items?page=1",
                      "headers": [{ "name": "Accept", "value": "application/json" }],
                      "queryString": [{ "name": "page", "value": "1" }]
                    }
                  },
                  {
                    "request": {
                      "method": "POST",
                      "url": "https://a.example.com/items",
                      "headers": [{ "name": "Content-Type", "value": "application/json" }],
                      "postData": { "mimeType": "application/json", "text": "{\"x\":1}" }
                    }
                  },
                  {
                    "request": {
                      "method": "GET",
                      "url": "https://b.example.com/ping"
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        val plan = HarImporter.plan(raw, untitled)
        assertTrue(plan.collectionName.contains("a.example.com") || plan.collectionName.isNotEmpty())
        assertEquals(2, plan.groups.size)
        assertTrue(plan.groups.any { it.tag == "a.example.com" })
        assertTrue(plan.groups.any { it.tag == "b.example.com" })

        val a = plan.groups.first { it.tag == "a.example.com" }
        assertEquals(2, a.items.size)
        val post = a.items.first { it.data.contains("\"POST\"") }
        val snap = snapshot(post.data)
        assertEquals("POST", snap.method)
        assertEquals("https://a.example.com/items", snap.url)
        assertEquals("JSON", snap.bodyType)
        assertEquals("{\"x\":1}", snap.bodyText)
    }

    @Test
    fun `urlencoded body is mapped to form fields`() {
        val raw = """
            {
              "log": {
                "version": "1.2",
                "entries": [
                  {
                    "request": {
                      "method": "POST",
                      "url": "https://a.example.com/login",
                      "postData": {
                        "mimeType": "application/x-www-form-urlencoded",
                        "params": [
                          { "name": "user", "value": "admin" },
                          { "name": "pass", "value": "secret" }
                        ]
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        val plan = HarImporter.plan(raw, untitled)
        val snap = snapshot(plan.groups.single().items.single().data)
        assertEquals("FORM_URLENCODED", snap.bodyType)
        assertEquals("user", snap.formFields[0].name)
        assertEquals("admin", snap.formFields[0].value)
    }

    @Test
    fun `query string is captured into params`() {
        val raw = """
            {
              "log": {
                "version": "1.2",
                "entries": [
                  {
                    "request": {
                      "method": "GET",
                      "url": "https://a.example.com/search?q=cat&page=2",
                      "queryString": [
                        { "name": "q", "value": "cat" },
                        { "name": "page", "value": "2" }
                      ]
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        val plan = HarImporter.plan(raw, untitled)
        val snap = snapshot(plan.groups.single().items.single().data)
        assertEquals(2, snap.params.size)
        assertEquals("q", snap.params[0].key)
        assertEquals("cat", snap.params[0].value)
    }

    @Test
    fun `non har json throws NOT_RECOGNIZED`() {
        val raw = """{ "foo": 1 }""".trimIndent()
        val ex = assertThrows(SpecImportException::class.java) {
            HarImporter.plan(raw, untitled)
        }
        assertEquals(SpecError.NOT_RECOGNIZED, ex.error)
    }

    @Test
    fun `malformed json throws UNPARSEABLE`() {
        val raw = """{ not json """.trimIndent()
        val ex = assertThrows(SpecImportException::class.java) {
            HarImporter.plan(raw, untitled)
        }
        assertEquals(SpecError.UNPARSEABLE, ex.error)
    }
}