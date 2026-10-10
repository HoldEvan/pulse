package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.data.model.HttpSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenApiImporterTest {

    private val untitled = "未分类"

    @Test
    fun `openapi3 yaml with tags and ref is planned into groups`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Pet Store
              version: '1.0'
            servers:
              - url: https://api.example.com/v2
            paths:
              /pets:
                get:
                  operationId: listPets
                  tags: [pets]
                  parameters:
                    - name: limit
                      in: query
                      required: false
                      schema:
                        type: integer
                        default: 20
                post:
                  operationId: createPet
                  tags: [pets]
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          type: object
                          required: [name]
                          properties:
                            name:
                              type: string
                            tag:
                              type: string
              /pets/{petId}:
                get:
                  summary: Show pet by ID
                  tags: [pets]
                  parameters:
                    - ${"$"}ref: '#/components/parameters/Id'
                    - name: trace
                      in: header
                      required: false
                      schema:
                        type: string
            components:
              parameters:
                Id:
                  name: petId
                  in: path
                  required: true
                  schema:
                    type: string
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        assertEquals("Pet Store", plan.collectionName)
        assertEquals(1, plan.groups.size)
        assertEquals("pets", plan.groups.first().tag)
        assertEquals(3, plan.groups.first().items.size)

        val byName = plan.groups.first().items.associateBy { it.name }
        val list = decode(byName.getValue("GET /pets"))
        assertEquals("GET", list.method)
        assertEquals("https://api.example.com/v2/pets", list.url)
        assertEquals(listOf(KeyVal("limit", "20")), list.params.map { KeyVal(it.key, it.value) })

        val create = decode(byName.getValue("POST /pets"))
        assertEquals("POST", create.method)
        assertEquals("JSON", create.bodyType)
        assertTrue(create.bodyText.contains("\"name\": \"\""))
        assertTrue(create.bodyText.contains("\"tag\": \"\""))

        val showItem = plan.groups.first().items.single { it.name == "Show pet by ID" }
        val show = decode(showItem)
        assertEquals("GET", show.method)
        assertEquals("/pets/{petId}", show.url.substringAfter("https://api.example.com/v2"))
        assertTrue(show.headers.any { it.key == "trace" })

        // 名称来自 summary，路径独立存放在快照里，两者不互相覆盖
        assertTrue(showItem.name != show.url)
    }

    @Test
    fun `json spec without tags falls back to untagged label`() {
        val spec = """
            {
              "openapi": "3.1.0",
              "info": {"title": "Tiny"},
              "paths": {
                "/ping": {
                  "get": {
                    "summary": "Ping",
                    "requestBody": {
                      "content": {
                        "application/x-www-form-urlencoded": {
                          "schema": {
                            "type": "object",
                            "properties": {
                              "q": {"type": "string", "default": "hello"}
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        assertEquals(untitled, plan.groups.single().tag)
        val snapshot = decode(plan.groups.single().items.single())
        assertEquals("FORM_URLENCODED", snapshot.bodyType)
        assertEquals("q", snapshot.params.single().key)
        assertEquals("hello", snapshot.params.single().value)
    }

    @Test
    fun `swagger 2 uses host schemes and basePath`() {
        val spec = """
            swagger: "2.0"
            info:
              title: Legacy
            host: api.example.com
            basePath: /v1
            schemes: [http]
            paths:
              /things:
                get:
                  summary: List things
                  security:
                    - ApiKeyAuth: []
            securityDefinitions:
              ApiKeyAuth:
                type: apiKey
                in: header
                name: X-Thing-Key
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        val snapshot = decode(plan.groups.single().items.single())
        assertEquals("http://api.example.com/v1/things", snapshot.url)
        assertEquals("APIKEY", snapshot.authType)
        assertEquals("X-Thing-Key", snapshot.apiKeyName)
    }

    @Test
    fun `security bearer is applied to snapshot`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Auth}
            paths:
              /me:
                get:
                  summary: Who am I
            components:
              securitySchemes:
                BearerAuth:
                  type: http
                  scheme: bearer
                  bearerFormat: JWT
            security:
              - BearerAuth: []
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        val snapshot = decode(plan.groups.single().items.single())
        assertEquals("BEARER", snapshot.authType)
    }

    @Test(expected = SpecImportException::class)
    fun `empty input fails with EMPTY`() {
        OpenApiImporter.plan("   \n ", untitled)
    }

    @Test(expected = SpecImportException::class)
    fun `broken yaml fails with UNPARSEABLE`() {
        OpenApiImporter.plan("openapi: [3.0", untitled)
    }

    @Test(expected = SpecImportException::class)
    fun `json without paths fails with NOT_OPENAPI`() {
        OpenApiImporter.plan("""{"info": {"title": "x"}}""", untitled)
    }

    @Test(expected = SpecImportException::class)
    fun `empty paths fails with NO_OPERATIONS`() {
        OpenApiImporter.plan("""{"openapi": "3.0.0", "info": {"title": "x"}, "paths": {}}""", untitled)
    }

    @Test
    fun `unsupported methods are skipped but others still import`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Weird}
            paths:
              /x:
                trace:
                  summary: Trace x
                post:
                  summary: Post x
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        assertEquals(1, plan.groups.single().items.size)
        assertEquals("POST", decode(plan.groups.single().items.single()).method)
    }

    @Test
    fun `yaml anchors and aliases are resolved before parsing`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Anchors}
            x-common: &common
              type: object
              properties:
                id: {type: string}
            paths:
              /a:
                post:
                  summary: A
                  requestBody:
                    content:
                      application/json:
                        schema: *common
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        val snapshot = decode(plan.groups.single().items.single())
        assertEquals("JSON", snapshot.bodyType)
        assertTrue(snapshot.bodyText.contains("\"id\": \"\""))
    }

    @Test
    fun `placeholder braces survive without being rewritten`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Placeholder}
            paths:
              /greet:
                get:
                  summary: Greet
                  parameters:
                    - name: greeting
                      in: query
                      schema: {type: string, default: "Hello {{name}}"}
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        val snapshot = decode(plan.groups.single().items.single())
        assertEquals("Hello {{name}}", snapshot.params.single().value)
    }

    @Test
    fun `path item ref is resolved to its operations`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Refs}
            paths:
              /alias:
                ${"$"}ref: '#/components/pathItems/Shared'
            components:
              pathItems:
                Shared:
                  post:
                    summary: Shared op
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        val snapshot = decode(plan.groups.single().items.single())
        assertEquals("POST", snapshot.method)
        assertEquals("/alias", snapshot.url)
    }

    @Test
    fun `paths without a leading slash are joined with a slash`() {
        val spec = """
            openapi: 3.0.0
            info: {title: NoSlash}
            servers:
              - url: https://api.example.com/v2
            paths:
              1_2:
                get:
                  summary: Numeric path
              pets/{petId}:
                post:
                  summary: Slashless
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        val byName = plan.groups.single().items.associateBy { it.name }
        assertEquals(
            "https://api.example.com/v2/1_2",
            decode(byName.getValue("Numeric path")).url
        )
        assertEquals(
            "https://api.example.com/v2/pets/{petId}",
            decode(byName.getValue("Slashless")).url
        )
    }

    @Test
    fun `relative url keeps a single leading slash when there is no base`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Relative}
            paths:
              1_2:
                get:
                  summary: Bare
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        assertEquals("/1_2", decode(plan.groups.single().items.single()).url)
    }

    @Test
    fun `empty server entries are skipped for the base url`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Servers}
            servers:
              - description: not set yet
              - url: https://b.example.com/api/
            paths:
              /x:
                get:
                  summary: Bare
        """.trimIndent()

        val plan = OpenApiImporter.plan(spec, untitled)
        assertEquals("https://b.example.com/api/x", decode(plan.groups.single().items.single()).url)
    }

    @Test
    fun `operation level ref is followed`() {
        val spec = """
            openapi: 3.0.0
            info: {title: Ops}
            servers:
              - url: https://api.example.com
            paths:
              /pets/{petId}:
                get:
                  ${"$"}ref: '#/components/operations/Show'
            components:
              operations:
                Show:
                  summary: Look up a pet
                  tags: [cats]
                  parameters:
                    - name: petId
                      in: path
                      required: true
                      schema: {type: integer}
        """.trimIndent()

        val group = OpenApiImporter.plan(spec, untitled).groups.single()
        assertEquals("cats", group.tag)
        val item = group.items.single()
        assertEquals("Look up a pet", item.name)
        val snapshot = decode(item)
        assertEquals("GET", snapshot.method)
        assertEquals("https://api.example.com/pets/{petId}", snapshot.url)
        assertTrue(snapshot.headers.isEmpty())
        assertTrue(snapshot.params.isEmpty())
    }

    @Test
    fun `summary is preferred over a numeric operation id`() {
        val spec = """
            openapi: 3.0.1
            info: {title: Arcade}
            servers:
              - url: http://uat.example.com/api
            paths:
              /platform/getUserAgreement:
                post:
                  operationId: 1_7
                  summary: 用户协议
                  tags: [platform]
        """.trimIndent()

        val group = OpenApiImporter.plan(spec, untitled).groups.single()
        assertEquals("platform", group.tag)
        val item = group.items.single()
        assertEquals("用户协议", item.name)
        assertEquals("http://uat.example.com/api/platform/getUserAgreement", decode(item).url)
    }

    @Test
    fun `duplicate summary names get a numeric suffix`() {
        val spec = """
            openapi: 3.0.1
            info: {title: Dups}
            paths:
              /a:
                post:
                  summary: 同名
                  tags: [t]
              /b:
                post:
                  summary: 同名
                  tags: [t]
              /c:
                post:
                  summary: 同名
                  tags: [t]
        """.trimIndent()

        val names = OpenApiImporter.plan(spec, untitled).groups.single().items.map { it.name }
        assertEquals(listOf("同名", "同名 2", "同名 3"), names)
    }

    private data class KeyVal(val key: String, val value: String)

    private fun decode(item: com.evan.pulse.data.model.ImportedItem): HttpSnapshot =
        snapshotJson.decodeFromString(HttpSnapshot.serializer(), item.data)
}
