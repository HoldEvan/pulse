package com.evan.pulse.domain.sse

import kotlin.random.Random

/**
 * SSE 重连等待时长计算（纯函数，便于单元测试）。
 *
 * - 基础间隔 [baseSec] 起算；开启指数退避后第 n 次重连等待 `base * 2^(n-1)`，
 *   并按 [maxSec] 封顶，避免无限增长。
 * - 开启抖动时在上一步结果上叠加 ±25% 随机量，削弱多个客户端同时重连的惊群。
 *
 * 输入非法（baseSec 不大于 0）时统一按 1 秒处理，保证退避不会退化成 0。
 */
object SseRetryPolicy {

    /** 抖动幅度：结果落在 [base*0.75, base*1.25] */
    private const val JITTER_RATIO = 0.25
    private const val MIN_DELAY_SEC = 1

    /**
     * 计算第 [attempt] 次重连（从 1 开始）的等待秒数。
     *
     * @param baseSec 首次重连的基础间隔
     * @param attempt 连续重连序号，1 表示第一次
     * @param exponential 是否指数退避
     * @param maxSec 等待上限；不大于 0 视为不封顶
     * @param jitter 是否叠加随机抖动
     * @param random 注入随机源，测试时传固定序列以断言精确值
     */
    fun delaySeconds(
        baseSec: Int,
        attempt: Int,
        exponential: Boolean,
        maxSec: Int,
        jitter: Boolean,
        random: Random = Random.Default
    ): Int {
        val base = baseSec.coerceAtLeast(MIN_DELAY_SEC)
        val step = attempt.coerceAtLeast(1)
        // 指数退避用移位，但限制最高 20 次方防止溢出成天文数字
        val raw = if (exponential) {
            val factor = 1L shl (step - 1).coerceIn(0, 20)
            (base.toLong() * factor).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            base
        }
        val capped = if (maxSec > 0) raw.coerceAtMost(maxSec) else raw
        if (!jitter) return capped
        val delta = (capped * JITTER_RATIO).toInt()
        if (delta <= 0) return capped
        val offset = random.nextInt(-delta, delta + 1)
        return (capped + offset).coerceAtLeast(MIN_DELAY_SEC)
    }
}
