package io.github.magisk317.relay.feature.call

import io.github.magisk317.relay.feature.call.CallEndResolutionPolicy.NumberSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class CallEndResolutionPolicyTest : FunSpec({

    test("number resolution keeps direct and recent ingress ahead of CallLog") {
        CallEndResolutionPolicy.decide(
            directNumber = "direct",
            recentIngressNumber = "recent",
            callLogNumber = "call-log",
            completedAttemptIndex = 0,
            retryAllowed = true,
        ) shouldBe CallEndResolutionPolicy.Decision(
            number = "direct",
            source = NumberSource.Direct,
            shouldRetry = false,
        )

        CallEndResolutionPolicy.decide(
            directNumber = null,
            recentIngressNumber = "recent",
            callLogNumber = "call-log",
            completedAttemptIndex = 0,
            retryAllowed = true,
        ).source shouldBe NumberSource.RecentIngress
    }

    test("retry is allowed only while direct recent and CallLog numbers are missing") {
        CallEndResolutionPolicy.decide(
            directNumber = null,
            recentIngressNumber = null,
            callLogNumber = null,
            completedAttemptIndex = 0,
            retryAllowed = true,
        ).shouldRetry shouldBe true

        listOf(
            Triple("direct", null, null),
            Triple(null, "recent", null),
            Triple(null, null, "call-log"),
        ).forEach { (direct, recent, callLog) ->
            CallEndResolutionPolicy.decide(
                directNumber = direct,
                recentIngressNumber = recent,
                callLogNumber = callLog,
                completedAttemptIndex = 0,
                retryAllowed = true,
            ).shouldRetry shouldBe false
        }
    }

    test("known direct or recent number skips CallLog entirely") {
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

            resolution.number shouldBe (direct ?: recent)
            resolution.attemptCount shouldBe 1
            queryCount shouldBe 0
        }
    }

    test("resolver backs off until a delayed CallLog row appears") {
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

        resolution shouldBe CallEndResolver.Resolution(
            number = "10086",
            callType = 2,
            source = NumberSource.CallLog,
            attemptCount = 3,
        )
        pauses.shouldContainExactly(250L, 500L)
        queryCount shouldBe 3
    }

    test("delayed recent ingress stops before another CallLog query") {
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

        resolution.number shouldBe "10010"
        resolution.source shouldBe NumberSource.RecentIngress
        resolution.attemptCount shouldBe 2
        pauses.shouldContainExactly(250L)
        queryCount shouldBe 1
    }

    test("missing CallLog permission disables retries") {
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

        resolution.attemptCount shouldBe 1
        resolution.source shouldBe NumberSource.Unavailable
        pauses shouldBe emptyList()
        queryCount shouldBe 0
    }

    test("retry budget is finite and uses increasing delays") {
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

        resolution.attemptCount shouldBe CallEndResolutionPolicy.attemptDelaysMs.size
        resolution.source shouldBe NumberSource.Unavailable
        pauses shouldBe CallEndResolutionPolicy.attemptDelaysMs.drop(1)
        pauses.sum() shouldBe 3_750L
        queryCount shouldBe CallEndResolutionPolicy.attemptDelaysMs.size
    }

    test("retry wait is cancellable before a terminal resolution") {
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

            completed shouldBe false
            queryCount shouldBe 1
        }
    }
})
