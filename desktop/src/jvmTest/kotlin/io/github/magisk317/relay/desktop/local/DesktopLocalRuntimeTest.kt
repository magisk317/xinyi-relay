package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.core.model.BindCode
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.SyncResult
import io.github.magisk317.relay.desktop.core.model.SystemInfo
import io.github.magisk317.relay.desktop.core.store.RemoteStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Walks the app's only path from `:desktop:core`/`:desktop:data` down to an open
 * SQLite store. The store and the sync engine are covered by their own module
 * tests; what is pinned here is that the composition root assembles them the way
 * the app will use them, and that it owns the database lifecycle.
 */
class DesktopLocalRuntimeTest {

    @Test
    fun `pull mirrors remote state and records into the local store`() = runBlocking {
        val remote = FakeRemoteStore()
        val runtime = DesktopLocalRuntime.createInMemory(remote)
        try {
            val report = runtime.sync.initialPull()

            assertEquals(SyncResult.Pulled(4), report.config)
            assertEquals(1, report.devicesSynced)
            assertEquals(1, report.recordsSynced)

            val devices = runtime.store.listDevices()
            assertEquals(1, devices.size)
            assertEquals(7L, devices.first().id)

            val config = runtime.store.getDeviceConfig(7L)
            assertEquals(4L, config.revision)
            assertEquals(remote.snapshot, config.snapshot)

            val records = runtime.store.listRecords(limit = 10)
            assertEquals(1, records.items.size)
            assertEquals("bank", records.items.first().sender)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `close releases the database it owns`() = runBlocking {
        val runtime = DesktopLocalRuntime.createInMemory(FakeRemoteStore())
        runtime.sync.initialPull()
        runtime.close()

        val failure = runCatching { runtime.store.listDevices() }.exceptionOrNull()
        assertTrue(
            failure is IllegalStateException,
            "a closed store must reject reads, saw $failure",
        )
    }

    private class FakeRemoteStore(
        val snapshot: JsonElement = JsonObject(mapOf("theme" to JsonPrimitive("dark"))),
    ) : RemoteStore {

        private val device = Device(
            id = 7L,
            deviceName = "pixel",
            displayName = "Pixel",
            createdAt = "2026-10-02T00:00:00Z",
            updatedAt = "2026-10-02T00:00:00Z",
        )

        private val record = Record(
            id = 42L,
            deviceId = 7L,
            sender = "bank",
            body = "123456",
            occurredAt = "2026-10-02T00:00:00Z",
        )

        override suspend fun listDevices(): List<Device> = listOf(device)

        override suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState =
            DeviceConfigState(deviceId = deviceId, revision = 4L, snapshot = snapshot)

        override suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record> =
            Paginated(items = listOf(record), limit = limit, offset = 0)

        override suspend fun listDeviceConfigAuditLogs(
            deviceId: Long,
            limit: Int,
            offset: Int,
        ): Paginated<DeviceConfigAuditLog> = error("the pull path never reads audit logs")

        override suspend fun patchDevice(
            deviceId: Long,
            displayName: String?,
            enabled: Boolean?,
        ): JsonElement = error("the pull path never patches devices")

        override suspend fun revokeDevice(deviceId: Long): JsonElement = error("the pull path never revokes")

        override suspend fun createBindCode(): BindCode = error("the pull path never binds a code")

        override suspend fun getRecord(recordId: Long): Record? = error("the pull path never reads one record")

        override suspend fun getSystemInfo(): SystemInfo = error("the pull path never reads system info")

        override suspend fun queueDeviceConfigCommand(
            deviceId: Long,
            baseRevision: Long,
            summary: String,
            mutation: JsonElement,
        ): DeviceConfigCommand = error("the pull path never queues commands")

        override fun setAccessToken(token: String?) = Unit
    }
}
