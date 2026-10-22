package com.evan.pulse.ui.ws

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class WsRetryBackoff(baseSeconds: Int, maxSeconds: Int) {
    private val base = baseSeconds.coerceAtLeast(1)
    private val max = maxSeconds.coerceAtLeast(base)
    private var next = base

    fun nextDelaySeconds(): Int {
        val current = next
        next = (next.toLong() * 2).coerceAtMost(max.toLong()).toInt()
        return current
    }

    fun reset() { next = base }
}

internal class WsReconnectController(
    private val scope: CoroutineScope,
    private val attempt: suspend () -> Boolean,
    private val onScheduled: (Int) -> Unit,
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    private var job: Job? = null

    fun schedule(baseSeconds: Int, maxSeconds: Int) {
        if (job?.isActive == true) return
        job = scope.launch {
            val backoff = WsRetryBackoff(baseSeconds, maxSeconds)
            while (isActive) {
                val seconds = backoff.nextDelaySeconds()
                onScheduled(seconds)
                wait(seconds * 1000L)
                try {
                    if (attempt()) {
                        backoff.reset()
                        break
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
