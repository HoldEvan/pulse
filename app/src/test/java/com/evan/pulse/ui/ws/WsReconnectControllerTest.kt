package com.evan.pulse.ui.ws

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WsReconnectControllerTest {
    @Test
    fun `retry delay doubles to maximum and reset starts at base`() {
        val backoff = WsRetryBackoff(3, 10)
        assertEquals(listOf(3, 6, 10, 10), List(4) { backoff.nextDelaySeconds() })
        backoff.reset()
        assertEquals(3, backoff.nextDelaySeconds())
    }

    @Test
    fun `failed attempts schedule again and success stops retries`() = runTest {
        val delays = mutableListOf<Long>()
        var attempts = 0
        val retry = WsReconnectController(
            scope = backgroundScope,
            attempt = { ++attempts == 3 },
            onScheduled = {},
            wait = { delays.add(it) }
        )

        retry.schedule(2, 5)
        runCurrent()

        assertEquals(listOf(2000L, 4000L, 5000L), delays)
        assertEquals(3, attempts)
    }

    @Test
    fun `explicit cancellation prevents pending attempt`() = runTest {
        var attempts = 0
        val retry = WsReconnectController(
            scope = backgroundScope,
            attempt = { attempts++; false },
            onScheduled = {}
        )

        retry.schedule(3, 60)
        runCurrent()
        retry.cancel()
        testScheduler.advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, attempts)
    }

    @Test
    fun `duplicate schedules keep one pending retry`() = runTest {
        var attempts = 0
        val retry = WsReconnectController(
            scope = backgroundScope,
            attempt = { attempts++; true },
            onScheduled = {}
        )

        retry.schedule(2, 10)
        runCurrent()
        retry.schedule(2, 10)
        testScheduler.advanceTimeBy(2000L)
        runCurrent()

        assertEquals(1, attempts)
    }

    @Test
    fun `new retry cycle after success starts at base delay`() = runTest {
        val delays = mutableListOf<Long>()
        var attempts = 0
        val retry = WsReconnectController(
            scope = backgroundScope,
            attempt = { ++attempts >= 2 },
            onScheduled = {},
            wait = { delays.add(it) }
        )

        retry.schedule(2, 10)
        runCurrent()
        retry.schedule(2, 10)
        runCurrent()

        assertEquals(listOf(2000L, 4000L, 2000L), delays)
        assertEquals(3, attempts)
    }
}
