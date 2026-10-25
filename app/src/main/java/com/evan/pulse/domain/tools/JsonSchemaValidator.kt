package com.evan.pulse.domain.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.round

/**
 * 单条校验失败。
 *
 * [keyword] 是稳定的语义编码（`type`、`required`、`enum` ……），界面据此挑选对应文案，
 * 领域层不产出面向用户的句子。[expected] 与 [actual] 是可直接展示的对比值。
 */
data class SchemaViolation(
    val path: String,
    val keyword: String,
    val expected: String = "",
    val actual: String = "",
)

/** 校验结果；[schemaError] 非空表示 schema 本身不可用，此时不产出 [violations] */
data class SchemaReport(
    val violations: List<SchemaViolation> = emptyList(),
    val schemaError: String? = null,
    /** 命中的失败条数超过上限，列表被截断 */
    val truncated: Boolean = false,
) {
    val valid: Boolean get() = schemaError == null && violations.isEmpty()
}

/**
 * JSON Schema（draft-07 常用子集）校验器。
 *
 * 支持的关键字：
 * - 结构：`type`、`properties`、`required`、`additionalProperties`、`items`（含元组写法）、
 *   `minItems`、`maxItems`、`uniqueItems`
 * - 取值：`enum`、`const`、`minimum`、`maximum`、`exclusiveMinimum`、`exclusiveMaximum`、`multipleOf`
 * - 字符串：`minLength`、`maxLength`、`pattern`
 * - 组合：`allOf`、`anyOf`、`oneOf`、`not`
 * - 引用：`$ref` 指向根文档内的 `#/definitions/...` 或 `#/$defs/...`
 *
 * `format`、`patternProperties`、`dependencies` 等其余关键字按「未声明」处理，
 * 只做结构校验，不做语义猜测。
 */
object JsonSchemaValidator {

    /** 单次校验最多记录多少条失败，避免超大文档把界面拖垮 */
    const val MAX_VIOLATIONS = 100

    private const val MAX_DEPTH = 64

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private val parseJson = Json { ignoreUnknownKeys = true }

    /** 从文本解析 schema 与实例后校验；任一解析失败都归入 [SchemaReport.schemaError] */
    fun validate(schemaText: String, instanceText: String): SchemaReport {
        val schema = runCatching { parseJson.parseToJsonElement(schemaText) }.getOrNull()
            ?: return SchemaReport(schemaError = "schemaInvalid")
        if (schema !is JsonObject) return SchemaReport(schemaError = "schemaType")
        val instance = runCatching { parseJson.parseToJsonElement(instanceText) }.getOrNull()
            ?: return SchemaReport(schemaError = "dataInvalid")
        return validate(schema, instance)
    }

    fun validate(schema: JsonObject, instance: JsonElement): SchemaReport {
        val ctx = Context(root = schema)
        evaluate(schema, instance, "$", ctx, 0)
        if (ctx.refError != null) return SchemaReport(schemaError = ctx.refError)
        return SchemaReport(
            violations = ctx.violations.toList(),
            truncated = ctx.truncated,
        )
    }

    private class Context(val root: JsonObject) {
        val violations = mutableListOf<SchemaViolation>()
        var truncated = false
        var refError: String? = null

        fun add(violation: SchemaViolation) {
            if (violations.size >= MAX_VIOLATIONS) {
                truncated = true
                return
            }
            violations += violation
        }
    }

    private fun evaluate(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
        depth: Int,
    ) {
        if (ctx.refError != null || ctx.truncated) return
        if (depth > MAX_DEPTH) {
            ctx.refError = "depth"
            return
        }

        val resolved = resolveRef(schema, ctx)
        if (resolved == null) {
            ctx.refError = "refNotFound"
            return
        }
        if (resolved !== schema) {
            evaluate(resolved, instance, path, ctx, depth + 1)
            return
        }

        if (!typeMatches(schema, instance, path, ctx)) return

        checkEnumAndConst(schema, instance, path, ctx)
        checkNumber(schema, instance, path, ctx)
        checkString(schema, instance, path, ctx)
        checkArray(schema, instance, path, ctx, depth)
        checkObject(schema, instance, path, ctx, depth)
        checkCombinators(schema, instance, path, ctx, depth)
    }

    private fun resolveRef(schema: JsonObject, ctx: Context): JsonObject? {
        val ref = (schema["\$ref"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return schema
        if (!ref.startsWith("#")) return schema
        if (ref == "#") return ctx.root
        val segments = ref.removePrefix("#/").split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return ctx.root
        var current: JsonElement = ctx.root
        for (raw in segments) {
            val segment = raw.replace("~1", "/").replace("~0", "~")
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> segment.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return current as? JsonObject
    }

    /** 校验 `type`；返回 false 表示类型不符，其余关键字不再求值 */
    private fun typeMatches(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
    ): Boolean {
        val raw = schema["type"] ?: return true
        val expected = when (raw) {
            is JsonPrimitive -> listOfNotNull(raw.stringValue())
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.stringValue() }
            else -> emptyList()
        }
        if (expected.isEmpty()) return true
        val matches = expected.any { it == typeOf(instance) || (it == "integer" && isInteger(instance)) }
        if (matches) return true
        ctx.add(
            SchemaViolation(
                path = path,
                keyword = "type",
                expected = expected.joinToString(" | "),
                actual = typeOf(instance),
            )
        )
        return false
    }

    private fun checkEnumAndConst(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
    ) {
        (schema["const"])?.let { constant ->
            if (constant != instance) {
                ctx.add(
                    SchemaViolation(
                        path = path,
                        keyword = "const",
                        expected = constant.toString(),
                        actual = instance.toString(),
                    )
                )
            }
        }
        val allowed = schema["enum"] as? JsonArray ?: return
        if (allowed.none { it == instance }) {
            ctx.add(
                SchemaViolation(
                    path = path,
                    keyword = "enum",
                    expected = allowed.joinToString(", ") { it.toString() },
                    actual = instance.toString(),
                )
            )
        }
    }

    private fun checkNumber(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
    ) {
        val value = numberOrNull(instance) ?: return
        schema.number("minimum")?.let {
            if (value < it) ctx.add(SchemaViolation(path, "minimum", it.toString(), value.toString()))
        }
        schema.number("maximum")?.let {
            if (value > it) ctx.add(SchemaViolation(path, "maximum", it.toString(), value.toString()))
        }
        schema.number("exclusiveMinimum")?.let {
            if (value <= it) {
                ctx.add(SchemaViolation(path, "exclusiveMinimum", it.toString(), value.toString()))
            }
        }
        schema.number("exclusiveMaximum")?.let {
            if (value >= it) {
                ctx.add(SchemaViolation(path, "exclusiveMaximum", it.toString(), value.toString()))
            }
        }
        schema.number("multipleOf")?.let { divisor ->
            if (divisor == 0.0) return@let
            val quotient = value / divisor
            if (abs(quotient - round(quotient)) > 1e-9) {
                ctx.add(SchemaViolation(path, "multipleOf", divisor.toString(), value.toString()))
            }
        }
    }

    private fun checkString(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
    ) {
        val text = (instance as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return
        val length = text.length
        schema.number("minLength")?.let {
            if (length < it) {
                ctx.add(SchemaViolation(path, "minLength", it.toInt().toString(), length.toString()))
            }
        }
        schema.number("maxLength")?.let {
            if (length > it) {
                ctx.add(SchemaViolation(path, "maxLength", it.toInt().toString(), length.toString()))
            }
        }
        (schema["pattern"] as? JsonPrimitive)?.stringValue()?.let { pattern ->
            val regex = runCatching { Regex(pattern) }.getOrNull()
            if (regex == null) {
                ctx.refError = "pattern"
                return
            }
            if (!regex.containsMatchIn(text)) {
                ctx.add(SchemaViolation(path, "pattern", pattern, text.take(40)))
            }
        }
    }

    private fun checkArray(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
        depth: Int,
    ) {
        val array = instance as? JsonArray ?: return
        schema.number("minItems")?.let {
            if (array.size < it) {
                ctx.add(SchemaViolation(path, "minItems", it.toInt().toString(), array.size.toString()))
            }
        }
        schema.number("maxItems")?.let {
            if (array.size > it) {
                ctx.add(SchemaViolation(path, "maxItems", it.toInt().toString(), array.size.toString()))
            }
        }
        if ((schema["uniqueItems"] as? JsonPrimitive)?.booleanOrNull == true) {
            val unique = array.map { it.toString() }.toSet()
            if (unique.size != array.size) {
                ctx.add(SchemaViolation(path, "uniqueItems", "true", array.size.toString()))
            }
        }
        when (val items = schema["items"]) {
            is JsonObject -> array.forEachIndexed { index, element ->
                evaluate(items, element, indexPath(path, index), ctx, depth + 1)
            }
            is JsonArray -> array.forEachIndexed { index, element ->
                val sub = items.getOrNull(index) as? JsonObject ?: return@forEachIndexed
                evaluate(sub, element, indexPath(path, index), ctx, depth + 1)
            }
            else -> Unit
        }
    }

    private fun checkObject(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
        depth: Int,
    ) {
        val obj = instance as? JsonObject ?: return
        (schema["required"] as? JsonArray)?.forEach { entry ->
            val name = (entry as? JsonPrimitive)?.stringValue() ?: return@forEach
            if (!obj.containsKey(name)) {
                ctx.add(SchemaViolation(childPath(path, name), "required", name, ""))
            }
        }
        val properties = schema["properties"] as? JsonObject
        if (properties != null) {
            properties.forEach { (name, sub) ->
                val value = obj[name] ?: return@forEach
                val subSchema = sub as? JsonObject ?: return@forEach
                evaluate(subSchema, value, childPath(path, name), ctx, depth + 1)
            }
        }
        val known = properties?.keys ?: emptySet()
        obj.keys.filter { it !in known }.forEach { name ->
            when (val extra = schema["additionalProperties"]) {
                is JsonPrimitive -> {
                    if (extra.booleanOrNull == false) {
                        ctx.add(SchemaViolation(childPath(path, name), "additionalProperties", name, ""))
                    }
                }
                is JsonObject -> evaluate(
                    extra,
                    obj.getValue(name),
                    childPath(path, name),
                    ctx,
                    depth + 1,
                )
                else -> Unit
            }
        }
    }

    private fun checkCombinators(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        ctx: Context,
        depth: Int,
    ) {
        (schema["allOf"] as? JsonArray)?.forEach { sub ->
            (sub as? JsonObject)?.let { evaluate(it, instance, path, ctx, depth + 1) }
        }
        (schema["anyOf"] as? JsonArray)?.let { branches ->
            val schemas = branches.filterIsInstance<JsonObject>()
            if (schemas.isEmpty()) return@let
            val passed = schemas.any { branchPasses(it, instance, path, ctx, depth) }
            if (!passed) {
                ctx.add(SchemaViolation(path, "anyOf", schemas.size.toString(), "0"))
            }
        }
        (schema["oneOf"] as? JsonArray)?.let { branches ->
            val schemas = branches.filterIsInstance<JsonObject>()
            if (schemas.isEmpty()) return@let
            val hits = schemas.count { branchPasses(it, instance, path, ctx, depth) }
            if (hits != 1) {
                ctx.add(SchemaViolation(path, "oneOf", "1", hits.toString()))
            }
        }
        (schema["not"] as? JsonObject)?.let { branch ->
            if (branchPasses(branch, instance, path, ctx, depth)) {
                ctx.add(SchemaViolation(path, "not", "", ""))
            }
        }
    }

    /** 组合关键字里的分支试跑：沿用同一根文档解析 $ref，但结果留在临时上下文里 */
    private fun branchPasses(
        schema: JsonObject,
        instance: JsonElement,
        path: String,
        parent: Context,
        depth: Int,
    ): Boolean {
        val scratch = Context(root = parent.root)
        evaluate(schema, instance, path, scratch, depth + 1)
        if (scratch.refError != null) parent.refError = scratch.refError
        return scratch.refError == null && scratch.violations.isEmpty()
    }

    private fun JsonPrimitive.stringValue(): String? = if (isString) content else null

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun numberOrNull(element: JsonElement): Double? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString || primitive is JsonNull) return null
        if (primitive.booleanOrNull != null) return null
        return primitive.doubleOrNull
    }

    private fun isInteger(element: JsonElement): Boolean {
        val value = numberOrNull(element) ?: return false
        return abs(value - round(value)) < 1e-9
    }

    private fun typeOf(element: JsonElement): String = when (element) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.booleanOrNull != null -> "boolean"
            else -> "number"
        }
    }

    private fun childPath(base: String, key: String): String =
        if (IDENTIFIER.matches(key)) "$base.$key" else "$base['${key.replace("'", "\\'")}']"

    private fun indexPath(base: String, index: Int): String = "$base[$index]"
}
