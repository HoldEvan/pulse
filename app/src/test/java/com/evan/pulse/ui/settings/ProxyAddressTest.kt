package com.evan.pulse.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyAddressTest {

    @Test
    fun `splits host and port`() {
        assertEquals("1.2.3.4" to "8080", splitProxyAddress("1.2.3.4:8080"))
        assertEquals("example.com" to "1080", splitProxyAddress("example.com:1080"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("1.2.3.4" to "8080", splitProxyAddress("  1.2.3.4:8080  "))
    }

    @Test
    fun `keeps host only when no port`() {
        assertEquals("example.com" to "", splitProxyAddress("example.com"))
        assertEquals("example.com" to "", splitProxyAddress("example.com:"))
        assertEquals("" to "", splitProxyAddress(""))
    }

    @Test
    fun `treats non numeric suffix as part of host`() {
        assertEquals("example.com:abc" to "", splitProxyAddress("example.com:abc"))
    }

    @Test
    fun `supports bracketed ipv6 with port`() {
        assertEquals("::1" to "8080", splitProxyAddress("[::1]:8080"))
        assertEquals("::1" to "", splitProxyAddress("[::1]"))
    }

    @Test
    fun `does not split bare ipv6`() {
        assertEquals("::1" to "", splitProxyAddress("::1"))
    }
}
