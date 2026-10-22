package com.evan.pulse.domain.tools

import java.security.MessageDigest

/** Hash 算法枚举 */
enum class HashAlgorithm(val id: String) {
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256")
}

object HashTool {

    /** 计算文本的哈希摘要，十六进制小写；空输入返回空串 */
    fun hash(text: String, algorithm: HashAlgorithm): String {
        if (text.isEmpty()) return ""
        val digest = MessageDigest.getInstance(algorithm.id)
            .digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { String.format("%02x", it) }
    }
}