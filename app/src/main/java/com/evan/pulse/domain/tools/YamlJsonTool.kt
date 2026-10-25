package com.evan.pulse.domain.tools

import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Compose
import it.krzeminski.snakeyaml.engine.kmp.nodes.MappingNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Node
import it.krzeminski.snakeyaml.engine.kmp.nodes.ScalarNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.SequenceNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Tag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * YAML 与 JSON 互转。
 *
 * YAML → JSON 基于 snakeyaml-engine 的组合节点树：标量保留类型标签与引号信息，
 * 因此 `"123"` 仍是字符串、`123` 才是数字，不会因为内容长得像数字就被改写类型。
 * 根节点允许是映射、序列或标量。
 *
 * JSON → YAML 手写块式输出：只产出两种主流写法（块映射与块序列），
 * 不使用锚点、折叠标量与流式写法，保证结果在大多数解析器里行为一致。
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object YamlJsonTool {

    /** 转换结果；[error] 为语义编码，界面据此挑选文案 */
    data class Result(val text: String = "", val error: String? = null)

    private const val MAX_DEPTH = 128

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val composer = Compose(LoadSettings.builder().build())

    private val SCALAR_NUMBER =
        Regex("[-+]?(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*))?(?:[eE][-+]?[1-9][0-9]*)?")

    private val AMBIGUOUS_WORDS = setOf(
        "null", "~", "true", "false", "yes", "no", "on", "off", "y", "n"
    )

    private val FORBIDDEN_LEADING = "-?:,[]{}#&*!|>'\"%@`"

    fun yamlToJson(raw: String): Result {
        if (raw.isBlank()) return Result(error = "empty")
        val root = try {
            composer.compose(raw)
        } catch (e: Exception) {
            return Result(error = "yamlInvalid")
        } ?: return Result(text = "null")
        val element = try {
            root.toElement(0, mutableSetOf())
        } catch (e: Exception) {
            return Result(error = "depth")
        }
        return Result(text = json.encodeToString(JsonElement.serializer(), element))
    }

    fun jsonToYaml(raw: String): Result {
        if (raw.isBlank()) return Result(error = "empty")
        val element = try {
            Json.parseToJsonElement(raw)
        } catch (e: Exception) {
            return Result(error = "jsonInvalid")
        }
        val out = StringBuilder()
        return try {
            emit(element, 0, out, 0)
            Result(text = out.toString().trimEnd())
        } catch (e: Exception) {
            Result(error = "depth")
        }
    }

    // ---- YAML 节点 → JsonElement ----

    private fun Node.toElement(depth: Int, visiting: MutableSet<Node>): JsonElement {
        if (depth > MAX_DEPTH) throw IllegalStateException("yaml depth")
        // 锚点可指向祖先节点；同一节点在路径上重复出现即视为环
        if (!visiting.add(this)) throw IllegalStateException("yaml cycle")
        try {
            return when (this) {
                is MappingNode -> {
                    val map = LinkedHashMap<String, JsonElement>(value.size)
                    value.forEach { tuple ->
                        val key = (tuple.keyNode as? ScalarNode)?.value
                            ?: throw IllegalStateException("yaml key")
                        map[key] = tuple.valueNode.toElement(depth + 1, visiting)
                    }
                    JsonObject(map)
                }
                is SequenceNode -> JsonArray(
                    value.map { it.toElement(depth + 1, visiting) }
                )
                is ScalarNode -> toScalarJson()
                else -> throw IllegalStateException("yaml node")
            }
        } finally {
            visiting.remove(this)
        }
    }

    private fun ScalarNode.toScalarJson(): JsonElement = when (tag) {
        Tag.NULL -> JsonNull
        Tag.BOOL -> JsonPrimitive(value.equals("true", ignoreCase = true))
        Tag.INT -> intOrString(value)
        Tag.FLOAT -> value.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(value)
        else -> JsonPrimitive(value)
    }

    /** YAML 1.2 的整数含十六/八/二进制，先按字面量进制解析，失败则退回字符串 */
    private fun intOrString(raw: String): JsonElement {
        raw.toLongOrNull()?.let { return JsonPrimitive(it) }
        val radix = when {
            raw.startsWith("0x") || raw.startsWith("0X") -> 16
            raw.startsWith("0o") || raw.startsWith("0O") -> 8
            raw.startsWith("0b") || raw.startsWith("0B") -> 2
            else -> return JsonPrimitive(raw)
        }
        val digits = raw.substring(2).replace("_", "")
        return digits.toLongOrNull(radix)?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
    }

    // ---- JsonElement → YAML ----

    private fun emit(element: JsonElement, indent: Int, out: StringBuilder, depth: Int) {
        if (depth > MAX_DEPTH) throw IllegalStateException("json depth")
        when (element) {
            is JsonObject -> emitMap(element, indent, out, depth)
            is JsonArray -> emitList(element, indent, out, depth)
            else -> out.append("  ".repeat(indent)).append(scalarText(element)).append('\n')
        }
    }

    private fun emitMap(map: JsonObject, indent: Int, out: StringBuilder, depth: Int) {
        if (map.isEmpty()) {
            out.append("  ".repeat(indent)).append("{}\n")
            return
        }
        map.forEach { (key, value) ->
            val pad = "  ".repeat(indent)
            out.append(pad).append(plainOrQuoted(key)).append(':')
            when {
                isInline(value) -> out.append(' ').append(inlineText(value)).append('\n')
                isFlowEmpty(value) -> out.append(' ').append(flowEmptyText(value)).append('\n')
                else -> {
                    out.append('\n')
                    emit(value, indent + 1, out, depth + 1)
                }
            }
        }
    }

    private fun emitList(list: JsonArray, indent: Int, out: StringBuilder, depth: Int) {
        if (list.isEmpty()) {
            out.append("  ".repeat(indent)).append("[]\n")
            return
        }
        val pad = "  ".repeat(indent)
        list.forEach { item ->
            when {
                isInline(item) -> out.append(pad).append("- ").append(inlineText(item)).append('\n')
                isFlowEmpty(item) -> out.append(pad).append("- ").append(flowEmptyText(item)).append('\n')
                else -> {
                    out.append(pad).append("-\n")
                    emit(item, indent + 1, out, depth + 1)
                }
            }
        }
    }

    private fun isInline(element: JsonElement): Boolean = element !is JsonObject && element !is JsonArray

    private fun inlineText(element: JsonElement): String = when (element) {
        is JsonPrimitive -> if (element.isString) {
            plainOrQuoted(element.content)
        } else {
            element.toString()
        }
        JsonNull -> "null"
        else -> element.toString()
    }

    private fun isFlowEmpty(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.isEmpty()
        is JsonArray -> element.isEmpty()
        else -> false
    }

    private fun flowEmptyText(element: JsonElement): String = when (element) {
        is JsonObject -> "{}"
        is JsonArray -> "[]"
        else -> element.toString()
    }

    private fun scalarText(element: JsonElement): String = when (element) {
        JsonNull -> "null"
        is JsonPrimitive -> if (element.isString) plainOrQuoted(element.content) else element.toString()
        else -> element.toString()
    }

    /** 能直接以裸文本写出就写出，否则用双引号包裹并转义 */
    private fun plainOrQuoted(value: String): String =
        if (isPlainSafe(value)) value else quote(value)

    private fun isPlainSafe(value: String): Boolean {
        if (value.isEmpty()) return false
        if (value.first().isWhitespace() || value.last().isWhitespace()) return false
        if (value.first() in FORBIDDEN_LEADING) return false
        if (value.any { it == '\n' || it == '\r' || it == '\t' }) return false
        if (value.contains(": ") || value.contains(" #")) return false
        if (value.endsWith(':')) return false
        if (value.lowercase() in AMBIGUOUS_WORDS) return false
        if (SCALAR_NUMBER.matches(value)) return false
        return true
    }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
