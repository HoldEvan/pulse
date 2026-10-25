package com.evan.pulse.domain.net

import java.util.Base64

/** 二进制帧的文本表示方式 */
enum class BinaryEncoding { HEX, BASE64 }

/**
 * WebSocket 二进制帧与文本之间的转换。
 *
 * 发送时把输入框里的 hex/base64 文本解码为字节；接收时把字节编码成文本预览。
 * 解码失败统一返回 [Result.failure]，由界面提示具体原因，不做静默兜底。
 */
object BinaryPayload {

    /** 日志预览最多展示的字节数，超出部分用省略号代替 */
    const val PREVIEW_LIMIT = 256

    /** 解码输入文本；允许空白与 `0x` 前缀、hex 允许冒号分隔 */
    fun decode(text: String, encoding: BinaryEncoding): Result<ByteArray> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return Result.failure(IllegalArgumentException("empty"))
        return when (encoding) {
            BinaryEncoding.HEX -> decodeHex(cleaned)
            BinaryEncoding.BASE64 -> try {
                Result.success(Base64.getDecoder().decode(cleaned.filterNot { it.isWhitespace() }))
            } catch (_: IllegalArgumentException) {
                Result.failure(IllegalArgumentException("invalid base64"))
            }
        }
    }

    /** 字节转 hex：大写、无分隔 */
    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX_CHARS[v ushr 4]).append(HEX_CHARS[v and 0x0F])
        }
        return out.toString()
    }

    /** 字节转 base64 */
    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /**
     * 生成日志预览：前 [PREVIEW_LIMIT] 字节按 [encoding] 编码，
     * 超出时追加省略号，保证长帧不会把日志撑爆。
     */
    fun preview(bytes: ByteArray, encoding: BinaryEncoding): String {
        val head = if (bytes.size > PREVIEW_LIMIT) bytes.copyOf(PREVIEW_LIMIT) else bytes
        val text = when (encoding) {
            BinaryEncoding.HEX -> hex(head)
            BinaryEncoding.BASE64 -> base64(head)
        }
        return if (bytes.size > PREVIEW_LIMIT) "$text…" else text
    }

    private fun decodeHex(text: String): Result<ByteArray> {
        val body = text.removePrefix("0x").removePrefix("0X")
            .filterNot { it.isWhitespace() || it == ':' }
        if (body.isEmpty()) return Result.failure(IllegalArgumentException("empty hex"))
        if (body.length % 2 != 0) return Result.failure(IllegalArgumentException("odd hex length"))
        val out = ByteArray(body.length / 2)
        for (i in out.indices) {
            val high = hexValue(body[i * 2])
            val low = hexValue(body[i * 2 + 1])
            if (high < 0 || low < 0) {
                return Result.failure(IllegalArgumentException("invalid hex digit"))
            }
            out[i] = ((high shl 4) or low).toByte()
        }
        return Result.success(out)
    }

    private fun hexValue(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        in 'A'..'F' -> ch - 'A' + 10
        else -> -1
    }

    private const val HEX_CHARS = "0123456789ABCDEF"
}
