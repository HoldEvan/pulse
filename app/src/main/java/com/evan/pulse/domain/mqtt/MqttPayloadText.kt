package com.evan.pulse.domain.mqtt

import com.evan.pulse.domain.net.BinaryEncoding
import com.evan.pulse.domain.net.BinaryPayload
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * MQTT 负载的展示判定：合法 UTF-8 且不含控制字符（换行/回车/制表除外）视为文本，
 * 否则视为二进制，交给界面按十六进制预览。
 */
object MqttPayloadText {

    /** 返回可直接展示的文本；二进制时返回 null，由调用方决定如何展示 */
    fun textOrNull(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        val hasControl = text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        return if (hasControl) null else text
    }

    fun isBinary(bytes: ByteArray): Boolean = textOrNull(bytes) == null

    /** 二进制负载的十六进制预览，超长截断 */
    fun hexPreview(bytes: ByteArray): String = BinaryPayload.preview(bytes, BinaryEncoding.HEX)
}
