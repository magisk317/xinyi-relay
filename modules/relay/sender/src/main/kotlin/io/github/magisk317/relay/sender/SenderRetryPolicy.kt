package io.github.magisk317.relay.sender

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Retry and circuit-breaker utilities for sender dispatch.
 *
 * - Retries transient failures (IOException, timeout) up to [MAX_RETRIES] times
 *   with exponential backoff.
 * - Circuit breaker opens after [CIRCUIT_THRESHOLD] consecutive failures,
 *   blocking dispatches for [CIRCUIT_COOLDOWN_MS] before half-opening.
 */
object SenderRetryPolicy {
    const val MAX_RETRIES = 2
    private const val INITIAL_BACKOFF_MS = 300L
    private const val CIRCUIT_THRESHOLD = 5
    private const val CIRCUIT_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes

    private val circuitState = ConcurrentHashMap<String, CircuitState>()

    private data class CircuitState(
        val failures: AtomicInteger = AtomicInteger(0),
        @Volatile var openedAt: Long = 0L,
    )

    fun isCircuitOpen(senderType: String): Boolean {
        val state = circuitState[senderType] ?: return false
        if (state.failures.get() < CIRCUIT_THRESHOLD) return false
        val elapsed = System.currentTimeMillis() - state.openedAt
        if (elapsed > CIRCUIT_COOLDOWN_MS) {
            // Half-open: allow one attempt
            state.failures.set(0)
            return false
        }
        return true
    }

    fun recordSuccess(senderType: String) {
        circuitState[senderType]?.failures?.set(0)
    }

    fun recordFailure(senderType: String) {
        val state = circuitState.getOrPut(senderType) { CircuitState() }
        val count = state.failures.incrementAndGet()
        if (count >= CIRCUIT_THRESHOLD) {
            state.openedAt = System.currentTimeMillis()
        }
    }

    fun isTransient(e: Exception): Boolean {
        return e is IOException
            || e is SocketTimeoutException
            || e is UnknownHostException
            || (e.message?.contains("timeout", ignoreCase = true) == true)
            || (e.message?.contains("connect", ignoreCase = true) == true)
    }

    suspend fun <T> withRetry(
        senderType: String,
        block: suspend () -> T,
    ): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val delay = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
                kotlinx.coroutines.delay(delay)
            }
            try {
                val result = block()
                recordSuccess(senderType)
                return result
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                lastException = e
                if (!isTransient(e) || attempt == MAX_RETRIES) {
                    recordFailure(senderType)
                    throw e
                }
            }
        }
        recordFailure(senderType)
        throw lastException ?: IllegalStateException("retry failed")
    }
}
