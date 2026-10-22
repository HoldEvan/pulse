package com.evan.pulse.domain.tools

/** 正则测试结果 */
data class RegexMatchResult(
    val matched: Boolean,
    val patternError: String? = null,
    /** 各匹配的全部捕获组，每组一 List<String>，下标 0 为整体 */
    val groups: List<List<String>> = emptyList(),
    val count: Int = 0
)

object RegexTool {

    /** 用 pattern 在 text 上做全局匹配，返回匹配与捕获组；pattern 非法时 patternError 非空 */
    fun test(pattern: String, text: String): RegexMatchResult {
        if (pattern.isBlank()) return RegexMatchResult(matched = false, patternError = "empty")
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            return RegexMatchResult(matched = false, patternError = e.message)
        }
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return RegexMatchResult(matched = false)
        val groups = matches.map { m -> m.groupValues }
        return RegexMatchResult(
            matched = true,
            groups = groups,
            count = matches.size
        )
    }
}