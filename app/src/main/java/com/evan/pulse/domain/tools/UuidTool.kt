package com.evan.pulse.domain.tools

import java.util.Locale
import java.util.UUID

object UuidTool {

    /** 生成一个随机 UUID v4 的标准字符串 */
    fun generate(): String = UUID.randomUUID().toString()

    /** 生成去掉连字符的小写紧凑形式 */
    fun generateCompact(): String = generate().replace("-", "")

    /** 校验是否为合法的标准 UUID 字符串（接受带/不带连字符） */
    fun isValid(text: String): Boolean {
        val normalized = normalize(text) ?: return false
        return try {
            UUID.fromString(normalized)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** 把紧凑或带连字符的 UUID 规范化成标准小写带连字符形式；非法返回 null */
    fun normalize(text: String): String? {
        val trimmed = text.trim().lowercase(Locale.getDefault())
        val compact = trimmed.replace("-", "")
        if (compact.length != 32 || !compact.all { it.isDigit() || it in 'a'..'f' }) return null
        val sb = StringBuilder(36)
        compact.forEachIndexed { i, c ->
            if (i == 8 || i == 12 || i == 16 || i == 20) sb.append('-')
            sb.append(c)
        }
        return sb.toString()
    }
}