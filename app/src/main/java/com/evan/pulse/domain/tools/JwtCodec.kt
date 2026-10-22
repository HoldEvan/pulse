package com.evan.pulse.domain.tools

import java.util.Base64

/** JWT 解码结果 */
data class JwtDecoded(
    val header: String,
    val payload: String,
    val signature: String,
    val valid: Boolean,
    val error: String = ""
)

/**
 * JWT 解码工具：把 JWT 拆成三段 Base64URL 并解出 header/payload。
 * 纯函数，便于单元测试。
 */
object JwtCodec {

    fun decode(token: String): JwtDecoded {
        if (token.isBlank()) return JwtDecoded("", "", "", false, "empty")
        val parts = token.trim().split('.')
        if (parts.size != 3) {
            return JwtDecoded("", "", "", false, "segments")
        }
        val (h, p, s) = parts
        val header = decodeSeg(h) ?: return JwtDecoded("", "", "", false, "header")
        val payload = decodeSeg(p) ?: return JwtDecoded("", "", "", false, "payload")
        return JwtDecoded(
            header = header,
            payload = payload,
            signature = s,
            valid = true
        )
    }

    private fun decodeSeg(seg: String): String? = try {
        // JWT 是 Base64URL，补齐填充后解码；宽松模式允许缺填充
        val normalized = seg.replace('-', '+').replace('_', '/')
        val padded = when (val r = normalized.length % 4) {
            2 -> normalized + "=="
            3 -> normalized + "="
            0 -> normalized
            else -> return null
        }
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}