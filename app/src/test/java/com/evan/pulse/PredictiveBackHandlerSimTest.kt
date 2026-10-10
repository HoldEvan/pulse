package com.evan.pulse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replicates the exact coroutine structure of
 * androidx.activity.compose.ComposePredictiveBackHandler (activity-compose 1.13.0, decompiled)
 * against the lambda shape used by MiuixPredictiveBack, to determine whether the
 * "You must collect the progress flow" IllegalStateException can fire when the collector
 * swallows the CancellationException.
 */
class PredictiveBackHandlerSimTest {

    private class BooleanRef(var element: Boolean = false)

    // Decompiled launchNewGesture: launch { if (isBackEnabled) { collected ref;
    // currentOnBack(channel.consumeAsFlow().onCompletion { collected = true });
    // if (!collected) throw IllegalStateException("You must collect the progress flow") } }
    private fun launchGesture(
        scope: CoroutineScope,
        channel: Channel<Int>,
        isBackEnabled: Boolean,
        onBack: suspend (Flow<Int>) -> Unit,
    ): Job = scope.launch {
        if (isBackEnabled) {
            val collected = BooleanRef(false)
            onBack(channel.consumeAsFlow().onCompletion { collected.element = true })
            if (!collected.element) {
                throw IllegalStateException("You must collect the progress flow")
            }
        }
    }

    // Current MiuixPredictiveBack lambda: swallows CancellationException, no rethrow.
    private val swallowingLambda: suspend (Flow<Int>) -> Unit = { flow ->
        try {
            flow.collect { }
        } catch (e: CancellationException) {
            // reset state; deliberately not rethrown (current code)
        }
    }

    // Official docs lambda: rethrows CancellationException.
    private val rethrowingLambda: suspend (Flow<Int>) -> Unit = { flow ->
        try {
            flow.collect { }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun scopeWithErrors(errors: MutableList<Throwable>): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, e -> errors.add(e) }
        return CoroutineScope(handler + Job())
    }

    @Test
    fun `button press - zero event flow - no crash`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = scopeWithErrors(errors)
        val channel = Channel<Int>(Channel.BUFFERED)
        val job = launchGesture(scope, channel, isBackEnabled = true, onBack = swallowingLambda)
        channel.close()
        job.join()
        assertEquals("unexpected errors: $errors", 0, errors.size)
    }

    @Test
    fun `gesture commit - flow completes after close - no crash`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = scopeWithErrors(errors)
        val seen = mutableListOf<Int>()
        val channel = Channel<Int>(Channel.BUFFERED)
        val job = launchGesture(scope, channel, isBackEnabled = true, onBack = { flow ->
            try {
                flow.collect { seen.add(it) }
            } catch (e: CancellationException) {
                throw e
            }
        })
        channel.send(1)
        channel.send(2)
        channel.close()
        job.join()
        assertEquals(listOf(1, 2), seen)
        assertEquals("unexpected errors: $errors", 0, errors.size)
    }

    @Test
    fun `probe - onCompletion state after cancel with swallowing lambda`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = scopeWithErrors(errors)
        var completionCause: Throwable? = null
        var collectRan = false
        val channel = Channel<Int>(Channel.BUFFERED)
        val job = scope.launch {
            var collected = false
            try {
                channel.consumeAsFlow()
                    .onCompletion { collected = true; completionCause = it }
                    .collect { collectRan = true }
            } catch (e: CancellationException) {
                // swallowed
            }
            println("PROBE: collected=$collected")
            if (!collected) throw IllegalStateException("You must collect the progress flow")
        }
        channel.send(1)
        yield()
        // Decompiled onBackCancelled order: channel.cancel then job.cancel
        channel.cancel(CancellationException("onBack cancelled"))
        job.cancel()
        job.join()
        println("PROBE RESULT: collectRan=$collectRan completionCause=$completionCause errors=$errors")
        errors.forEach { println("PROBE ERROR -> ${it::class.java.name}: ${it.message}") }
    }

    @Test
    fun `gesture cancel - swallowing lambda - observe crash behavior`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = scopeWithErrors(errors)
        val channel = Channel<Int>(Channel.BUFFERED)
        val job = launchGesture(scope, channel, isBackEnabled = true, onBack = swallowingLambda)
        channel.send(1)
        yield()
        channel.cancel(CancellationException("onBack cancelled"))
        job.cancel()
        job.join()
        println("CANCEL SWALLOW: errors=$errors")
        errors.forEach { println("CANCEL SWALLOW ERROR -> ${it::class.java.name}: ${it.message}") }
    }

    @Test
    fun `gesture cancel - rethrowing lambda - observe crash behavior`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = scopeWithErrors(errors)
        val channel = Channel<Int>(Channel.BUFFERED)
        val job = launchGesture(scope, channel, isBackEnabled = true, onBack = rethrowingLambda)
        channel.send(1)
        yield()
        channel.cancel(CancellationException("onBack cancelled"))
        job.cancel()
        job.join()
        println("CANCEL RETHROW: errors=$errors")
        errors.forEach { println("CANCEL RETHROW ERROR -> ${it::class.java.name}: ${it.message}") }
    }

    @Test
    fun `cancel race - job cancel before collector processes channel cancel`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val scope = scopeWithErrors(errors)
        val channel = Channel<Int>(Channel.BUFFERED)
        val job = launchGesture(scope, channel, isBackEnabled = true, onBack = swallowingLambda)
        // No yield: cancel while collector may still be suspended at the first receive
        channel.cancel(CancellationException("onBack cancelled"))
        job.cancel()
        job.join()
        println("RACE: errors=$errors")
        errors.forEach { println("RACE ERROR -> ${it::class.java.name}: ${it.message}") }
    }
}
