package io.github.magisk317.relay.feature.call

import kotlinx.coroutines.delay

internal object CallEndResolutionPolicy {
    /** Delay before each CallLog attempt; the first lookup remains immediate. */
    val attemptDelaysMs = listOf(0L, 250L, 500L, 1_000L, 2_000L)

    enum class NumberSource {
        Direct,
        RecentIngress,
        CallLog,
        Unavailable,
    }

    data class Decision(
        val number: String?,
        val source: NumberSource,
        val shouldRetry: Boolean,
    )

    fun shouldQueryCallLog(
        directNumber: String?,
        recentIngressNumber: String?,
        retryAllowed: Boolean,
    ): Boolean {
        return retryAllowed &&
            directNumber.isNullOrBlank() &&
            recentIngressNumber.isNullOrBlank()
    }

    fun decide(
        directNumber: String?,
        recentIngressNumber: String?,
        callLogNumber: String?,
        completedAttemptIndex: Int,
        retryAllowed: Boolean,
    ): Decision {
        val direct = directNumber?.ifBlank { null }
        val recent = recentIngressNumber?.ifBlank { null }
        val callLog = callLogNumber?.ifBlank { null }
        val (number, source) = when {
            direct != null -> direct to NumberSource.Direct
            recent != null -> recent to NumberSource.RecentIngress
            callLog != null -> callLog to NumberSource.CallLog
            else -> null to NumberSource.Unavailable
        }
        val shouldRetry = number == null &&
            direct == null &&
            recent == null &&
            retryAllowed &&
            completedAttemptIndex < attemptDelaysMs.lastIndex
        return Decision(number = number, source = source, shouldRetry = shouldRetry)
    }
}

internal object CallEndResolver {
    data class Resolution(
        val number: String?,
        val callType: Int,
        val source: CallEndResolutionPolicy.NumberSource,
        val attemptCount: Int,
    )

    suspend fun resolve(
        directNumber: String?,
        expectedCallType: Int,
        retryAllowed: Boolean,
        recentNumberProvider: () -> String?,
        callLogProvider: () -> CallLogQueryHelper.RecentCall?,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): Resolution {
        var latestCallLogRow: CallLogQueryHelper.RecentCall? = null
        CallEndResolutionPolicy.attemptDelaysMs.forEachIndexed { attemptIndex, delayMs ->
            if (delayMs > 0L) pause(delayMs)

            val recentNumber = recentNumberProvider()
            if (
                CallEndResolutionPolicy.shouldQueryCallLog(
                    directNumber = directNumber,
                    recentIngressNumber = recentNumber,
                    retryAllowed = retryAllowed,
                )
            ) {
                latestCallLogRow = callLogProvider() ?: latestCallLogRow
            }
            val decision = CallEndResolutionPolicy.decide(
                directNumber = directNumber,
                recentIngressNumber = recentNumber,
                callLogNumber = latestCallLogRow?.number,
                completedAttemptIndex = attemptIndex,
                retryAllowed = retryAllowed,
            )
            if (!decision.shouldRetry) {
                return Resolution(
                    number = decision.number,
                    callType = latestCallLogRow?.callType ?: expectedCallType,
                    source = decision.source,
                    attemptCount = attemptIndex + 1,
                )
            }
        }
        error("Call-end resolution exhausted without a terminal decision")
    }
}
