package io.github.magisk317.relay.feature.call

import io.github.magisk317.relay.feature.call.CallEndResolutionPolicy.NumberSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallEndResolutionPolicyTest {

    @Test
    fun `number resolution keeps direct and recent ingress ahead of CallLog`() {
        assertEquals(
            CallEndResolutionPolicy.Decision(
                number = "direct",
                source = NumberSource.Direct,
                shouldRetry = false,
            ),
            CallEndResolutionPolicy.decide(
                directNumber = "direct",
                recentIngressNumber = "recent",
                callLogNumber = "call-log",
                completedAttemptIndex = 0,
                retryAllowed = true,
            ),
        )

        assertEquals(
            NumberSource.RecentIngress,
            CallEndResolutionPolicy.decide(
                directNumber = null,
                recentIngressNumber = "recent",
                callLogNumber = "call-log",
                completedAttemptIndex = 0,
                retryAllowed = true,
            ).source,
        )
    }

    @Test
    fun `retry is allowed only while direct recent and CallLog numbers are missing`() {
        assertTrue(
            CallEndResolutionPolicy.decide(
                directNumber = null,
                recentIngressNumber = null,
                callLogNumber = null,
                completedAttemptIndex = 0,
                retryAllowed = true,
            ).shouldRetry,
            "Retry should be allowed when every number source is missing",
        )

        listOf(
            Triple("direct", null, null),
            Triple(null, "recent", null),
            Triple(null, null, "call-log"),
        ).forEach { (direct, recent, callLog) ->
            assertFalse(
                CallEndResolutionPolicy.decide(
                    directNumber = direct,
                    recentIngressNumber = recent,
                    callLogNumber = callLog,
                    completedAttemptIndex = 0,
                    retryAllowed = true,
                ).shouldRetry,
                "Retry should not be allowed for direct=$direct recent=$recent callLog=$callLog",
            )
        }
    }

    @Test
    fun `known direct or recent number skips CallLog entirely`() {
        runBlocking {
            listOf(
                "direct" to null,
                null to "recent",
            ).forEach { (direct, recent) ->
                var queryCount = 0
                val resolution = CallEndResolver.resolve(
                    directNumber = direct,
                    expectedCallType = 1,
                    retryAllowed = true,
                    recentNumberProvider = { recent },
                    callLogProvider = {
                        queryCount += 1
                        null
                    },
                    pause = {},
                )

                assertEquals(direct ?: recent, resolution.number)
                assertEquals(1, resolution.attemptCount)
                assertEquals(0, queryCount)
            }
        }
    }

    @Test
    fun `resolver backs off until a delayed CallLog row appears`() {
        runBlocking {
            val pauses = mutableListOf<Long>()
            val candidates = listOf<CallLogQueryHelper.RecentCall?>(
                null,
                null,
                CallLogQueryHelper.RecentCall(
                    number = "10086",
                    callType = 2,
                    startedAt = 10_000L,
                ),
            )
            var queryCount = 0

            val resolution = CallEndResolver.resolve(
                directNumber = null,
                expectedCallType = 1,
                retryAllowed = true,
                recentNumberProvider = { null },
                callLogProvider = { candidates[queryCount++] },
                pause = { pauses += it },
            )

            assertEquals(
                CallEndResolver.Resolution(
                    number = "10086",
                    callType = 2,
                    source = NumberSource.CallLog,
                    attemptCount = 3,
                ),
                resolution,
            )
            assertEquals(listOf(250L, 500L), pauses.toList())
            assertEquals(3, queryCount)
        }
    }

    @Test
    fun `delayed recent ingress stops before another CallLog query`() {
        runBlocking {
            val pauses = mutableListOf<Long>()
            val recentNumbers = listOf(null, "10010")
            var recentReadCount = 0
            var queryCount = 0

            val resolution = CallEndResolver.resolve(
                directNumber = null,
                expectedCallType = 1,
                retryAllowed = true,
                recentNumberProvider = { recentNumbers[recentReadCount++] },
                callLogProvider = {
                    queryCount += 1
                    null
                },
                pause = { pauses += it },
            )

            assertEquals("10010", resolution.number)
            assertEquals(NumberSource.RecentIngress, resolution.source)
            assertEquals(2, resolution.attemptCount)
            assertEquals(listOf(250L), pauses.toList())
            assertEquals(1, queryCount)
        }
    }

    @Test
    fun `missing CallLog permission disables retries`() {
        runBlocking {
            val pauses = mutableListOf<Long>()
            var queryCount = 0

            val resolution = CallEndResolver.resolve(
                directNumber = null,
                expectedCallType = 1,
                retryAllowed = false,
                recentNumberProvider = { null },
                callLogProvider = {
                    queryCount += 1
                    null
                },
                pause = { pauses += it },
            )

            assertEquals(1, resolution.attemptCount)
            assertEquals(NumberSource.Unavailable, resolution.source)
            assertEquals(emptyList<Long>(), pauses.toList())
            assertEquals(0, queryCount)
        }
    }

    @Test
    fun `retry budget is finite and uses increasing delays`() {
        runBlocking {
            val pauses = mutableListOf<Long>()
            var queryCount = 0

            val resolution = CallEndResolver.resolve(
                directNumber = null,
                expectedCallType = 1,
                retryAllowed = true,
                recentNumberProvider = { null },
                callLogProvider = {
                    queryCount += 1
                    null
                },
                pause = { pauses += it },
            )

            assertEquals(CallEndResolutionPolicy.attemptDelaysMs.size, resolution.attemptCount)
            assertEquals(NumberSource.Unavailable, resolution.source)
            assertEquals(CallEndResolutionPolicy.attemptDelaysMs.drop(1), pauses.toList())
            assertEquals(3_750L, pauses.sum())
            assertEquals(CallEndResolutionPolicy.attemptDelaysMs.size, queryCount)
        }
    }

    @Test
    fun `retry wait is cancellable before a terminal resolution`() {
        runBlocking {
            coroutineScope {
                val firstQuery = CompletableDeferred<Unit>()
                var queryCount = 0
                var completed = false
                val job = launch {
                    CallEndResolver.resolve(
                        directNumber = null,
                        expectedCallType = 1,
                        retryAllowed = true,
                        recentNumberProvider = { null },
                        callLogProvider = {
                            queryCount += 1
                            firstQuery.complete(Unit)
                            null
                        },
                    )
                    completed = true
                }

                firstQuery.await()
                job.cancelAndJoin()

                assertFalse(completed, "Resolver should not complete after cancellation")
                assertEquals(1, queryCount)
            }
        }
    }
}
