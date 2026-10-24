package io.github.magisk317.relay.feature.call

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CallLogQueryHelperTest {

    @Test
    fun `bestMatch prefers matching call direction closest to session start`() {
        val rows = listOf(
            CallLogQueryHelper.RecentCall(number = "10010", callType = 1, startedAt = 90_000L),
            CallLogQueryHelper.RecentCall(number = "10086", callType = 2, startedAt = 101_000L),
            CallLogQueryHelper.RecentCall(number = "10000", callType = 1, startedAt = 100_500L),
        )

        assertEquals(
            "10000",
            CallLogQueryHelper.bestMatch(
                rows = rows,
                expectedCallType = 1,
                sessionStartedAt = 100_000L,
            )?.number,
        )
    }

    @Test
    fun `bestMatch falls back to nearest row when expected direction is unknown`() {
        val rows = listOf(
            CallLogQueryHelper.RecentCall(number = "first", callType = 1, startedAt = 90_000L),
            CallLogQueryHelper.RecentCall(number = "nearest", callType = 2, startedAt = 100_200L),
        )

        assertEquals(
            "nearest",
            CallLogQueryHelper.bestMatch(
                rows = rows,
                expectedCallType = 0,
                sessionStartedAt = 100_000L,
            )?.number,
        )
    }
}
