package com.evan.pulse.domain.crypto

import java.util.Base64

/**
 * 字段级加密的最小接口：只负责明密文字节互转，密钥来源与算法由实现决定。
 *
 * 抽成接口是为了让持久化层能在 JVM 单测里跑通整条加解密链路，
 * Android 侧的 Keystore 实现单独放在 data 层。
 */
interface SecretBox {

    /** 加密明文字节；同一明文每次结果应不同（实现需自带随机 IV） */
    fun encrypt(plain: ByteArray): ByteArray

    /** 解密 [encrypt] 的输出；密文被截断或密钥不匹配时抛异常 */
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * 敏感字段的文本封装格式：`enc1:` + Base64(iv + 密文)。
 *
 * 前缀让明文与密文共存于同一个 JSON 字段里：旧数据、旧备份里的明文原样读回，
 * 下一次写盘时才被加密。解密失败（换机器、密钥失效、数据损坏）返回空串，
 * 宁可让用户重新填写凭据，也不把密文当成内容显示出来。
 */
object SecretField {

    const val PREFIX = "enc1:"

    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)

    /** 加密单个字段；空串不加密，避免产生一堆无意义的密文 */
    fun seal(plain: String, box: SecretBox): String {
        if (plain.isEmpty()) return plain
        val sealed = box.encrypt(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(sealed)
    }

    /** 解密单个字段：非密文原样返回，无密钥或解密失败返回空串 */
    fun open(value: String, box: SecretBox?): String {
        if (!isSealed(value)) return value
        if (box == null) return ""
        val payload = value.removePrefix(PREFIX)
        val sealed = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return ""
        return runCatching { String(box.decrypt(sealed), Charsets.UTF_8) }.getOrElse { "" }
    }
}
