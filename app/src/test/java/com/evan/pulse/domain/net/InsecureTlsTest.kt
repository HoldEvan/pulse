package com.evan.pulse.domain.net

import java.security.cert.X509Certificate
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import com.evan.pulse.domain.net.InsecureTls.applyInsecureTls

class InsecureTlsTest {

    @Test
    fun `extension never replaces the builder, only decorates it`() {
        val builder = OkHttpClient.Builder()
        assertSame(builder, builder.applyInsecureTls(false))
        assertSame(builder, builder.applyInsecureTls(true))
    }

    @Test
    fun `enabled accepts any hostname`() {
        val client = OkHttpClient.Builder().applyInsecureTls(true).build()
        assertEquals(true, client.hostnameVerifier.verify("example.com", null))
        assertEquals(true, client.hostnameVerifier.verify("192.168.1.10", null))
    }

    @Test
    fun `trust manager accepts arbitrary chains`() {
        val chain = arrayOf<X509Certificate>()
        InsecureTls.trustManager.checkServerTrusted(chain, "RSA")
        InsecureTls.trustManager.checkClientTrusted(chain, "RSA")
        assertEquals(0, InsecureTls.trustManager.acceptedIssuers.size)
    }
}
