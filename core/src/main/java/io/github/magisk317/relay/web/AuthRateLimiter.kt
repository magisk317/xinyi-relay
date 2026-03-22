package io.github.magisk317.relay.web

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AuthRateLimiter(
    private val failureWindowMillis: Long = TimeUnit.MINUTES.toMillis(5),
    private val blockDurationMillis: Long = TimeUnit.MINUTES.toMillis(10),
    private val maxFailures: Int = 8,
) {

    private data class RateState(
        val failures: ArrayDeque<Long> = ArrayDeque(),
        var blockedUntil: Long = 0L,
    )

    private val states = ConcurrentHashMap<String, RateState>()

    fun isBlocked(key: String): Boolean {
        val now = System.currentTimeMillis()
        val state = states[key] ?: return false
        synchronized(state) {
            pruneFailures(state, now)
            if (state.blockedUntil <= now) {
                state.blockedUntil = 0L
                return false
            }
            return true
        }
    }

    fun recordFailure(key: String) {
        val now = System.currentTimeMillis()
        val state = states.computeIfAbsent(key) { RateState() }
        synchronized(state) {
            pruneFailures(state, now)
            state.failures.addLast(now)
            if (state.failures.size >= maxFailures) {
                state.blockedUntil = now + blockDurationMillis
            }
        }
    }

    fun recordSuccess(key: String) {
        val state = states[key] ?: return
        synchronized(state) {
            state.failures.clear()
            state.blockedUntil = 0L
        }
    }

    private fun pruneFailures(state: RateState, now: Long) {
        while (state.failures.isNotEmpty() && now - state.failures.first() > failureWindowMillis) {
            state.failures.removeFirst()
        }
    }
}
