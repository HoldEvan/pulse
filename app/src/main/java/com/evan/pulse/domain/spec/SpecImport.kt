package com.evan.pulse.domain.spec

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import com.evan.pulse.data.model.ImportGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/** 导入失败原因；UI 层按枚举映射成本地化文案 */
enum class SpecError {
    EMPTY,
    UNPARSEABLE,
    NOT_OPENAPI,
    NOT_RECOGNIZED,
    NO_OPERATIONS,
    FETCH_FAILED
}

/** 支持导入的 API 描述格式 */
enum class SpecFormat { OPENAPI, POSTMAN, HAR }

/** 解析结果：集合名 + 分组列表，三种格式共用一套预览与写入管线 */
data class ImportPlan(val collectionName: String, val groups: List<ImportGroup>)

class SpecImportException(val error: SpecError, message: String) : Exception(message)

/**
 * 规范文本入口：按首个非空白字符判别 JSON 还是 YAML，统一产出 JsonObject。
 */
object SpecParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseAny(raw: String): JsonObject {
        val text = raw.trim()
        if (text.isEmpty()) {
            throw SpecImportException(SpecError.EMPTY, "规范内容为空")
        }
        val head = text.first()
        if (head != '{' && head != '[') {
            return YamlToJson.toJson(text)
        }
        return try {
            json.parseToJsonElement(text) as? JsonObject
                ?: throw SpecImportException(SpecError.UNPARSEABLE, "JSON 根节点不是对象")
        } catch (e: SpecImportException) {
            throw e
        } catch (e: Exception) {
            throw SpecImportException(SpecError.UNPARSEABLE, "JSON 解析失败：${e.message}")
        }
    }
}

/**
 * YAML 转 JSON：先解析为 kaml 的节点树，再递归转换成 JsonElement。
 * 锚点/别名允许保留，转换时直接引用被引用的节点，等价于 YAML 原生语义。
 */
object YamlToJson {

    private const val MAX_DEPTH = 128

    private val yaml = Yaml(
        configuration = YamlConfiguration(anchorsAndAliases = AnchorsAndAliases.Permitted())
    )

    fun toJson(raw: String): JsonObject {
        val text = raw.trim()
        if (text.isEmpty()) {
            throw SpecImportException(SpecError.EMPTY, "规范内容为空")
        }
        val root = try {
            yaml.parseToYamlNode(text)
        } catch (e: SpecImportException) {
            throw e
        } catch (e: Exception) {
            throw SpecImportException(SpecError.UNPARSEABLE, "YAML 解析失败：${e.message}")
        }
        val map = root as? YamlMap
            ?: throw SpecImportException(SpecError.UNPARSEABLE, "YAML 根节点不是对象")
        return map.toMap(0)
    }

    private fun YamlMap.toMap(depth: Int): JsonObject {
        val map = LinkedHashMap<String, JsonElement>(entries.size)
        entries.forEach { (key, value) ->
            map.put(key.content, value.toElement(depth + 1))
        }
        return JsonObject(map)
    }

    private fun YamlNode.toElement(depth: Int): JsonElement {
        if (depth > MAX_DEPTH) {
            throw SpecImportException(SpecError.UNPARSEABLE, "YAML 嵌套层级过深")
        }
        return when (val node = this) {
            is YamlTaggedNode -> node.innerNode.toElement(depth)
            is YamlNull -> JsonNull
            is YamlList -> buildJsonArray {
                node.items.forEach { add(it.toElement(depth + 1)) }
            }
            is YamlMap -> node.toMap(depth)
            is YamlScalar -> node.toScalarJson()
        }
    }

    private fun YamlScalar.toScalarJson(): JsonElement {
        val content = this.content
        when {
            content.isEmpty() || content == "~" -> return JsonNull
            content.equals("null", ignoreCase = true) -> return JsonNull
        }
        if (content.equals("true", ignoreCase = true)) return JsonPrimitive(true)
        if (content.equals("false", ignoreCase = true)) return JsonPrimitive(false)

        val trimmed = content.trim()
        if (trimmed.matches(SCALAR_NUMBER)) {
            trimmed.toLongOrNull()?.let { return JsonPrimitive(it) }
            trimmed.toDoubleOrNull()?.let { return JsonPrimitive(it) }
        }
        return JsonPrimitive(content)
    }

    private val SCALAR_NUMBER = Regex("[-+]?(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*))?(?:[eE][-+]?[1-9][0-9]*)?")
}
