package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.snapshotJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostmanImporterTest {

    private val untitled = "未分类"

    private fun snapshot(data: String): HttpSnapshot =
        snapshotJson.decodeFromString(HttpSnapshot.serializer(), data)

    @Test
    fun `v21 collection with folders groups requests by folder path`() {
        val raw = """
            {
              "info": { "name": "Team API", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
              "item": [
                {
                  "name": "Users",
                  "item": [
                    {
                      "name": "List",
                      "request": {
                        "method": "GET",
                        "url": {
                          "raw": "https://api.example.com/users?limit=10",
                          "host": ["api.example.com"],
                          "path": ["users"],
                          "query": [{ "key": "limit", "value": "10" }]
                        }
                      }
                    }
                  ]
                },
                {
                  "name": "Ping",
                  "request": {
                    "method": "GET",
                    "url": { "raw": "https://api.example.com/ping" }
                  }
                }
              ]
            }
        """.trimIndent()
        val plan = PostmanImporter.plan(raw, untitled)
        assertEquals("Team API", plan.collectionName)
        assertEquals(2, plan.groups.size)
        val folder = plan.groups.first { it.tag == "Users" }
        val item = folder.items.first()
        val snap = snapshot(item.data)
        assertEquals("List", item.name)
        assertEquals("GET", snap.method)
        assertEquals("https://api.example.com/users?limit=10", snap.url)
        assertEquals("limit", snap.params.single().key)
        assertEquals("10", snap.params.single().value)
        assertTrue(plan.groups.any { it.tag == untitled })
    }

    @Test
    fun `bearer auth is applied to snapshot`() {
        val raw = """
            {
              "info": { "name": "Auth API" },
              "item": [
                {
                  "name": "Me",
                  "request": {
                    "method": "GET",
                    "url": { "raw": "https://api.example.com/me" },
                    "auth": { "type": "bearer", "bearer": [{ "key": "token", "value": "abc123" }] }
                  }
                }
              ]
            }
        """.trimIndent()
        val plan = PostmanImporter.plan(raw, untitled)
        val snap = snapshot(plan.groups.single().items.single().data)
        assertEquals("BEARER", snap.authType)
        assertEquals("abc123", snap.bearerToken)
    }

    @Test
    fun `collection level auth inherits when request lacks auth`() {
        val raw = """
            {
              "info": { "name": "Auth API" },
              "auth": { "type": "basic", "basic": [
                { "key": "username", "value": "u" },
                { "key": "password", "value": "p" }
              ] },
              "item": [
                {
                  "name": "Me",
                  "request": { "method": "GET", "url": { "raw": "https://api.example.com/me" } }
                }
              ]
            }
        """.trimIndent()
        val plan = PostmanImporter.plan(raw, untitled)
        val snap = snapshot(plan.groups.single().items.single().data)
        assertEquals("BASIC", snap.authType)
        assertEquals("u", snap.basicUsername)
        assertEquals("p", snap.basicPassword)
    }

    @Test
    fun `raw json body is preserved and param name falls back to url assembly`() {
        val raw = """
            {
              "info": { "name": "Body API" },
              "item": [
                {
                  "name": "Create",
                  "request": {
                    "method": "POST",
                    "url": { "host": ["api.example.com"], "path": ["items"] },
                    "header": [{ "key": "Content-Type", "value": "application/json" }],
                    "body": { "mode": "raw", "raw": "{\"a\":1}" }
                  }
                }
              ]
            }
        """.trimIndent()
        val plan = PostmanImporter.plan(raw, untitled)
        val snap = snapshot(plan.groups.single().items.single().data)
        assertEquals("POST", snap.method)
        assertEquals("https://api.example.com/items", snap.url)
        assertEquals("RAW", snap.bodyType)
        assertEquals("{\"a\":1}", snap.bodyText)
    }

    @Test
    fun `non postman json throws NOT_RECOGNIZED`() {
        val raw = """{ "whatever": 1 }""".trimIndent()
        val ex = assertThrows(SpecImportException::class.java) {
            PostmanImporter.plan(raw, untitled)
        }
        assertEquals(SpecError.NOT_RECOGNIZED, ex.error)
    }

    @Test
    fun `malformed json throws UNPARSEABLE`() {
        val raw = """{ not json """
        val ex = assertThrows(SpecImportException::class.java) {
            PostmanImporter.plan(raw, untitled)
        }
        assertEquals(SpecError.UNPARSEABLE, ex.error)
    }
}