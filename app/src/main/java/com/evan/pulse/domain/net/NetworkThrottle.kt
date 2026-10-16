package com.evan.pulse.domain.net

import kotlin.random.Random

/**
 * 弱网模拟档位：给出上下行速率与往返时延区间，由引擎按请求采样。
 * NONE 表示不模拟；FORCE_TIMEOUT 表示阻塞指定秒数后按超时失败返回。
 */
enum class NetworkPreset(
    val downKbps: Long,
    val upKbps: Long,
    val minRttMs: Long,
    val maxRttMs: Long
) {
    NONE(0, 0, 0, 0),
    FIVE_G(50_000, 20_000, 20, 40),
    FOUR_G(20_000, 10_000, 50, 100),
    THREE_G(2_000, 1_000, 200, 400),
    TWO_G(100, 50, 600, 1_200),
    FORCE_TIMEOUT(0, 0, 0, 0)
}

/** 网络模拟配置：档位即开关，强制超时档需要秒数大于 0 才生效 */
data class NetworkThrottleConfig(
    val preset: NetworkPreset = NetworkPreset.NONE,
    val forceTimeoutSec: Int = 0
) {
    val isActive: Boolean
        get() = when (preset) {
            NetworkPreset.NONE -> false
            NetworkPreset.FORCE_TIMEOUT -> forceTimeoutSec > 0
            else -> true
        }
}

/**
 * 弱网采样：请求前按档位随机往返时延 + 请求体按上行速率估算传输耗时，
 * 响应后按响应体大小与下行速率估算下载耗时；每段封顶 [MAX_TRANSFER_DELAY_MS]，
 * 避免大报文把请求卡死几分钟。强制超时档由引擎直接阻塞，不走采样。
 */
object NetworkThrottle {

    const val MAX_TRANSFER_DELAY_MS = 15_000L

    /** 请求前：随机 RTT + 请求体按上行速率的传输耗时 */
    fun uploadDelayMs(bodyBytes: Long, config: NetworkThrottleConfig, random: Random = Random.Default): Long {
        if (!config.isActive || config.preset == NetworkPreset.FORCE_TIMEOUT) return 0L
        val preset = config.preset
        val rtt = random.nextLong(preset.minRttMs, preset.maxRttMs + 1)
        return (rtt + transferDelayMs(bodyBytes, preset.upKbps))
            .coerceAtMost(MAX_TRANSFER_DELAY_MS)
    }

    /** 响应后：响应体按下行速率的下载耗时 */
    fun downloadDelayMs(responseBytes: Long, config: NetworkThrottleConfig): Long {
        if (!config.isActive || config.preset == NetworkPreset.FORCE_TIMEOUT) return 0L
        return transferDelayMs(responseBytes, config.preset.downKbps)
    }

    /** 传输 bytes 字节在 kbps 速率下需要的毫秒数（bytes * 8 / kbps） */
    fun transferDelayMs(bytes: Long, kbps: Long): Long =
        if (bytes <= 0 || kbps <= 0) 0L else (bytes * 8L / kbps).coerceAtMost(MAX_TRANSFER_DELAY_MS)
}
