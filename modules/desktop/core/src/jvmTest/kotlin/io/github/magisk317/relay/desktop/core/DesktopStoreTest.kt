package io.github.magisk317.relay.desktop.core

import io.github.magisk317.relay.desktop.core.model.COMMAND_STATUS_APPLIED
import io.github.magisk317.relay.desktop.core.model.COMMAND_STATUS_FAILED
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.store.StoreError
import io.github.magisk317.relay.desktop.core.sync.SyncEngine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * The revision protocol is the contract shared with the Go backend; these tests
 * pin the formula and the queue ordering used by the pending-command feed.
 */
class DeviceConfigQueueTest {

    @Test
    fun `first command starts from revision zero`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            val command = store.queueDeviceConfigCommand(1L, 0L, "first", json("a" to 1))
            assertEquals(0L, command.baseRevision)
            assertEquals(1L, command.targetRevision)
        } finally {
            database.close()
        }
    }

    @Test
    fun `expected base revision is the max of mirror and pending target`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            store.queueDeviceConfigCommand(1L, 0L, "one", json("a" to 1))
            store.queueDeviceConfigCommand(1L, 1L, "two", json("a" to 2))

            val stale = assertFailsWith<StoreError.Conflict> {
                store.queueDeviceConfigCommand(1L, 0L, "stale", json("a" to 3))
            }
            assertEquals(2L, stale.local)
            assertEquals(0L, stale.remote)

            val accepted = store.queueDeviceConfigCommand(1L, 2L, "three", json("a" to 3))
            assertEquals(3L, accepted.targetRevision)
        } finally {
            database.close()
        }
    }

    @Test
    fun `conflict message keeps the legacy format`() {
        assertEquals(
            "conflict: local revision 2 vs remote revision 0",
            StoreError.Conflict(local = 2L, remote = 0L).toString(),
        )
    }

    @Test
    fun `pending commands are ordered by created_at then id`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            store.queueDeviceConfigCommand(1L, 0L, "one", json("a" to 1))
            store.queueDeviceConfigCommand(1L, 1L, "two", json("a" to 2))
            val pending = store.getDeviceConfig(1L).pendingCommands.map { it.summary }
            assertEquals(listOf("one", "two"), pending)
        } finally {
            database.close()
        }
    }

    @Test
    fun `ack rejects anything but applied and failed`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            val command = store.queueDeviceConfigCommand(1L, 0L, "one", json("a" to 1))
            assertFailsWith<StoreError.Internal> {
                store.ackLocalDeviceConfigCommand(
                    deviceId = 1L,
                    commandId = command.id,
                    status = "weird",
                    appliedRevision = 1L,
                    failureReason = "",
                    snapshot = json("a" to 1),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `applied ack advances the mirror and rejects a second ack`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            val command = store.queueDeviceConfigCommand(1L, 0L, "one", json("a" to 1))
            store.ackLocalDeviceConfigCommand(
                deviceId = 1L,
                commandId = command.id,
                status = COMMAND_STATUS_APPLIED,
                appliedRevision = 1L,
                failureReason = "",
                snapshot = json("a" to 1),
            )
            assertEquals(1L, store.getDeviceConfig(1L).revision)
            assertEquals(0, store.getDeviceConfig(1L).pendingCommands.size)

            assertFailsWith<StoreError.Conflict> {
                store.ackLocalDeviceConfigCommand(
                    deviceId = 1L,
                    commandId = command.id,
                    status = COMMAND_STATUS_FAILED,
                    appliedRevision = 1L,
                    failureReason = "again",
                    snapshot = json("a" to 1),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `a mirror jump ahead of pending commands drops the stale queue`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            store.queueDeviceConfigCommand(1L, 0L, "one", json("a" to 1))
            store.upsertDeviceConfigMirror(1L, 4L, json("a" to 4))
            val pending = store.getDeviceConfig(1L).pendingCommands
            assertTrue(pending.isEmpty(), "commands behind the mirror must not linger")
            assertEquals(4L, store.getDeviceConfig(1L).revision)
        } finally {
            database.close()
        }
    }

    @Test
    fun `bind codes are consumed once and stored as digests`() = runBlocking {
        val (database, store) = testStore()
        try {
            val code = store.createBindCode()
            val first = store.registerLocalDevice(
                bindCode = code.code,
                deviceName = "pixel",
                deviceModel = "Pixel 8",
                platform = "android",
                appVersion = "0.2.5",
                deviceToken = "token-1",
            )
            assertEquals(1L, first?.id)
            assertEquals(
                1L,
                store.authenticateLocalDevice("token-1"),
                "the token must authenticate against its digest",
            )
            assertEquals(
                null,
                store.registerLocalDevice(
                    bindCode = code.code,
                    deviceName = "pixel",
                    deviceModel = "Pixel 8",
                    platform = "android",
                    appVersion = "0.2.5",
                    deviceToken = "token-2",
                ),
                "a bind code must be single use",
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `revoking a device invalidates its token`() = runBlocking {
        val (database, store) = testStore()
        try {
            val code = store.createBindCode()
            store.registerLocalDevice(
                bindCode = code.code,
                deviceName = "pixel",
                deviceModel = "Pixel 8",
                platform = "android",
                appVersion = "0.2.5",
                deviceToken = "token-1",
            )
            store.revokeDevice(1L)
            assertEquals(null, store.authenticateLocalDevice("token-1"))
        } finally {
            database.close()
        }
    }

    private fun json(vararg pairs: Pair<String, Int>): JsonElement =
        JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })
}

/**
 * Record sync is the idempotency-critical path; these tests pin the inserted /
 * updated / deleted accounting of the Rust implementation.
 */
class RecordSyncTest {

    @Test
    fun `repeated event ids update in place and keep the row id`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            val first = store.syncLocalDeviceRecords(
                deviceId = 1L,
                incoming = listOf(record("evt-1", "hello"), record("evt-2", "world")),
                replaceExisting = false,
            )
            assertEquals(RecordSyncResultProbe(2, 0, 0), first.probe())

            val second = store.syncLocalDeviceRecords(
                deviceId = 1L,
                incoming = listOf(record("evt-1", "hello again")),
                replaceExisting = false,
            )
            assertEquals(RecordSyncResultProbe(0, 1, 0), second.probe())

            val stored = database.relayRecordDao().listPage(10, 0)
            assertEquals(2, stored.size)
            assertEquals("hello again", stored.first { it.eventId == "evt-1" }.body)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replaceExisting deletes rows missing from the incoming snapshot`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            store.syncLocalDeviceRecords(
                deviceId = 1L,
                incoming = listOf(record("evt-1", "a"), record("evt-2", "b")),
                replaceExisting = false,
            )
            val result = store.syncLocalDeviceRecords(
                deviceId = 1L,
                incoming = listOf(record("evt-1", "a")),
                replaceExisting = true,
            )
            assertEquals(RecordSyncResultProbe(0, 1, 1), result.probe())
            assertEquals(1, database.relayRecordDao().listPage(10, 0).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `records without an event id are inserted and never deduplicated`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            val result = store.syncLocalDeviceRecords(
                deviceId = 1L,
                incoming = listOf(record(null, "a"), record(null, "b")),
                replaceExisting = false,
            )
            assertEquals(RecordSyncResultProbe(2, 0, 0), result.probe())
        } finally {
            database.close()
        }
    }

    private fun record(eventId: String?, body: String): Record =
        Record(
            deviceId = 1L,
            eventId = eventId,
            recordType = "sms",
            sender = "10086",
            body = body,
            occurredAt = "2026-10-02T00:00:00Z",
            uploadedAt = "2026-10-02T00:00:00Z",
        )

    private fun io.github.magisk317.relay.desktop.core.model.RecordSyncResult.probe() =
        RecordSyncResultProbe(inserted, updated, deleted)

    private data class RecordSyncResultProbe(val inserted: Int, val updated: Int, val deleted: Int)
}

/**
 * Guards the sync state machine: single flight, throttling, pull/push outcomes.
 */
class SyncEngineTest {

    @Test
    fun `pull reports the pulled revision when the remote is ahead`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            val remote = FakeRemoteView(devices = listOf(device(1L)))
            remote.setState(1L, revision = 3L, snapshot = json("a" to 3))
            val engine = SyncEngine(local = store, remote = remote)
            val report = engine.pull()
            assertEquals(
                io.github.magisk317.relay.desktop.core.model.SyncResult.Pulled(3L),
                report.config,
            )
            assertEquals(3L, store.getDeviceConfig(1L).revision)
        } finally {
            database.close()
        }
    }

    @Test
    fun `pull reports a conflict when the local mirror is ahead with pending work`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            store.upsertDeviceConfigMirror(1L, 4L, json("a" to 4))
            store.queueDeviceConfigCommand(1L, 4L, "local change", json("a" to 5))

            val remote = FakeRemoteView(devices = listOf(device(1L)))
            remote.setState(1L, revision = 2L, snapshot = json("a" to 2))
            val report = SyncEngine(local = store, remote = remote).pull()
            assertEquals(
                io.github.magisk317.relay.desktop.core.model.SyncResult.Conflict(4L, 2L),
                report.config,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `push clears the queue only after every command is accepted`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            store.queueDeviceConfigCommand(1L, 0L, "one", json("a" to 1))
            val remote = FakeRemoteView(devices = listOf(device(1L)))
            remote.setState(1L, revision = 0L, snapshot = json("a" to 0))

            val report = SyncEngine(local = store, remote = remote).push()
            assertEquals(
                io.github.magisk317.relay.desktop.core.model.SyncResult.Pushed(1L),
                report.config,
            )
            assertEquals(0, store.getDeviceConfig(1L).pendingCommands.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a stale base revision from the remote surfaces as a conflict`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            store.queueDeviceConfigCommand(1L, 0L, "one", json("a" to 1))
            val remote = FakeRemoteView(devices = listOf(device(1L)))
            remote.setState(1L, revision = 9L, snapshot = json("a" to 9))

            val report = SyncEngine(local = store, remote = remote).push()
            assertEquals(
                io.github.magisk317.relay.desktop.core.model.SyncResult.Conflict(0L, 9L),
                report.config,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `hybrid pulls are throttled to one per five minutes`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            val remote = FakeRemoteView(devices = listOf(device(1L)))
            remote.setState(1L, revision = 1L, snapshot = json("a" to 1))
            val engine = SyncEngine(local = store, remote = remote)

            engine.pull(nowMillis = 1_000L)
            val throttled = engine.pull(nowMillis = 1_000L + 60_000L)
            assertEquals(
                io.github.magisk317.relay.desktop.core.model.SyncResult.UpToDate,
                throttled.config,
            )

            val allowed = engine.pull(nowMillis = 1_000L + 400_000L)
            assertEquals(
                io.github.magisk317.relay.desktop.core.model.SyncResult.Pulled(1L),
                allowed.config,
            )

            val forced = engine.pull(nowMillis = 1_000L + 400_001L, force = true)
            assertEquals(
                io.github.magisk317.relay.desktop.core.model.SyncResult.Pulled(1L),
                forced.config,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `a second concurrent sync is rejected instead of queued`() = runBlocking {
        val (database, store) = testStore()
        try {
            database.seedDevice(1L)
            // Deterministic concurrency: the fake parks inside `listDevices` until
            // we release it, so the first sync provably holds the single-flight
            // guard while the second (synchronous) call tries to acquire it.
            val entered = CompletableDeferred<Unit>()
            val releaser = CompletableDeferred<Unit>()
            val remote = FakeRemoteView(
                devices = listOf(device(1L)),
                entered = entered,
                releaser = releaser,
            )
            remote.setState(1L, revision = 1L, snapshot = json("a" to 1))
            val engine = SyncEngine(local = store, remote = remote)

            val first = async { engine.pull(nowMillis = 0L) }
            entered.await() // first sync now holds the guard, parked in the remote read

            val error = runCatching { engine.pull(nowMillis = 10L, force = true) }.exceptionOrNull()
            assertTrue(
                error is StoreError.Internal && error.message == SyncEngine.SYNC_IN_PROGRESS,
                "concurrent syncs must be rejected, got: $error",
            )

            releaser.complete(Unit)
            first.await()
        } finally {
            database.close()
        }
    }

    private fun device(id: Long) = io.github.magisk317.relay.desktop.core.model.Device(
        id = id,
        deviceName = "device-$id",
        displayName = "device-$id",
        createdAt = "2026-10-02T00:00:00Z",
        updatedAt = "2026-10-02T00:00:00Z",
    )

    private fun json(vararg pairs: Pair<String, Int>): JsonElement =
        JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) })

    private class FakeRemoteView(
        private val devices: List<io.github.magisk317.relay.desktop.core.model.Device>,
        private val entered: CompletableDeferred<Unit>? = null,
        private val releaser: CompletableDeferred<Unit>? = null,
    ) : SyncEngine.RemoteView {

        private val states = mutableMapOf<Long, io.github.magisk317.relay.desktop.core.model.DeviceConfigState>()
        private val records = mutableListOf<Record>()

        fun setState(deviceId: Long, revision: Long, snapshot: JsonElement) {
            states[deviceId] = io.github.magisk317.relay.desktop.core.model.DeviceConfigState(
                deviceId = deviceId,
                revision = revision,
                snapshot = snapshot,
            )
        }

        override suspend fun listDevices(): List<io.github.magisk317.relay.desktop.core.model.Device> {
            // Announce we've entered (and thus the caller holds the single-flight
            // guard), then park until released. Sequential tests pass no latches, so
            // `?.await()` on a null latch returns immediately and behaves unchanged.
            entered?.complete(Unit)
            releaser?.await()
            return devices
        }

        override suspend fun getDeviceConfig(deviceId: Long): io.github.magisk317.relay.desktop.core.model.DeviceConfigState? =
            states[deviceId]

        override suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record> =
            Paginated(items = records.toList(), limit = limit, offset = 0)

        override suspend fun queueDeviceConfigCommand(
            deviceId: Long,
            baseRevision: Long,
            summary: String,
            mutation: JsonElement,
        ): DeviceConfigCommand {
            val current = states[deviceId]
                ?: throw StoreError.Internal("unknown device $deviceId")
            if (current.revision != baseRevision) {
                throw StoreError.Conflict(local = baseRevision, remote = current.revision)
            }
            val target = baseRevision + 1
            setState(deviceId, target, mutation)
            return DeviceConfigCommand(
                id = target,
                baseRevision = baseRevision,
                targetRevision = target,
                mutation = mutation,
                summary = summary,
                status = COMMAND_STATUS_APPLIED,
            )
        }
    }
}
