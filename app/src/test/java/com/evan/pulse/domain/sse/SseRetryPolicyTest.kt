package com.evan.pulse.domain.sse

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class SseRetryPolicyTest {

    @Test
    fun `exponential backoff doubles each attempt`() {
        val delays = (1..4).map { attempt ->
            SseRetryPolicy.delaySeconds(
                baseSec = 2,
                attempt = attempt,
                exponential = true,
                maxSec = 0,
                jitter = false
            )
        }
        assertEquals(listOf(2, 4, 8, 16), delays)
    }

    @Test
    fun `fixed delay ignores attempt when backoff disabled`() {
        val delays = (1..3).map { attempt ->
            SseRetryPolicy.delaySeconds(3, attempt, exponential = false, maxSec = 0, jitter = false)
        }
        assertEquals(listOf(3, 3, 3), delays)
    }

    @Test
    fun `backoff is capped by maxSec`() {
        val delay = SseRetryPolicy.delaySeconds(
            baseSec = 5,
            attempt = 10,
            exponential = true,
            maxSec = 20,
            jitter = false
        )
        assertEquals(20, delay)
    }

    @Test
    fun `invalid base coerces to one second`() {
        assertEquals(
            1,
            SseRetryPolicy.delaySeconds(0, 1, exponential = false, maxSec = 0, jitter = false)
        )
    }

    @Test
    fun `jitter stays within twenty-five percent`() {
        // 用固定种子保证可复现；结果必须落在 [base*0.75, base*1.25]
        val random = Random(42)
        repeat(100) {
            val delay = SseRetryPolicy.delaySeconds(
                baseSec = 12,
                attempt = 1,
                exponential = false,
                maxSec = 0,
                jitter = true,
                random = random
            )
            assert(delay in 9..15) { "jitter out of range: $delay" }
        }
    }

    @Test
    fun `jitter never returns zero`() {
        repeat(50) {
            val delay = SseRetryPolicy.delaySeconds(
                baseSec = 1,
                attempt = 1,
                exponential = false,
                maxSec = 0,
                jitter = true
            )
            assert(delay >= 1) { "delay must be >= 1, was $delay" }
        }
    }
}
