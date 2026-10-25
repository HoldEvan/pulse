package com.evan.pulse.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.evan.pulse.domain.crypto.SecretBox
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的 AES-256-GCM 实现。
 *
 * 密钥不可导出，只能在本机的应用沙箱内用于加解密：数据文件被拷走也无法还原明文，
 * 代价是换机恢复后需要重新填写凭据。
 *
 * 每次加密生成新的随机 IV，输出为 `iv || 密文 || 认证标签`，GCM 的标签由
 * [Cipher.doFinal] 自动附加，解密时一并校验，密文被改动会直接失败。
 */
class KeystoreSecretBox(private val alias: String = DEFAULT_ALIAS) : SecretBox {

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "密文长度不足" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            // 不要求用户认证：后台自动备份与定时拨测也会触发写盘
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "pulse_secret_fields_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256

        /** GCM 标准 96 位 IV，短 IV 兼容性最好 */
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
