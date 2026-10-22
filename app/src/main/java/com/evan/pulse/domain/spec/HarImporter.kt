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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * HAR 1.2 导入器。
 *
 * 纯解析：输入 HAR JSON，输出导入计划。逐条取 log.entries 的 request 重建请求，
 * 按 URL host 分组（避免混杂多个站点的抓包混成一锅），组内同名补序号。
 * HAR 记录的是已发生的流量，不保留 Postman/OpenAPI 的接口语义，但方法、URL、
 * 查询串、请求头与请求体均可还原。
 */
object HarImporter {

    private const val FALLBACK_TITLE = "HAR 抓包导入"
    private const val MAX_ENTRIES = 500

    private val json = Json { ignoreUnknownKeys = true }

    fun plan(raw: String, untaggedLabel: String): ImportPlan {
        val root = parseRoot(raw)
        val log = root.obj("log")
            ?: throw SpecImportException(SpecError.NOT_RECOGNIZED, "缺少 log 节点")
        val entries = log.list("entries")
            ?: throw SpecImportException(SpecError.NOT_RECOGNIZED, "缺少 entries 节点")
        if (entries.isEmpty()) {
            throw SpecImportException(SpecError.NO_OPERATIONS, "entries 为空")
        }

        val bucket = LinkedHashMap<String, MutableList<ImportedItem>>()
        val source = log.text("creator").ifBlank { FALLBACK_TITLE }
        val target = if (source == FALLBACK_TITLE) source else "HAR（$source）"

        var count = 0
        entries.take(MAX_ENTRIES).forEach { element ->
            val entry = element.obj() ?: return@forEach
            val request = entry.obj("request") ?: return@forEach
            val url = request.text("url")
            if (url.isEmpty()) return@forEach
            val method = request.text("method").uppercase().ifEmpty { "GET" }
            val host = hostOf(url).ifEmpty { untaggedLabel }
            val imported = buildImported(url, method, request)
            bucket.getOrPut(host) { mutableListOf() } += imported
            count++
        }

        if (count == 0) {
            throw SpecImportException(SpecError.NO_OPERATIONS, "没有可导入的请求")
        }

        return ImportPlan(
            collectionName = target,
            groups = bucket.entries.map { (tag, items) ->
                ImportGroup(tag, uniquify(items))
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

    private fun buildImported(url: String, method: String, request: JsonObject): ImportedItem {
        val query = (request.list("queryString"))
            ?.mapNotNull { it.obj()?.kv() }
            ?: emptyList()
        val headers = (request.list("headers"))
            ?.mapNotNull { it.obj()?.kv() }
            ?: emptyList()
        val body = buildBody(request.obj("postData"))

        val snapshot = HttpSnapshot(
            method = method,
            url = url,
            params = query.toMutableList(),
            headers = headers.toMutableList(),
            bodyType = body.type,
            bodyText = body.text,
            formFields = body.fields
        )
        return ImportedItem("$method ${url}", snapshotJson.encodeToString(HttpSnapshot.serializer(), snapshot))
    }

    private data class Body(val type: String, val text: String, val fields: List<FormFieldEntry>)

    private fun buildBody(postData: JsonObject?): Body {
        postData ?: return Body("NONE", "", emptyList())
        val mime = postData.text("mimeType").lowercase()
        val text = postData.text("text")
        return when {
            "application/json" in mime -> Body("JSON", text, emptyList())
            "application/x-www-form-urlencoded" in mime -> {
                val fields = (postData.list("params"))
                    ?.mapNotNull { it.obj()?.form() }
                    ?: emptyList()
                Body("FORM_URLENCODED", "", fields)
            }
            "multipart/form-data" in mime -> {
                val fields = (postData.list("params"))
                    ?.mapNotNull { it.obj()?.formData() }
                    ?: emptyList()
                Body("FORM_DATA", "", fields)
            }
            else -> Body("RAW", text, emptyList())
        }
    }

    private fun JsonElement.kv(): KeyValueEntry {
        val obj = this.obj()
        return KeyValueEntry(obj?.text("name") ?: "", obj?.text("value").orEmpty(), true)
    }

    private fun JsonElement.form(): FormFieldEntry {
        val obj = this.obj()
        return FormFieldEntry(obj?.text("name") ?: "", obj?.text("value").orEmpty(), false)
    }

    private fun JsonElement.formData(): FormFieldEntry {
        val obj = this.obj()
        val isFile = obj?.text("fileName").orEmpty().isNotEmpty()
        return FormFieldEntry(
            obj?.text("name") ?: "",
            if (isFile) obj?.text("fileName").orEmpty() else obj?.text("value").orEmpty(),
            isFile
        )
    }

    private fun hostOf(url: String): String {
        return runCatching {
            val withScheme = if (url.startsWith("http")) url else "https://$url"
            java.net.URI(withScheme).host ?: ""
        }.getOrElse { "" }
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