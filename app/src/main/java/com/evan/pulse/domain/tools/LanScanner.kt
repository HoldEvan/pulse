package com.evan.pulse.domain.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.NetworkInterface
import java.util.Collections
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 扫描主机地址总数上限：更大的网段只探测前 [LAN_SCAN_MAX_HOSTS] 个地址 */
const val LAN_SCAN_MAX_HOSTS = 512

/** 扫描并发上限 */
const val LAN_SCAN_CONCURRENCY = 64

/** 单个在线主机结果 */
data class LanHost(
    val ip: String,
    val hostname: String?,
    val latencyMs: Long,
    val probe: String
)

/** 检测到的网段与待探测主机地址 */
data class LanNetworkInfo(
    val ip: String,
    val prefixLength: Int,
    val hosts: List<InetAddress>
)

/** 探测抽象：返回存活主机的信息，或 null 表示不可达 */
fun interface HostProbe {
    suspend fun probe(host: InetAddress): LanHost?
}

/** 把 IPv4 点分十进制转成无符号长整型，用于数值排序 */
fun ipv4ToLong(ip: String): Long =
    ip.split('.').fold(0L) { acc, octet -> (acc shl 8) or (octet.toLong() and 0xFF) }

private fun inetToLong(addr: InetAddress): Long {
    val raw = addr.address
    return ((raw[0].toLong() and 0xFF) shl 24) or
        ((raw[1].toLong() and 0xFF) shl 16) or
        ((raw[2].toLong() and 0xFF) shl 8) or
        (raw[3].toLong() and 0xFF)
}

private fun longToInet(value: Long): InetAddress = InetAddress.getByAddress(
    byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
)

/**
 * 展开网段内全部主机地址。
 * 前缀 < 31 时排除网络地址与广播地址；前缀 >= 31 时返回块内全部地址。
 * 数量超过 [maxHosts] 时只取数值最小的前 [maxHosts] 个，保持升序。
 */
fun lanHostAddresses(address: InetAddress, prefixLength: Int, maxHosts: Int = LAN_SCAN_MAX_HOSTS): List<InetAddress> {
    val base = inetToLong(address)
    val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
    val network = base and mask
    val broadcast = network or (0xFFFFFFFFL xor mask)
    val first = if (prefixLength < 31) network + 1 else network
    val last = if (prefixLength < 31) broadcast - 1 else broadcast
    if (first > last) return emptyList()
    return (first..last).take(maxHosts).map { longToInet(it) }
}

/**
 * 有界并发扫描主机列表。
 * 每个主机在独立的 IO 协程里探测，Semaphore 限制并发数；取消父协程即取消全部探测。
 * [onHost] 在发现存活主机时回调，[onProgress] 在每个地址完成后回调（scanned 单调递增）。
 */
suspend fun lanScan(
    hosts: List<InetAddress>,
    probe: HostProbe,
    concurrency: Int = LAN_SCAN_CONCURRENCY,
    context: CoroutineContext = Dispatchers.IO,
    onHost: (LanHost) -> Unit = { _ -> },
    onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
) {
    val semaphore = Semaphore(concurrency)
    val counter = java.util.concurrent.atomic.AtomicInteger(0)
    coroutineScope {
        hosts.forEach { host ->
            launch(context) {
                semaphore.withPermit {
                    val result = runCatching { probe.probe(host) }.getOrNull()
                    val done = counter.incrementAndGet()
                    if (result != null) onHost(result)
                    onProgress(done, hosts.size)
                }
            }
        }
    }
}

/** 默认探针：ICMP 优先，回退到常用 TCP 端口连接探测，主机名尽力解析 */
class DefaultHostProbe(
    private val icmpTimeoutMs: Int = 300,
    private val tcpPorts: List<Int> = listOf(80, 443, 8080, 22, 1883, 5555),
    private val tcpTimeoutMs: Int = 250
) : HostProbe {

    override suspend fun probe(host: InetAddress): LanHost? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val icmp = runCatching { host.isReachable(icmpTimeoutMs) }.getOrDefault(false)
        val probeName = if (icmp) "ICMP" else tcpProbeName(host)
        if (probeName == null) return@withContext null
        val hostname = runCatching { host.hostName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != host.hostAddress }
        LanHost(host.hostAddress, hostname, System.currentTimeMillis() - start, probeName)
    }

    private suspend fun tcpProbeName(host: InetAddress): String? = coroutineScope {
        tcpPorts.map { port ->
            async(Dispatchers.IO) {
                runCatching {
                    Socket().use { it.connect(InetSocketAddress(host, port), tcpTimeoutMs) }
                    port
                }.getOrNull()
            }
        }.awaitAll().firstOrNull()?.let { "TCP $it" }
    }
}

/** 检测设备当前 IPv4 局域网网段；无可用局域网连接时返回 null */
object LanNetwork {
    fun detect(context: Context): LanNetworkInfo? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val links = cm?.allNetworks.orEmpty().filter { network ->
            val caps = cm?.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }.flatMap { cm?.getLinkProperties(it)?.linkAddresses.orEmpty() }
        val connected = links.asSequence()
            .filter { it.address is Inet4Address }
            .map { it.address as Inet4Address to it.prefixLength }
            .filter { (addr, _) -> !addr.isLoopbackAddress && !isLinkLocal(addr) }
            .map { (addr, prefix) ->
                LanNetworkInfo(addr.hostAddress.orEmpty(), prefix, lanHostAddresses(addr, prefix))
            }
            .firstOrNull()
        if (connected != null) return connected

        // 热点共享接口通常不作为 ConnectivityManager 的客户端网络暴露。
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
                .filter { iface ->
                    listOf("wlan", "ap", "swlan", "wifi", "eth", "softap")
                        .any { iface.name.startsWith(it) }
                }
                .flatMap { it.interfaceAddresses.asSequence() }
                .filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
                .filter { it.networkPrefixLength.toInt() in 1..32 }
                .map { address ->
                    LanNetworkInfo(
                        address.address.hostAddress.orEmpty(),
                        address.networkPrefixLength.toInt(),
                        lanHostAddresses(address.address, address.networkPrefixLength.toInt())
                    )
                }.firstOrNull()
        }.getOrNull()
    }

    private fun isLinkLocal(addr: Inet4Address): Boolean =
        addr.hostAddress?.startsWith("169.254.") == true
}
