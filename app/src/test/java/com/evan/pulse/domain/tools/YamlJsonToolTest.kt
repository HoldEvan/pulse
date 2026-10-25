package com.evan.pulse.domain.tools

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlJsonToolTest {

    private fun toJson(yaml: String) = YamlJsonTool.yamlToJson(yaml)

    private fun toYaml(json: String) = YamlJsonTool.jsonToYaml(json)

    private fun sameJson(a: String, b: String): Boolean =
        Json.parseToJsonElement(a) == Json.parseToJsonElement(b)

    @Test
    fun yamlScalarsKeepTheirTypes() {
        val result = toJson(
            """
            name: pulse
            enabled: true
            retries: 3
            ratio: 1.5
            missing: null
            """.trimIndent()
        )
        assertTrue(result.error == null)
        val parsed = Json.parseToJsonElement(result.text)
        assertTrue(sameJson(parsed.toString(), """{"name":"pulse","enabled":true,"retries":3,"ratio":1.5,"missing":null}"""))
    }

    @Test
    fun yamlNestedMappingBecomesNestedObject() {
        val result = toJson(
            """
            server:
              host: example.com
              ports:
                - 80
                - 443
            """.trimIndent()
        )
        assertTrue(
            sameJson(
                result.text,
                """{"server":{"host":"example.com","ports":[80,443]}}"""
            )
        )
    }

    @Test
    fun yamlListOfMappingsBecomesArrayOfObjects() {
        val result = toJson(
            """
            - id: 1
              name: a
            - id: 2
              name: b
            """.trimIndent()
        )
        assertTrue(
            sameJson(result.text, """[{"id":1,"name":"a"},{"id":2,"name":"b"}]""")
        )
    }

    @Test
    fun yamlQuotedScalarStaysString() {
        val result = toJson("""value: "123"""")
        assertEquals("""{"value":"123"}""", result.text.replace(Regex("\\s"), ""))
    }

    @Test
    fun yamlRootArrayIsAllowed() {
        val result = toJson("- a\n- b")
        assertTrue(sameJson(result.text, """["a","b"]"""))
    }

    @Test
    fun yamlRootScalarIsAllowed() {
        val result = toJson("42")
        assertEquals("42", result.text)
    }

    @Test
    fun yamlAnchorsAndAliasesResolve() {
        val result = toJson(
            """
            base: &b
              host: local
            copy: *b
            """.trimIndent()
        )
        assertTrue(
            sameJson(result.text, """{"base":{"host":"local"},"copy":{"host":"local"}}""")
        )
    }

    @Test
    fun invalidYamlReportsError() {
        assertEquals("yamlInvalid", toJson("a: [1, 2").error)
    }

    @Test
    fun blankInputReportsEmpty() {
        assertEquals("empty", toJson("   ").error)
        assertEquals("empty", toYaml("").error)
    }

    @Test
    fun jsonObjectEmitsBlockMapping() {
        val result = toYaml("""{"name":"pulse","retries":3}""")
        assertEquals("name: pulse\nretries: 3", result.text)
    }

    @Test
    fun nestedJsonUsesTwoSpaceIndent() {
        val result = toYaml("""{"server":{"host":"example.com","ports":[80,443]}}""")
        assertEquals(
            """
            server:
              host: example.com
              ports:
                - 80
                - 443
            """.trimIndent(),
            result.text
        )
    }

    @Test
    fun arrayOfObjectsEmitsDashThenIndentedKeys() {
        val result = toYaml("""[{"id":1},{"id":2}]""")
        assertEquals("-\n  id: 1\n-\n  id: 2", result.text)
    }

    @Test
    fun ambiguousStringsAreQuoted() {
        val result = toYaml("""{"a":"true","b":"123","c":"a: b","d":"#x","e":"null"}""")
        assertEquals(
            """
            a: "true"
            b: "123"
            c: "a: b"
            d: "#x"
            e: "null"
            """.trimIndent(),
            result.text
        )
    }

    @Test
    fun multilineStringsAreEscaped() {
        val result = toYaml("""{"a":"line1\nline2"}""")
        assertEquals("""a: "line1\nline2"""", result.text)
    }

    @Test
    fun emptyContainersStayInline() {
        val result = toYaml("""{"a":{},"b":[]}""")
        assertEquals("a: {}\nb: []", result.text)
    }

    @Test
    fun rootArrayEmitsDashList() {
        val result = toYaml("""["a","b"]""")
        assertEquals("- a\n- b", result.text)
    }

    @Test
    fun rootScalarIsEmittedAsIs() {
        assertEquals("42", toYaml("42").text)
        assertEquals("pulse", toYaml(""""pulse"""").text)
    }

    @Test
    fun roundTripPreservesStructure() {
        val original = """
            {
              "server": {"host": "example.com", "ports": [80, 443], "tls": true},
              "tags": ["a", "b c", "true"],
              "empty": {},
              "nil": null
            }
        """.trimIndent()
        val yaml = toYaml(original)
        assertFalse(yaml.text.isEmpty())
        val back = toJson(yaml.text)
        assertTrue(sameJson(original, back.text))
    }

    @Test
    fun invalidJsonReportsError() {
        assertEquals("jsonInvalid", toYaml("{oops}").error)
    }

    @Test
    fun deeplyNestedJsonReportsDepthError() {
        val depth = 200
        val nested = "[".repeat(depth) + "1" + "]".repeat(depth)
        assertEquals("depth", toYaml(nested).error)
    }
}
