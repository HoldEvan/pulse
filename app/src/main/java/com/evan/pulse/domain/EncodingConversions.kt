package com.evan.pulse.domain

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * 信息码转换的纯函数集合：Base64、进制、URL、Unicode、ASCII 互转。
 * 全部为无副作用的计算，供信息码工具页组合展示，也便于单元测试。
 */

/** 进制转换支持的进制，与 UI 上的选择器一一对应 */
val radixBases = intArrayOf(2, 8, 10, 16)

/** Base64 编码（无换行），输入按 UTF-8 字节处理 */
fun base64Encode(text: String): String =
    Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

/** Base64 解码，容忍换行等空白；非法输入返回 null */
fun base64Decode(text: String): String? = try {
    String(Base64.getMimeDecoder().decode(text.trim()), Charsets.UTF_8)
} catch (_: IllegalArgumentException) {
    null
}

/** Base64 编码二进制数据（如图片字节） */
fun base64EncodeBytes(data: ByteArray): String =
    Base64.getEncoder().encodeToString(data)

/**
 * Base64 解码为原始字节（如图片字节）；容忍换行等空白，
 * 自动剥离 data URI 前缀（data:image/...;base64,），非法输入返回 null
 */
fun base64DecodeBytes(text: String): ByteArray? {
    val raw = text.trim()
    val payload = if (raw.startsWith("data:", ignoreCase = true)) {
        val marker = ";base64,"
        val markerIndex = raw.indexOf(marker, ignoreCase = true)
        if (markerIndex >= 0) raw.substring(markerIndex + marker.length) else raw
    } else {
        raw
    }
    return try {
        Base64.getMimeDecoder().decode(payload)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** 按 [base] 进制解析整数；含非法字符或超出 Long 范围返回 null */
fun parseRadix(text: String, base: Int): Long? = text.trim().toLongOrNull(base)

/** 把 [value] 写成 [base] 进制字符串（负数带符号） */
fun formatRadix(value: Long, base: Int): String = value.toString(base)

/** URL 组件风格百分号编码：空格编码为 %20 而非表单风格的 + */
fun urlEncode(text: String): String =
    URLEncoder.encode(text, "UTF-8").replace("+", "%20")

/** URL 解码（同时接受 %20 与 + 表示的空格）；非法转义序列返回 null */
fun urlDecode(text: String): String? = try {
    URLDecoder.decode(text, "UTF-8")
} catch (_: IllegalArgumentException) {
    null
}

/** 文本转 Unicode 转义：非 ASCII 字符写成 \\uXXXX（小写十六进制），ASCII 原样保留 */
fun unicodeEscape(text: String): String = buildString {
    for (ch in text) {
        if (ch.code < 0x80) {
            append(ch)
        } else {
            append("\\u")
            append(ch.code.toString(16).padStart(4, '0'))
        }
    }
}

/** Unicode 转义还原：\\uXXXX 换回对应字符，其余文本原样保留 */
fun unicodeUnescape(text: String): String =
    Regex("\\\\u([0-9a-fA-F]{4})").replace(text) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }

/** 文本转字符码：返回每个字符（UTF-16 码元）的十进制码 */
fun asciiDecimalCodes(text: String): List<Int> = text.map { it.code }

/** 文本转字符码：返回每个字符的大写十六进制码（至少两位） */
fun asciiHexCodes(text: String): List<String> =
    text.map { it.code.toString(16).uppercase().padStart(2, '0') }

/**
 * 字符码转文本：接受空白或逗号（含全角逗号）分隔的十进制码，
 * 或 0x 前缀的十六进制码；含非法码值（越界或代理区）返回 null
 */
fun asciiFromCodes(text: String): String? {
    val tokens = text.trim().split(Regex("[\\s,，]+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    return buildString {
        for (token in tokens) {
            val code = if (token.startsWith("0x", ignoreCase = true)) {
                token.substring(2).toIntOrNull(16)
            } else {
                token.toIntOrNull(10)
            } ?: return null
            if (code !in 0..0x10FFFF || code in 0xD800..0xDFFF) return null
            append(String(Character.toChars(code)))
        }
    }
}
