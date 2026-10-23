package com.evan.pulse.domain.tools

/**
 * 内置常用提取规则。
 *
 * [pattern] 是唯一事实来源，界面只负责展示名称；这样新增一种提取只需要在枚举里
 * 补一条规则并加对应文案，不需要同步维护第二份正则。
 *
 * [trimTrailing] 用于去掉网址这类表达式容易顺带匹配上的尾部标点。
 */
enum class ExtractPreset(val pattern: String, val trimTrailing: String = "") {
    URL(
        // 中文正文常不加空格，这里把 CJK 与全角标点一起排除，避免网址把后面的汉字吞进来
        pattern = """(?:https?://|ftp://|www\.)[^\s"'<>()（）\u4e00-\u9fff，。；：！？、]+""",
        trimTrailing = ".,;:!?)]}，。；：！？）】"
    ),
    MOBILE("""(?<!\d)1[3-9]\d{9}(?!\d)"""),
    EMAIL("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
    IPV4("""\b(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\b"""),
    MAC("""\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b"""),
    ID_CARD("""(?<!\d)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\d)"""),
    DATE("""\b\d{4}[-/.]\d{1,2}[-/.]\d{1,2}\b"""),
    TIME("""\b(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?\b"""),
    UUID("""\b[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\b"""),
    HEX_COLOR("""#[0-9A-Fa-f]{6}\b|#[0-9A-Fa-f]{3}\b"""),
    NUMBER("""-?\d+(?:\.\d+)?"""),
    CHINESE("""[\u4e00-\u9fa5]+""")
}

/** 按预设规则提取的结果；[patternError] 非空表示规则本身非法（内置规则正常不会出现） */
data class ExtractResult(
    val values: List<String> = emptyList(),
    val patternError: String? = null,
) {
    val count: Int get() = values.size
}

object RegexExtractor {

    /** 用预设规则做全局提取，[distinct] 为 true 时按首次出现顺序去重 */
    fun extract(preset: ExtractPreset, text: String, distinct: Boolean = false): ExtractResult {
        if (text.isEmpty()) return ExtractResult()
        val regex = try {
            Regex(preset.pattern)
        } catch (e: Exception) {
            return ExtractResult(patternError = e.message)
        }
        val matches = regex.findAll(text)
            .map { match ->
                if (preset.trimTrailing.isEmpty()) {
                    match.value
                } else {
                    match.value.trimEnd { it in preset.trimTrailing }
                }
            }
            .filter { it.isNotEmpty() }
        val values = if (distinct) matches.distinct().toList() else matches.toList()
        return ExtractResult(values = values)
    }
}
