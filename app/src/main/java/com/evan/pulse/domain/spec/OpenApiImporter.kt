package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.FormFieldEntry
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.ImportedItem
import com.evan.pulse.data.model.ImportGroup
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.snapshotJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * OpenAPI 3.x 与 Swagger 2.0 规范导入器。
 *
 * 纯解析：输入规范文本，输出导入计划，不接触 UI 与存储。
 * 应用内占位符用双花括号 {{var}}，OpenAPI 路径参数用单花括号 {var}，两者互不干扰。
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object OpenApiImporter {

    private const val FALLBACK_TITLE = "Imported API"
    private const val MAX_SCHEMA_DEPTH = 6
    private const val MAX_REF_DEPTH = 32

    private val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD")

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    data class Plan(val collectionName: String, val groups: List<ImportGroup>)

    fun plan(raw: String, untaggedLabel: String): Plan {
        val root = SpecParser.parseAny(raw)
        val paths = root.obj("paths")
            ?: throw SpecImportException(SpecError.NOT_OPENAPI, "缺少 paths 节点")
        if (paths.isEmpty()) {
            throw SpecImportException(SpecError.NO_OPERATIONS, "paths 为空")
        }

        val base = baseUrl(root)
        val title = root.obj("info")?.text("title").orEmpty().ifBlank { FALLBACK_TITLE }

        val bucket = LinkedHashMap<String, MutableList<ImportedItem>>()
        var count = 0
        paths.forEach { (rawPath, pathItem) ->
            val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            resolve(root, pathItem).obj()?.forEach { operation, operationItem ->
                val method = operation.uppercase()
                if (method !in SUPPORTED_METHODS) return@forEach
                val op = resolve(root, operationItem).obj() ?: return@forEach
                buildImported(root, base, method, path, op).let { imported ->
                    bucket.getOrPut(tagOf(op, untaggedLabel)) { mutableListOf() } += imported
                    count++
                }
            }
        }

        if (count == 0) {
            throw SpecImportException(SpecError.NO_OPERATIONS, "没有可导入的接口")
        }

        return Plan(
            collectionName = title,
            groups = bucket.entries.map { (tag, items) -> ImportGroup(tag, uniquify(items.toList())) }
        )
    }

    /**
     * summary 作为显示名。不少 API 网关把 operationId 编成 1_1、1_2 这类流水号，
     * 直接拿来当名字会失去可读性，所以 operationId 不参与命名。
     */
    private fun displayName(op: JsonObject, method: String, path: String): String =
        op.text("summary").ifEmpty { "$method $path" }

    /** 同组内重名接口补序号，避免预览列表里出现多个同名条目 */
    private fun uniquify(items: List<ImportedItem>): List<ImportedItem> {
        val seen = HashMap<String, Int>()
        return items.map { item ->
            val count = seen.getOrDefault(item.name, 0)
            seen[item.name] = count + 1
            if (count == 0) item else item.copy(name = "${item.name} ${count + 1}")
        }
    }

    private fun buildImported(
        root: JsonObject,
        base: String,
        method: String,
        path: String,
        op: JsonObject
    ): ImportedItem {
        val parameters = op.list("parameters")
        val query = mutableListOf<KeyValueEntry>()
        val headers = mutableListOf<KeyValueEntry>()
        parameters?.forEach { parameter ->
            val resolved = resolve(root, parameter)
            when (resolved.text("in")) {
                "query" -> resolved.entry()?.let { query += it }
                "header" -> resolved.entry()?.let { headers += it }
            }
        }

        val body = buildBody(root, op.obj("requestBody"), query)
        val snapshot = HttpSnapshot(
            method = method,
            url = joinUrl(base, path),
            params = query,
            headers = headers,
            bodyType = body.type,
            bodyText = body.text,
            formFields = body.fields
        )
        val name = displayName(op, method, path)

        val data = snapshotJson.encodeToString(
            HttpSnapshot.serializer(),
            applyAuth(root, op, snapshot)
        )
        return ImportedItem(name, data)
    }

    private data class Body(val type: String, val text: String, val fields: List<FormFieldEntry>)

    private fun buildBody(
        root: JsonObject,
        requestBody: JsonObject?,
        query: MutableList<KeyValueEntry>
    ): Body {
        val resolved = requestBody?.let { resolve(root, it).obj() }
            ?: return Body("NONE", "", emptyList())
        val content = resolved.obj("content") ?: return Body("NONE", "", emptyList())

        val preferred = content.entries.firstOrNull { "application/json" in it.key.lowercase() }
            ?: content.entries.firstOrNull() ?: return Body("NONE", "", emptyList())
        val mediaObj = preferred.value.obj()
        val mediaSchema = mediaObj?.obj("schema")
        val mediaType = preferred.key.lowercase()

        val payload = pretty(mediaExample(mediaObj) ?: skeleton(root, mediaSchema))
        return when {
            "application/json" in mediaType -> Body("JSON", payload, emptyList())
            "application/x-www-form-urlencoded" in mediaType -> {
                query += schemaEntries(root, mediaSchema)
                Body("FORM_URLENCODED", "", emptyList())
            }
            "multipart/form-data" in mediaType ->
                Body("FORM_DATA", "", schemaFields(root, mediaSchema))
            else -> Body("RAW", payload, emptyList())
        }
    }

    private fun mediaExample(media: JsonObject?): JsonElement? {
        media ?: return null
        media.opt("example")?.takeUnless { it == JsonNull }?.let { return it }
        media.obj("examples")?.values?.firstOrNull()?.obj("value")?.let { return it }
        return null
    }

    private fun pretty(element: JsonElement): String =
        prettyJson.encodeToString(JsonElement.serializer(), element)

    private fun schemaEntries(root: JsonObject, schema: JsonObject?): List<KeyValueEntry> {
        val properties = schema?.let { resolve(root, it).obj("properties") }
            ?: return emptyList()
        return properties.mapNotNull { (key, value) ->
            val resolved = resolve(root, value)
            val sample = resolved.opt("default") ?: resolved.obj("example")
            if (sample == null) null else KeyValueEntry(key, textOf(sample), true)
        }
    }

    private fun schemaFields(root: JsonObject, schema: JsonObject?): List<FormFieldEntry> {
        val properties = schema?.let { resolve(root, it).obj("properties") }
            ?: return emptyList()
        return properties.map { (name, value) ->
            val resolved = resolve(root, value).obj()
            FormFieldEntry(name = name, value = "", isFile = schemaType(resolved) == "file")
        }
    }

    private fun schemaType(schema: JsonObject?): String {
        if (schema == null) return ""
        return when (val type = schema.opt("type")) {
            is JsonPrimitive -> type.str()?.lowercase().orEmpty()
            is JsonArray -> type.firstOrNull()?.str()?.lowercase().orEmpty()
            else -> ""
        }
    }

    /** 递归生成字段骨架；深度受限，避免深层 schema 递归爆栈 */
    private fun skeleton(root: JsonObject, schema: JsonObject?, depth: Int = 0): JsonElement {
        if (depth > MAX_SCHEMA_DEPTH) return JsonNull
        val node = schema?.let { resolve(root, it).obj() } ?: return JsonNull
        val properties = node.obj("properties")
        val type = schemaType(node)
        return when {
            type == "object" || properties != null ->
                JsonObject(
                    (properties ?: JsonObject(emptyMap())).mapValues { (_, value) ->
                        skeleton(root, value.obj(), depth + 1)
                    }
                )
            type == "array" -> JsonArray(listOf(skeleton(root, node.obj("items"), depth + 1)))
            type == "string" -> JsonPrimitive("")
            type == "integer" -> JsonPrimitive(0)
            type == "number" -> JsonPrimitive(0.0)
            type == "boolean" -> JsonPrimitive(false)
            else -> JsonNull
        }
    }

    private fun applyAuth(root: JsonObject, op: JsonObject, snapshot: HttpSnapshot): HttpSnapshot {
        val schemes = root.obj("components")?.obj("securitySchemes")
            ?: root.obj("securitySchemes")
            ?: root.obj("securityDefinitions")
            ?: return snapshot
        val requirement = (op.list("security") ?: root.list("security"))
            ?.firstOrNull()?.obj()?.keys?.firstOrNull()
            ?: return snapshot
        val scheme = schemes[requirement]?.obj() ?: return snapshot
        val type = scheme.text("type").lowercase()
        val detail = scheme.text("scheme").lowercase()
        return when {
            type == "http" && detail == "bearer" -> snapshot.copy(authType = "BEARER")
            type == "http" && detail == "basic" -> snapshot.copy(authType = "BASIC")
            type == "apikey" -> snapshot.copy(
                authType = "APIKEY",
                apiKeyName = scheme.text("name").ifEmpty { "X-API-Key" }
            )
            else -> snapshot
        }
    }

    /** OpenAPI 3 用 servers，Swagger 2 用 schemes + host + basePath */
    private fun baseUrl(root: JsonObject): String {
        val server = root.list("servers")
            ?.map { resolve(root, it) }
            ?.firstNotNullOfOrNull { node ->
                node.obj()?.text("url")?.trim()?.takeIf { it.isNotEmpty() }
            }
        if (server != null) return server.trimEnd('/')
        val host = root.text("host").trim()
        if (host.isEmpty()) return ""
        val scheme = root.list("schemes")?.firstOrNull()?.text().orEmpty().ifEmpty { "https" }
        return "$scheme://$host${root.text("basePath")}".trimEnd('/')
    }

    /** base 与 path 之间保证恰好一个 /；path 缺前导斜杠的规范按非法但常见处理 */
    private fun joinUrl(base: String, path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        if (base.isEmpty()) return normalized
        return base.trimEnd('/') + normalized
    }

    private fun tagOf(op: JsonObject, untaggedLabel: String): String =
        op.list("tags")?.firstOrNull()?.text().orEmpty().ifEmpty { untaggedLabel }

    private fun JsonElement.entry(): KeyValueEntry? {
        val key = text("name")
        if (key.isEmpty()) return null
        val schema = obj("schema")
        val sample = opt("default") ?: obj("example")
            ?: schema?.opt("default")
            ?: schema?.obj("example")
            ?: return KeyValueEntry(key, "", false)
        return KeyValueEntry(key, textOf(sample), true)
    }

    /** 解析 $ref：JSON Pointer 语义，~1 / ~0 反转义，限制跳数防循环引用 */
    private fun resolve(root: JsonObject, node: JsonElement): JsonElement {
        var current = node
        var guard = 0
        while (guard++ < MAX_REF_DEPTH) {
            val ref = current.obj()?.opt("\$ref")?.str()
            if (ref == null || !ref.startsWith("#/")) return current

            var target: JsonElement = root
            var broken = false
            for (segment in ref.removePrefix("#/").split("/")) {
                if (broken) break
                val key = segment.replace("~1", "/").replace("~0", "~")
                val next = target.obj()?.opt(key)
                if (next == null) {
                    broken = true
                } else {
                    target = next
                }
            }
            if (broken) return current
            current = target
        }
        return current
    }

    private fun textOf(element: JsonElement): String = when (element) {
        JsonNull -> ""
        is JsonPrimitive -> element.content
        else -> element.toString()
    }

    private fun JsonElement.opt(key: String): JsonElement? = obj()?.get(key)

    private fun JsonElement.obj(key: String): JsonObject? = opt(key)?.obj()

    private fun JsonElement.list(key: String): JsonArray? = opt(key) as? JsonArray

    private fun JsonElement.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement.text(key: String): String = opt(key)?.str()?.trim().orEmpty()

    private fun JsonElement.text(): String = str()?.trim().orEmpty()
}
