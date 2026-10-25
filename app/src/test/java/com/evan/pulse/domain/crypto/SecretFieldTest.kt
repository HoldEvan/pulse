package com.evan.pulse.domain.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用可逆的假实现代替 Keystore：这里要验证的是文本封装格式与降级行为，
 * 真正的 AES-GCM 由 Android Keystore 保证，JVM 单测不覆盖。
 */
private object FakeBox : SecretBox {
    private var counter = 0

    override fun encrypt(plain: ByteArray): ByteArray {
        counter += 1
        // 首字节模拟随机 IV，保证同一明文每次密文不同
        return byteArrayOf(counter.toByte()) + plain.reversedArray()
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.isNotEmpty()) { "密文为空" }
        return sealed.copyOfRange(1, sealed.size).reversedArray()
    }
}

private object BrokenBox : SecretBox {
    override fun encrypt(plain: ByteArray): ByteArray = plain
    override fun decrypt(sealed: ByteArray): ByteArray = throw IllegalStateException("密钥失效")
}

class SecretFieldTest {

    @Test
    fun sealAddsPrefix() {
        val sealed = SecretField.seal("token", FakeBox)
        assertTrue(SecretField.isSealed(sealed))
        assertTrue(sealed.startsWith("enc1:"))
    }

    @Test
    fun sealHidesPlaintext() {
        val plain = "super-secret-token"
        assertFalse(SecretField.seal(plain, FakeBox).contains(plain))
    }

    @Test
    fun sealAndOpenRoundTrip() {
        val plain = "Bearer abc.def.ghi"
        assertEquals(plain, SecretField.open(SecretField.seal(plain, FakeBox), FakeBox))
    }

    @Test
    fun samePlaintextSealsDifferently() {
        val first = SecretField.seal("repeat", FakeBox)
        val second = SecretField.seal("repeat", FakeBox)
        assertNotEquals(first, second)
        assertEquals("repeat", SecretField.open(first, FakeBox))
        assertEquals("repeat", SecretField.open(second, FakeBox))
    }

    @Test
    fun emptyStringStaysEmpty() {
        assertEquals("", SecretField.seal("", FakeBox))
    }

    @Test
    fun plaintextPassesThroughUnchanged() {
        assertEquals("plain-value", SecretField.open("plain-value", FakeBox))
        assertEquals("plain-value", SecretField.open("plain-value", null))
    }

    @Test
    fun unicodeRoundTrip() {
        val plain = "密码：中文与符号 !@#$%^&*()"
        assertEquals(plain, SecretField.open(SecretField.seal(plain, FakeBox), FakeBox))
    }

    @Test
    fun longTextRoundTrip() {
        val plain = "x1y2".repeat(8_000)
        assertEquals(plain, SecretField.open(SecretField.seal(plain, FakeBox), FakeBox))
    }

    @Test
    fun sealedValueWithoutKeyBecomesEmpty() {
        val sealed = SecretField.seal("token", FakeBox)
        assertEquals("", SecretField.open(sealed, null))
    }

    @Test
    fun brokenKeyDegradesToEmpty() {
        val sealed = SecretField.seal("token", BrokenBox)
        assertEquals("", SecretField.open(sealed, BrokenBox))
    }

    @Test
    fun corruptedBase64DegradesToEmpty() {
        assertEquals("", SecretField.open("enc1:%%%not-base64%%%", FakeBox))
    }

    @Test
    fun truncatedCiphertextDegradesToEmpty() {
        assertEquals("", SecretField.open("enc1:", FakeBox))
    }

    @Test
    fun prefixIsOnlyRecognisedAtStart() {
        assertFalse(SecretField.isSealed("value enc1:abc"))
        assertTrue(SecretField.isSealed("enc1:abc"))
    }
}
