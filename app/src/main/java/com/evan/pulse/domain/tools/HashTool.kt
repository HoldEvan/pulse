package com.evan.pulse.domain.tools

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 摘要算法。
 *
 * 枚举顺序即界面顺序；新增算法一律追加在末尾，避免打乱既有下标。
 */
enum class HashAlgorithm(val id: String, val macId: String) {
    MD5("MD5", "HmacMD5"),
    SHA1("SHA-1", "HmacSHA1"),
    SHA256("SHA-256", "HmacSHA256"),
    SHA384("SHA-384", "HmacSHA384"),
    SHA512("SHA-512", "HmacSHA512")
}

/** 摘要输出编码 */
enum class DigestOutput { HEX, BASE64 }

object HashTool {

    /** 计算文本的哈希摘要，十六进制小写；空输入返回空串 */
    fun hash(text: String, algorithm: HashAlgorithm): String =
        hash(text, algorithm, DigestOutput.HEX)

    fun hash(text: String, algorithm: HashAlgorithm, output: DigestOutput): String {
        if (text.isEmpty()) return ""
        val digest = MessageDigest.getInstance(algorithm.id)
            .digest(text.toByteArray(Charsets.UTF_8))
        return encode(digest, output)
    }

    /**
     * 计算 HMAC 摘要。
     *
     * 密钥为空时返回空串：HMAC 没有密钥就没有意义，这与空文本返回空串的约定一致。
     * 文本允许为空，等价于对空消息做认证。
     */
    fun hmac(
        text: String,
        key: String,
        algorithm: HashAlgorithm,
        output: DigestOutput = DigestOutput.HEX,
    ): String {
        if (key.isEmpty()) return ""
        val mac = Mac.getInstance(algorithm.macId)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm.macId))
        return encode(mac.doFinal(text.toByteArray(Charsets.UTF_8)), output)
    }

    private fun encode(bytes: ByteArray, output: DigestOutput): String = when (output) {
        DigestOutput.HEX -> bytes.joinToString("") { String.format("%02x", it) }
        DigestOutput.BASE64 -> Base64.getEncoder().encodeToString(bytes)
    }
}
