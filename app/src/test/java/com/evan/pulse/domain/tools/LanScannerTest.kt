package com.evan.pulse.domain.tools

import java.net.InetAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanScannerTest {

    private fun inet(ip: String): InetAddress = InetAddress.getByName(ip)

    // ---- lanHostAddresses（需求 1.3）----

    @Test
    fun `a24 range yields 254 hosts excluding network and broadcast`() {
        val hosts = lanHostAddresses(inet("192.168.1.10"), 24)
        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", hosts.first().hostAddress)
        assertEquals("192.168.1.254", hosts.last().hostAddress)
        assertTrue(hosts.none { it.hostAddress == "192.168.1.0" || it.hostAddress == "192.168.1.255" })
    }

    @Test
    fun `a30 range excludes network and broadcast`() {
        val hosts = lanHostAddresses(inet("192.168.1.5"), 30)
        assertEquals(listOf("192.168.1.5", "192.168.1.6"), hosts.map { it.hostAddress })
    }

    @Test
    fun `a31 range keeps both addresses usable`() {
        val hosts = lanHostAddresses(inet("192.168.1.4"), 31)
        assertEquals(listOf("192.168.1.4", "192.168.1.5"), hosts.map { it.hostAddress })
    }

    @Test
    fun `a32 range keeps the single address`() {
        val hosts = lanHostAddresses(inet("192.168.1.7"), 32)
        assertEquals(listOf("192.168.1.7"), hosts.map { it.hostAddress })
    }

    @Test
    fun `large range is truncated to the lowest maxHosts addresses`() {
        val hosts = lanHostAddresses(inet("10.0.0.3"), 16, maxHosts = 512)
        assertEquals(512, hosts.size)
        assertEquals("10.0.0.1", hosts.first().hostAddress)
        assertEquals("10.0.2.0", hosts.last().hostAddress)
    }

    // ---- ipv4ToLong（需求 3.1）----

    @Test
    fun `ipv4ToLong maps dotted quad to numeric value`() {
        assertEquals(0xC0A80101L, ipv4ToLong("192.168.1.1"))
        assertEquals(0xC0A801FEL, ipv4ToLong("192.168.1.254"))
        assertEquals(0L, ipv4ToLong("0.0.0.0"))
    }

    @Test
    fun `ipv4ToLong orders numeric ascending`() {
        val ips = listOf("192.168.1.10", "192.168.1.2", "192.168.1.1")
        assertEquals(
            listOf("192.168.1.1", "192.168.1.2", "192.168.1.10"),
            ips.sortedBy { ipv4ToLong(it) }
        )
    }

    // ---- lanScan（需求 2.1、2.3、2.4）----

    @Test
    fun `scan collects alive hosts and reports progress`() = runTest {
        val targets = listOf(inet("192.168.1.1"), inet("192.168.1.2"), inet("192.168.1.3"))
        val probe = HostProbe { host ->
            if (host.hostAddress == "192.168.1.2") {
                LanHost(host.hostAddress, "host2", 12L, "ICMP")
            } else {
                null
            }
        }
        val found = mutableListOf<LanHost>()
        val scanned = mutableListOf<Pair<Int, Int>>()

        lanScan(
            targets,
            probe,
            concurrency = 3,
            context = UnconfinedTestDispatcher(),
            onHost = { found += it },
            onProgress = { s, t -> scanned += s to t }
        )

        assertEquals(1, found.size)
        assertEquals("192.168.1.2", found.single().ip)
        assertEquals("host2", found.single().hostname)
        assertEquals(12L, found.single().latencyMs)
        assertEquals("ICMP", found.single().probe)
        assertEquals(3, scanned.size)
        assertEquals(3, scanned.last().first)
        assertEquals(3, scanned.last().second)
    }

    @Test
    fun `scan reports tcp probe name for alive host`() = runTest {
        val targets = listOf(inet("192.168.1.9"))
        val found = mutableListOf<LanHost>()
        lanScan(
            targets,
            HostProbe { LanHost(it.hostAddress, null, 33L, "TCP 80") },
            context = UnconfinedTestDispatcher(),
            onHost = { found += it }
        )
        assertEquals("TCP 80", found.single().probe)
    }

    @Test
    fun `scan skips hosts whose probe throws`() = runTest {
        val targets = listOf(inet("192.168.1.1"), inet("192.168.1.2"))
        val found = mutableListOf<LanHost>()
        lanScan(
            targets,
            HostProbe { throw RuntimeException("unreachable") },
            onHost = { found += it }
        )
        assertTrue(found.isEmpty())
    }
}