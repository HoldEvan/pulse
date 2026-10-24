package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.FormFieldEntry
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.snapshotJson
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 可导出的集合格式 */
enum class CollectionExportFormat { POSTMAN, OPENAPI }

/** 导出结果：文本内容与被跳过的非 HTTP 条目数 */
data class CollectionExportResult(
    val text: String,
    val exported: Int,
    val skipped: Int
)

/**
 * 集合导出：把一个文件夹及其子树的 HTTP 请求写成 Postman Collection v2.1
 * 或 OpenAPI 3.0 文档。
 *
 * WS / SSE / MQTT 与损坏快照会被跳过并计数——两种格式都没有对应的通用表达，
 * 强行塞进去只会让导入方解析失败。纯函数，不依赖 Android。
 */
object CollectionExporter {

    fun export(
        root: CollectionInfo,
        folders: List<CollectionInfo>,
        requests: List<SavedRequest>,
        variables: Map<String, String>,
        format: CollectionExportFormat
    ): CollectionExportResult {
        val subtree = CollectionTree.descendantsOf(folders, root.id) + root.id
        val scopedFolders = folders.filter { it.id in subtree }
        val scopedRequests = requests.filter { it.collectionId in subtree }
        val http = scopedRequests.mapNotNull { request ->
            if (request.type != RequestType.HTTP) return@mapNotNull null
            val snapshot = runCatching {
                snapshotJson.decodeFromString(HttpSnapshot.serializer(), request.data)
            }.getOrNull() ?: return@mapNotNull null
            request to snapshot
        }
        val skipped = scopedRequests.size - http.size
        val text = when (format) {
            CollectionExportFormat.POSTMAN -> postman(root, scopedFolders, http, variables)
            CollectionExportFormat.OPENAPI -> openApi(root, http)
        }
        return CollectionExportResult(text, http.size, skipped)
    }

    /** 建议文件名：文件夹名去掉不安全字符后加格式后缀 */
    fun fileName(root: CollectionInfo, format: CollectionExportFormat): String {
        val base = root.name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .ifBlank { "collection" }
        return when (format) {
            CollectionExportFormat.POSTMAN -> "$base.postman.json"
            CollectionExportFormat.OPENAPI -> "$base.openapi.json"
        }
    }

    // ---- Postman ----

    private fun postman(
        root: CollectionInfo,
        folders: List<CollectionInfo>,
        requests: List<Pair<SavedRequest, HttpSnapshot>>,
        variables: Map<String, String>
    ): String = buildJsonObject {
        putJsonObject("info") {
            put("name", root.name)
            put("schema", POSTMAN_SCHEMA)
        }
        putJsonArray("variable") {
            variables.forEach { (key, value) ->
                add(buildJsonObject {
                    put("key", key)
                    put("value", value)
                })
            }
        }
        put("item", postmanItems(root.id, folders, requests))
    }.toString()

    private fun postmanItems(
        parentId: Long,
        folders: List<CollectionInfo>,
        requests: List<Pair<SavedRequest, HttpSnapshot>>
    ): JsonArray = buildJsonArray {
        folders.filter { it.parentId == parentId }.forEach { folder ->
            add(buildJsonObject {
                put("name", folder.name)
                put("item", postmanItems(folder.id, folders, requests))
            })
        }
        requests.filter { it.first.collectionId == parentId }.forEach { (request, snapshot) ->
            add(postmanRequest(request, snapshot))
        }
    }

    private fun postmanRequest(request: SavedRequest, snapshot: HttpSnapshot) = buildJsonObject {
        put("name", request.name)
        putJsonObject("request") {
            put("method", snapshot.method)
            putJsonArray("header") {
                snapshot.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { header ->
                    add(buildJsonObject {
                        put("key", header.key)
                        put("value", header.value)
                    })
                }
            }
            put("url", postmanUrl(snapshot))
            postmanBody(snapshot)?.let { put("body", it) }
            postmanAuth(snapshot)?.let { put("auth", it) }
        }
        val events = buildJsonArray {
            scriptEvent("prerequest", snapshot.preScript)?.let { add(it) }
            scriptEvent("test", snapshot.postScript)?.let { add(it) }
        }
        if (events.isNotEmpty()) put("event", events)
    }

    private fun postmanUrl(snapshot: HttpSnapshot): JsonObject {
        val query = snapshot.params.filter { it.enabled && it.key.isNotBlank() }
        val raw = buildString {
            append(snapshot.url)
            if (query.isNotEmpty()) {
                append(if ('?' in snapshot.url) '&' else '?')
                append(query.joinToString("&") { "${it.key}=${it.value}" })
            }
        }
        return buildJsonObject {
            put("raw", raw)
            val uri = runCatching { URI(snapshot.url) }.getOrNull()
            if (uri?.scheme != null && uri.host != null) {
                put("protocol", uri.scheme)
                putJsonArray("host") { uri.host.split('.').forEach { add(it) } }
                putJsonArray("path") {
                    uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }.forEach { add(it) }
                }
            }
            if (query.isNotEmpty()) {
                putJsonArray("query") {
                    query.forEach { param ->
                        add(buildJsonObject {
                            put("key", param.key)
                            put("value", param.value)
                        })
                    }
                }
            }
        }
    }

    private fun postmanBody(snapshot: HttpSnapshot): JsonObject? = when (snapshot.bodyType) {
        "JSON", "GRAPHQL" -> buildJsonObject {
            put("mode", "raw")
            put("raw", snapshot.bodyText)
            putJsonObject("options") { putJsonObject("raw") { put("language", "json") } }
        }

        "RAW" -> buildJsonObject {
            put("mode", "raw")
            put("raw", snapshot.bodyText)
        }

        "FORM_URLENCODED" -> buildJsonObject {
            put("mode", "urlencoded")
            putJsonArray("urlencoded") {
                snapshot.formFields.filter { it.name.isNotBlank() }.forEach { field ->
                    add(buildJsonObject {
                        put("key", field.name)
                        put("value", field.value)
                    })
                }
            }
        }

        "FORM_DATA" -> buildJsonObject {
            put("mode", "formdata")
            putJsonArray("formdata") {
                snapshot.formFields.filter { it.name.isNotBlank() }.forEach { field ->
                    add(formDataField(field))
                }
            }
        }

        else -> null
    }

    private fun formDataField(field: FormFieldEntry) = buildJsonObject {
        put("key", field.name)
        if (field.isFile) {
            put("type", "file")
            put("src", field.fileName.orEmpty())
        } else {
            put("value", field.value)
        }
    }

    private fun postmanAuth(snapshot: HttpSnapshot): JsonObject? = when (snapshot.authType) {
        "BEARER" -> buildJsonObject {
            put("type", "bearer")
            putJsonArray("bearer") {
                add(buildJsonObject {
                    put("key", "token")
                    put("value", snapshot.bearerToken)
                    put("type", "string")
                })
            }
        }

        "BASIC" -> buildJsonObject {
            put("type", "basic")
            putJsonArray("basic") {
                add(buildJsonObject {
                    put("key", "username")
                    put("value", snapshot.basicUsername)
                    put("type", "string")
                })
                add(buildJsonObject {
                    put("key", "password")
                    put("value", snapshot.basicPassword)
                    put("type", "string")
                })
            }
        }

        "APIKEY" -> buildJsonObject {
            put("type", "apikey")
            putJsonArray("apikey") {
                add(buildJsonObject {
                    put("key", "key")
                    put("value", snapshot.apiKeyName.ifBlank { "X-API-Key" })
                    put("type", "string")
                })
                add(buildJsonObject {
                    put("key", "value")
                    put("value", snapshot.apiKeyValue)
                    put("type", "string")
                })
                add(buildJsonObject {
                    put("key", "in")
                    put("value", "header")
                    put("type", "string")
                })
            }
        }

        else -> null
    }

    private fun scriptEvent(listen: String, script: String): JsonObject? {
        if (script.isBlank()) return null
        return buildJsonObject {
            put("listen", listen)
            putJsonObject("script") {
                put("type", "text/javascript")
                putJsonArray("exec") { script.lines().forEach { add(it) } }
            }
        }
    }

    // ---- OpenAPI ----

    private fun openApi(
        root: CollectionInfo,
        requests: List<Pair<SavedRequest, HttpSnapshot>>
    ): String {
        val used = mutableSetOf<String>()
        val operationIds = mutableSetOf<String>()
        val paths = linkedMapOf<String, MutableList<Pair<String, Pair<SavedRequest, HttpSnapshot>>>>()
        requests.forEach { pair ->
            val (_, snapshot) = pair
            val path = openApiPath(snapshot.url)
            val method = snapshot.method.lowercase()
            if (!used.add("$method $path")) return@forEach
            paths.getOrPut(path) { mutableListOf() } += method to pair
        }
        return buildJsonObject {
            put("openapi", "3.0.3")
            putJsonObject("info") {
                put("title", root.name)
                put("version", "1.0.0")
            }
            putJsonObject("paths") {
                paths.forEach { (path, operations) ->
                    putJsonObject(path) {
                        operations.forEach { (method, pair) ->
                            putJsonObject(method) { operation(pair, operationIds) }
                        }
                    }
                }
            }
        }.toString()
    }

    private fun JsonObjectBuilder.operation(
        pair: Pair<SavedRequest, HttpSnapshot>,
        operationIds: MutableSet<String>
    ) {
        val (request, snapshot) = pair
        put("summary", request.name)
        put("operationId", operationId(request.name, snapshot.method, operationIds))
        putJsonArray("parameters") {
            snapshot.params.filter { it.enabled && it.key.isNotBlank() }.forEach { param ->
                add(buildJsonObject {
                    put("name", param.key)
                    put("in", "query")
                    put("required", false)
                    putJsonObject("schema") { put("type", "string") }
                })
            }
            snapshot.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { header ->
                add(buildJsonObject {
                    put("name", header.key)
                    put("in", "header")
                    put("required", false)
                    putJsonObject("schema") { put("type", "string") }
                })
            }
        }
        openApiBody(snapshot)?.let { put("requestBody", it) }
        putJsonObject("responses") {
            putJsonObject("200") { put("description", "OK") }
        }
    }

    private fun openApiBody(snapshot: HttpSnapshot): JsonObject? {
        val (mediaType, example) = when (snapshot.bodyType) {
            "JSON" -> "application/json" to snapshot.bodyText
            "GRAPHQL" -> "application/json" to snapshot.bodyText
            "FORM_URLENCODED" -> "application/x-www-form-urlencoded" to
                snapshot.formFields.joinToString("&") { "${it.name}=${it.value}" }
            "FORM_DATA" -> "multipart/form-data" to ""
            "RAW" -> "text/plain" to snapshot.bodyText
            else -> return null
        }
        if (example.isBlank() && snapshot.bodyType != "FORM_DATA") return null
        return buildJsonObject {
            putJsonObject("content") {
                putJsonObject(mediaType) {
                    putJsonObject("schema") { put("type", "string") }
                    if (example.isNotBlank()) put("example", example)
                }
            }
        }
    }

    /** 从地址里取路径部分；解析失败时按最后一段兜底 */
    private fun openApiPath(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        val path = uri?.path?.takeIf { it.isNotBlank() } ?: "/"
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun operationId(name: String, method: String, usedIds: MutableSet<String>): String {
        val base = name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            .ifBlank { "request" }
        val id = "${method.lowercase()}_$base"
        var candidate = id
        var index = 2
        while (!usedIds.add(candidate)) {
            candidate = "${id}_$index"
            index++
        }
        return candidate
    }

    private const val POSTMAN_SCHEMA =
        "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
}
