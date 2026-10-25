package com.evan.pulse.domain.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSchemaValidatorTest {

    private fun report(schema: String, data: String) =
        JsonSchemaValidator.validate(schema, data)

    private fun obj(schema: String): JsonObject =
        Json.parseToJsonElement(schema) as JsonObject

    private fun run(schema: String, data: String) =
        JsonSchemaValidator.validate(obj(schema), Json.parseToJsonElement(data))

    @Test
    fun validObjectPasses() {
        val result = run(
            """{"type":"object","required":["id"],"properties":{"id":{"type":"integer"}}}""",
            """{"id":1}"""
        )
        assertTrue(result.valid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun rootTypeMismatchReportsActualType() {
        val result = run("""{"type":"array"}""", """{"a":1}""")
        assertFalse(result.valid)
        assertEquals(1, result.violations.size)
        assertEquals("$", result.violations[0].path)
        assertEquals("type", result.violations[0].keyword)
        assertEquals("array", result.violations[0].expected)
        assertEquals("object", result.violations[0].actual)
    }

    @Test
    fun typeMismatchStopsDeeperKeywords() {
        val result = run(
            """{"type":"string","minLength":10}""",
            """123"""
        )
        assertEquals(listOf("type"), result.violations.map { it.keyword })
    }

    @Test
    fun missingRequiredPointsAtProperty() {
        val result = run(
            """{"type":"object","required":["name","age"]}""",
            """{"name":"a"}"""
        )
        assertEquals(1, result.violations.size)
        assertEquals("$.age", result.violations[0].path)
        assertEquals("required", result.violations[0].keyword)
        assertEquals("age", result.violations[0].expected)
    }

    @Test
    fun nestedPropertyPathUsesDots() {
        val result = run(
            """{"type":"object","properties":{"user":{"type":"object","properties":{"age":{"type":"integer"}}}}}""",
            """{"user":{"age":"x"}}"""
        )
        assertEquals("$.user.age", result.violations[0].path)
        assertEquals("type", result.violations[0].keyword)
    }

    @Test
    fun nonIdentifierKeyUsesBracketPath() {
        val result = run(
            """{"type":"object","properties":{"user name":{"type":"string"}}}""",
            """{"user name":1}"""
        )
        assertEquals("$['user name']", result.violations[0].path)
    }

    @Test
    fun enumRejectsValueOutsideList() {
        val result = run(
            """{"enum":["a","b"]}""",
            """"c""""
        )
        assertEquals("enum", result.violations[0].keyword)
        assertEquals("\"a\", \"b\"", result.violations[0].expected)
    }

    @Test
    fun constRejectsDifferentValue() {
        val result = run("""{"const":7}""", """8""")
        assertEquals("const", result.violations[0].keyword)
        assertEquals("7", result.violations[0].expected)
    }

    @Test
    fun additionalPropertiesFalseRejectsUnknownKey() {
        val result = run(
            """{"type":"object","properties":{"id":{}},"additionalProperties":false}""",
            """{"id":1,"extra":true}"""
        )
        assertEquals(1, result.violations.size)
        assertEquals("$.extra", result.violations[0].path)
        assertEquals("additionalProperties", result.violations[0].keyword)
    }

    @Test
    fun additionalPropertiesSchemaValidatesUnknownKey() {
        val result = run(
            """{"type":"object","additionalProperties":{"type":"string"}}""",
            """{"a":1}"""
        )
        assertEquals("$.a", result.violations[0].path)
        assertEquals("type", result.violations[0].keyword)
    }

    @Test
    fun stringLengthAndPatternAreChecked() {
        val short = run("""{"minLength":3}""", """"ab"""")
        assertEquals("minLength", short.violations[0].keyword)
        assertEquals("3", short.violations[0].expected)

        val long = run("""{"maxLength":2}""", """"abc"""")
        assertEquals("maxLength", long.violations[0].keyword)

        val pattern = run("""{"pattern":"^[0-9]+$"}""", """"12a"""")
        assertEquals("pattern", pattern.violations[0].keyword)
        assertEquals("^[0-9]+$", pattern.violations[0].expected)
    }

    @Test
    fun invalidPatternIsSchemaError() {
        val result = run("""{"pattern":"[a-"}""", """"x"""")
        assertEquals("pattern", result.schemaError)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun itemsSchemaChecksEachElementWithIndex() {
        val result = run(
            """{"type":"array","items":{"type":"integer"}}""",
            """[1,"two",3]"""
        )
        assertEquals(1, result.violations.size)
        assertEquals("$[1]", result.violations[0].path)
    }

    @Test
    fun tupleItemsValidatePositionally() {
        val result = run(
            """{"items":[{"type":"string"},{"type":"integer"}]}""",
            """["a","b"]"""
        )
        assertEquals("$[1]", result.violations[0].path)
        assertEquals("type", result.violations[0].keyword)
    }

    @Test
    fun arrayBoundsAndUniquenessAreChecked() {
        val tooFew = run("""{"minItems":2}""", """[1]""")
        assertEquals("minItems", tooFew.violations[0].keyword)

        val tooMany = run("""{"maxItems":1}""", """[1,2]""")
        assertEquals("maxItems", tooMany.violations[0].keyword)

        val dup = run("""{"uniqueItems":true}""", """[1,1]""")
        assertEquals("uniqueItems", dup.violations[0].keyword)
    }

    @Test
    fun numericBoundsAreChecked() {
        assertEquals("minimum", run("""{"minimum":5}""", """4""").violations[0].keyword)
        assertEquals("maximum", run("""{"maximum":5}""", """6""").violations[0].keyword)
        assertEquals("exclusiveMinimum", run("""{"exclusiveMinimum":5}""", """5""").violations[0].keyword)
        assertEquals("exclusiveMaximum", run("""{"exclusiveMaximum":5}""", """5""").violations[0].keyword)
        assertEquals("multipleOf", run("""{"multipleOf":3}""", """7""").violations[0].keyword)
    }

    @Test
    fun integerAcceptsWholeFloatsOnly() {
        assertTrue(run("""{"type":"integer"}""", """1.0""").valid)
        assertFalse(run("""{"type":"integer"}""", """1.5""").valid)
        assertTrue(run("""{"type":"number"}""", """1.5""").valid)
        assertFalse(run("""{"type":"number"}""", """"1.5"""").valid)
    }

    @Test
    fun typeArrayAcceptsAnyListedType() {
        val schema = """{"type":["string","null"]}"""
        assertTrue(run(schema, "null").valid)
        assertTrue(run(schema, """"x"""").valid)
        assertFalse(run(schema, "1").valid)
    }

    @Test
    fun anyOfRequiresOneBranchToPass() {
        val schema = """{"anyOf":[{"type":"string"},{"type":"integer"}]}"""
        assertTrue(run(schema, """1""").valid)
        val failed = run(schema, """true""")
        assertEquals("anyOf", failed.violations[0].keyword)
        assertEquals("2", failed.violations[0].expected)
    }

    @Test
    fun oneOfRejectsMultipleMatches() {
        val schema = """{"oneOf":[{"type":"number"},{"minimum":1}]}"""
        assertTrue(run(schema, """"x"""").valid)
        val failed = run(schema, """5""")
        assertEquals("oneOf", failed.violations[0].keyword)
        assertEquals("2", failed.violations[0].actual)
    }

    @Test
    fun notRejectsMatchingInstance() {
        val schema = """{"not":{"type":"string"}}"""
        assertEquals("not", run(schema, """"x"""").violations[0].keyword)
        assertTrue(run(schema, """1""").valid)
    }

    @Test
    fun allOfPropagatesEveryBranch() {
        val schema = """{"allOf":[{"type":"object","required":["a"]},{"type":"object","required":["b"]}]}"""
        val result = run(schema, """{}""")
        assertEquals(listOf("$.a", "$.b"), result.violations.map { it.path })
    }

    @Test
    fun localRefResolvesAgainstDefinitions() {
        val schema = """
            {
              "definitions": {"user": {"type": "object", "required": ["id"]}},
              "type": "object",
              "properties": {"owner": {"${'$'}ref": "#/definitions/user"}}
            }
        """.trimIndent()
        val result = run(schema, """{"owner":{}}""")
        assertEquals("$.owner.id", result.violations[0].path)
        assertEquals("required", result.violations[0].keyword)
    }

    @Test
    fun unresolvedRefReportsSchemaError() {
        val result = run("""{"${'$'}ref":"#/definitions/missing"}""", """1""")
        assertEquals("refNotFound", result.schemaError)
    }

    @Test
    fun invalidJsonTextsReportSchemaError() {
        assertEquals("schemaInvalid", report("""{"type":}""", """{}""").schemaError)
        assertEquals("schemaType", report("""[1]""", """{}""").schemaError)
        assertEquals("dataInvalid", report("""{}""", """{oops""").schemaError)
    }

    @Test
    fun violationsAreCappedAndFlagged() {
        val schema = """{"type":"array","items":{"type":"integer"}}"""
        val data = (1..(JsonSchemaValidator.MAX_VIOLATIONS + 20)).joinToString(",", "[", "]") { "\"x\"" }
        val result = run(schema, data)
        assertEquals(JsonSchemaValidator.MAX_VIOLATIONS, result.violations.size)
        assertTrue(result.truncated)
    }
}
