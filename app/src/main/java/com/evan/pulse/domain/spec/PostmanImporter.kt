package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.FormFieldEntry
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.ImportedItem
import com.evan.pulse.data.model.ImportGroup
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.snapshotJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Postman Collection v2.1 导入器。
 *
 * 纯解析：输入 collection JSON，输出导入计划，不接触 UI 与存储。
 * 支持 v2.1（schema 字段含 postman.com/json/collection/v2.1.0）。
 * 顶层 item 递归展开：文件夹路径以 " / " 连接作为分组名，文件夹直属请求归入该组，
 * 未包进文件夹的请求归入 untagged 分组。应用内 {{var}} 占位符与 Postman 一致，原样保留。
 */
object PostmanImporter {

    private const val FALLBACK_TITLE = "Postman Collection"

    private val json = Json { ignoreUnknownKeys = true }

    fun plan(raw: String, untaggedLabel: String): ImportPlan {
        val root = parseRoot(raw)
        val info = root.obj("info")
        val items = root.list("item")
            ?: throw SpecImportException(SpecError.NOT_RECOGNIZED, "缺少 item 节点")
        if (items.isEmpty()) {
            throw SpecImportException(SpecError.NO_OPERATIONS, "item 为空")
        }

        val collectionAuth = root.obj("auth")
        val title = info?.text("name").orEmpty().ifBlank { FALLBACK_TITLE }

        val bucket = LinkedHashMap<String, MutableList<ImportedItem>>()
        val count = walk(items, null, collectionAuth, bucket, untaggedLabel)

        if (count == 0) {
            throw SpecImportException(SpecError.NO_OPERATIONS, "没有可导入的请求")
        }

        return ImportPlan(
            collectionName = title,
            groups = bucket.entries.map { (tag, items_) ->
                ImportGroup(tag, uniquify(items_))
            }
        )
    }

    private fun parseRoot(raw: String): JsonObject {
        val text = raw.trim()
        if (text.isEmpty()) {
            throw SpecImportException(SpecError.EMPTY, "内容为空")
        }
        return try {
            json.parseToJsonElement(text) as? JsonObject
                ?: throw SpecImportException(SpecError.NOT_RECOGNIZED, "根节点不是对象")
        } catch (e: SpecImportException) {
            throw e
        } catch (e: Exception) {
            throw SpecImportException(SpecError.UNPARSEABLE, "JSON 解析失败：${e.message}")
        }
    }

    /** 递归展开 item：带 request 的是请求，带 item 的是文件夹 */
    private fun walk(
        items: JsonArray,
        folderPath: String?,
        collectionAuth: JsonObject?,
        bucket: MutableMap<String, MutableList<ImportedItem>>,
        untaggedLabel: String
    ): Int {
        var count = 0
        items.forEach { element ->
            val node = element.obj() ?: return@forEach
            val name = node.text("name")
            val request = node.obj("request")
            if (request != null) {
                buildImported(name, request, collectionAuth).let { imported ->
                    val tag = folderPath ?: untaggedLabel
                    bucket.getOrPut(tag) { mutableListOf() } += imported
                    count++
                }
            } else {
                val childItems = node.list("item") ?: return@forEach
                val childPath = folderPath?.let { "$it / $name" } ?: name
                count += walk(childItems, childPath, collectionAuth, bucket, untaggedLabel)
            }
        }
        return count
    }

    private fun buildImported(
        name: String,
        request: JsonObject,
        collectionAuth: JsonObject?
    ): ImportedItem {
        val method = request.text("method").uppercase().ifEmpty { "GET" }
        val url = request.obj("url")
        val rawUrl = url?.text("raw")
        val query = (url?.list("query"))?.mapNotNull { it.obj()?.param() } ?: emptyList()
        val headers = (request.list("header"))?.mapNotNull { it.obj()?.header() } ?: emptyList()
        val body = buildBody(request.obj("body"))
        val auth = request.obj("auth") ?: collectionAuth

        val snapshot = HttpSnapshot(
            method = method,
            url = rawUrl.orEmpty().ifEmpty { assembleUrl(url) },
            params = query.toMutableList(),
            headers = headers.toMutableList(),
            bodyType = body.type,
            bodyText = body.text,
            formFields = body.fields
        )
        val data = snapshotJson.encodeToString(HttpSnapshot.serializer(), applyAuth(auth, snapshot))
        val display = name.ifBlank { "$method ${snapshot.url}" }
        return ImportedItem(display, data)
    }

    private data class Body(val type: String, val text: String, val fields: List<FormFieldEntry>)

    private fun buildBody(body: JsonObject?): Body {
        body ?: return Body("NONE", "", emptyList())
        val mode = body.text("mode")
        return when (mode) {
            "raw" -> Body("RAW", body.text("raw"), emptyList())
            "urlencoded" -> {
                val fields = (body.list("urlencoded"))
                    ?.mapNotNull { it.obj()?.form() }
                    ?: emptyList()
                Body("FORM_URLENCODED", "", fields)
            }
            "formdata" -> {
                val fields = (body.list("formdata"))
                    ?.mapNotNull { it.obj()?.formData() }
                    ?: emptyList()
                Body("FORM_DATA", "", fields)
            }
            "file" -> Body("NONE", "", emptyList())
            "graphql" -> Body("RAW", graphqlBody(body.obj("graphql")), emptyList())
            else -> Body("RAW", body.text("raw"), emptyList())
        }
    }

    private fun graphqlBody(graphql: JsonObject?): String {
        graphql ?: return ""
        val query = graphql.text("query")
        val variables = graphql.obj("variables")
        if (variables == null) return query
        return query + "\n\n" + json.encodeToString(JsonElement.serializer(), variables)
    }

    private fun applyAuth(auth: JsonObject?, snapshot: HttpSnapshot): HttpSnapshot {
        auth ?: return snapshot
        val type = auth.text("type")
        val details = auth.list(type)
        val token = details?.find("token") ?: details?.find("value")
        return when (type) {
            "bearer" -> snapshot.copy(
                authType = "BEARER",
                bearerToken = token ?: snapshot.bearerToken
            )
            "basic" -> {
                val username = details?.find("username") ?: ""
                val password = details?.find("password") ?: ""
                snapshot.copy(
                    authType = "BASIC",
                    basicUsername = username,
                    basicPassword = password
                )
            }
            "apikey" -> snapshot.copy(
                authType = "APIKEY",
                apiKeyName = details?.find("key")?.ifEmpty { "X-API-Key" } ?: "X-API-Key",
                apiKeyValue = details?.find("value").orEmpty()
            )
            else -> snapshot
        }
    }

    private fun JsonArray.find(key: String): String? {
        forEach { element ->
            val obj = element.obj() ?: return@forEach
            if (obj.text("key") == key) {
                obj.text("value").takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    private fun JsonElement.param(): KeyValueEntry {
        val obj = this.obj()
        return KeyValueEntry(obj?.text("key") ?: "", obj?.text("value").orEmpty(), true)
    }

    private fun JsonElement.header(): KeyValueEntry {
        val obj = this.obj()
        return KeyValueEntry(obj?.text("key") ?: "", obj?.text("value").orEmpty(), true)
    }

    private fun JsonElement.form(): FormFieldEntry {
        val obj = this.obj()
        return FormFieldEntry(obj?.text("key") ?: "", obj?.text("value").orEmpty(), false)
    }

    private fun JsonElement.formData(): FormFieldEntry {
        val obj = this.obj()
        val isFile = obj?.text("type") == "file"
        val src = obj?.text("src").orEmpty()
        return FormFieldEntry(obj?.text("key") ?: "", if (isFile) src else obj?.text("value").orEmpty(), isFile)
    }

    /** url 缺 raw 时按 host + path + query 拼接（Postman 常见结构） */
    private fun assembleUrl(url: JsonObject?): String {
        url ?: return ""
        val protocol = url.text("protocol").ifEmpty { "https" }
        val host = (url.list("host"))?.joinToString("") { it.str() ?: "" }.orEmpty()
        val path = (url.list("path"))?.joinToString("/") { it.str() ?: "" }.orEmpty()
        val query = (url.list("query"))?.joinToString("&") { element ->
            val obj = element.obj()
            "${obj?.text("key") ?: ""}=${obj?.text("value").orEmpty()}"
        }.orEmpty()
        val base = if (host.isEmpty()) "" else "$protocol://$host"
        val withPath = if (path.isEmpty()) base else "$base/$path"
        return if (query.isEmpty()) withPath else "$withPath?$query"
    }

    private fun uniquify(items: List<ImportedItem>): List<ImportedItem> {
        val seen = HashMap<String, Int>()
        return items.map { item ->
            val count = seen.getOrDefault(item.name, 0)
            seen[item.name] = count + 1
            if (count == 0) item else item.copy(name = "${item.name} ${count + 1}")
        }
    }

    private fun JsonElement.opt(key: String): JsonElement? = obj()?.get(key)

    private fun JsonElement.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement.obj(key: String): JsonObject? = opt(key)?.obj()

    private fun JsonElement.list(key: String): JsonArray? = opt(key) as? JsonArray

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement.text(key: String): String = opt(key)?.str()?.trim().orEmpty()
}