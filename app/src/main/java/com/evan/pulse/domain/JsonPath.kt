package com.evan.pulse.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 极简 JSONPath 求值器，专门服务于 SSE 拼接预览场景。
 *
 * 支持子集：
 * - `$`           根节点（可省略）
 * - `.field`      对象属性
 * - `['field']`   属性（兼容带引号写法）
 * - `[0]`         数字下标
 * - `[*]`         数组通配（展开所有元素）
 *
 * 不支持过滤器 `[?()]`、递归下降 `..`、切片等高级语法。
 * 求值失败返回空，不抛异常。
 */
object JsonPath {

    private sealed interface Token {
        data class Field(val name: String) : Token
        data class Index(val index: Int) : Token
        data object Wildcard : Token
    }

    /**
     * 解析 JSONPath 为 token 序列。非法表达式返回 null。
     * 语法：`[$]? ( '.' field | '[' ( '*' | '0'..'9'+ ) | '[\'' field '\']' )*`
     */
    private fun parse(path: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = if (path.startsWith("$")) 1 else if (path.isBlank()) return null else 0
        val n = path.length

        while (i < n) {
            when (path[i]) {
                '.' -> {
                    val start = i + 1
                    var j = start
                    while (j < n && path[j] != '.' && path[j] != '[') j++
                    if (j == start) return null
                    tokens += Token.Field(path.substring(start, j))
                    i = j
                }

                '[' -> {
                    val close = path.indexOf(']', i)
                    if (close < 0) return null
                    val inner = path.substring(i + 1, close).trim()
                    i = close + 1
                    when {
                        inner == "*" -> tokens += Token.Wildcard
                        inner.length >= 2 &&
                            inner.first() == '\'' && inner.last() == '\'' -> {
                            tokens += Token.Field(inner.substring(1, inner.length - 1))
                        }

                        inner.toIntOrNull() != null -> tokens += Token.Index(inner.toInt())
                        else -> return null
                    }
                }

                else -> return null
            }
        }
        return tokens
    }

    /** 在 [root] 上求值 [path]，返回所有命中的元素；表达式非法返回空列表 */
    fun eval(root: JsonElement, path: String): List<JsonElement> {
        val tokens = parse(path.trim()) ?: return emptyList()
        var current: List<JsonElement> = listOf(root)
        for (token in tokens) {
            val next = mutableListOf<JsonElement>()
            for (element in current) {
                when (token) {
                    is Token.Field -> (element as? JsonObject)?.get(token.name)?.let(next::add)
                    is Token.Index -> (element as? JsonArray)?.getOrNull(token.index)?.let(next::add)
                    Token.Wildcard -> when (element) {
                        is JsonArray -> next.addAll(element)
                        is JsonObject -> next.addAll(element.values)
                        else -> {}
                    }
                }
            }
            current = next
            if (current.isEmpty()) return emptyList()
        }
        return current
    }

    /** 字符串形式的 json 文本求值：解析失败返回 null */
    fun eval(jsonText: String, path: String): List<JsonElement>? = try {
        val root = Json.parseToJsonElement(jsonText)
        eval(root, path)
    } catch (_: Exception) {
        null
    }
}

/** 命中的元素转成拼接文本：字符串用原值，数字/布尔原样，对象/数组压缩成 JSON */
fun JsonElement.extractString(): String = when (this) {
    is JsonPrimitive -> content
    else -> this.toString()
}