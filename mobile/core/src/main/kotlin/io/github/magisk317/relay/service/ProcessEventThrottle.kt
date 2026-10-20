package io.github.magisk317.relay.service

import java.util.concurrent.atomic.AtomicLong

internal enum class RecoveryAdmission {
    TOKEN_REJECTED,
    THROTTLED,
    ADMITTED,
}

internal fun recoveryAdmission(tokenAccepted: Boolean, throttle: ProcessEventThrottle): RecoveryAdmission = when {
    !tokenAccepted -> RecoveryAdmission.TOKEN_REJECTED
    !throttle.tryAcquire() -> RecoveryAdmission.THROTTLED
    else -> RecoveryAdmission.ADMITTED
}

internal class ProcessEventThrottle(
    private val minIntervalMillis: Long,
    private val elapsedRealtime: () -> Long,
) {
    private val lastAcceptedAt = AtomicLong(NO_EVENT)

    init {
        require(minIntervalMillis > 0L)
    }

    fun tryAcquire(): Boolean {
        val now = elapsedRealtime()
        while (true) {
            val previous = lastAcceptedAt.get()
            if (previous != NO_EVENT && (now < previous || now - previous < minIntervalMillis)) {
                return false
            }
            if (lastAcceptedAt.compareAndSet(previous, now)) return true
        }
    }

    private companion object {
        const val NO_EVENT = Long.MIN_VALUE
    }
}
