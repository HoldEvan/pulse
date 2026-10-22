package com.evan.pulse.domain.net

/**
 * 请求耗时分解：DNS / TCP / TLS / TTFB 与总耗时。
 * 各字段单位毫秒，缺失阶段（如直连无 DNS、http 无 TLS）为 0。
 * 事件时刻来自 OkHttp EventListener 回调，落在 OkHttp 分发线程，故用线程安全的计数器。
 */
data class HttpTimeline(
    val dnsMs: Long = 0,
    val tcpMs: Long = 0,
    val tlsMs: Long = 0,
    val ttfbMs: Long = 0,
    val totalMs: Long = 0
)

/** EventListener 回调的落点：线程安全地累积各阶段起始/结束时刻 */
internal class MutableTimeline {
    private val dnsStart = AtomicLong(-1)
    private val dnsEnd = AtomicLong(-1)
    private val connectStart = AtomicLong(-1)
    private val secureStart = AtomicLong(-1)
    private val secureEnd = AtomicLong(-1)
    private val connectEnd = AtomicLong(-1)
    private val headersStart = AtomicLong(-1)
    private val callStart = AtomicLong(-1)

    fun markCallStart(t: Long) = callStart.compareAndSet(-1, t)
    fun markDnsStart(t: Long) = dnsStart.compareAndSet(-1, t)
    fun markDnsEnd(t: Long) = dnsEnd.compareAndSet(-1, t)
    fun markConnectStart(t: Long) = connectStart.compareAndSet(-1, t)
    fun markSecureStart(t: Long) = secureStart.compareAndSet(-1, t)
    fun markSecureEnd(t: Long) = secureEnd.compareAndSet(-1, t)
    fun markConnectEnd(t: Long) = connectEnd.compareAndSet(-1, t)
    fun markHeadersStart(t: Long) = headersStart.compareAndSet(-1, t)

    fun snapshot(totalMs: Long): HttpTimeline {
        val ds = dnsStart.get()
        val de = dnsEnd.get()
        val cs = connectStart.get()
        val ss = secureStart.get()
        val se = secureEnd.get()
        val ce = connectEnd.get()
        val hs = headersStart.get()
        val cs0 = callStart.get()
        return HttpTimeline(
            dnsMs = if (ds >= 0 && de >= ds) de - ds else 0,
            tcpMs = if (cs >= 0 && ss >= cs) {
                ss - cs
            } else if (cs >= 0 && ce >= cs) {
                ce - cs
            } else 0,
            tlsMs = if (ss >= 0 && se >= ss) se - ss else 0,
            ttfbMs = if (cs0 >= 0 && hs >= cs0) hs - cs0 else 0,
            totalMs = totalMs
        )
    }

    private class AtomicLong(@Volatile private var value: Long) {
        fun get() = value
        fun compareAndSet(expected: Long, newValue: Long): Boolean {
            synchronized(this) {
                if (value == expected) {
                    value = newValue
                    return true
                }
                return false
            }
        }
    }
}