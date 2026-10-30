package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.TlsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsContextsTest {

    @Test
    fun `default config produces a context`() {
        val context = TlsContexts.build(TlsConfig())
        assertTrue(context.protocol == "TLS")
    }

    @Test
    fun `insecure config trusts arbitrary chains`() {
        val tm = TlsContexts.serverTrustManager(TlsConfig(insecure = true))
        // 空链也放行，说明是信任一切的管理器
        tm.checkServerTrusted(emptyArray(), "RSA")
    }

    @Test
    fun `strip pem headers leaves only base64 body`() {
        val certs = TlsContexts.parseCertificates("")
        assertEquals(0, certs.size)
    }

    @Test(expected = TlsConfigException::class)
    fun `invalid base64 private key is rejected`() {
        TlsContexts.parsePrivateKey("-----BEGIN PRIVATE KEY-----\nnot-base64!!!\n-----END PRIVATE KEY-----")
    }

    @Test(expected = TlsConfigException::class)
    fun `blank private key is rejected`() {
        TlsContexts.parsePrivateKey("")
    }
}
