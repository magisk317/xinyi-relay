package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.core.model.BindCode
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.SystemInfo
import io.github.magisk317.relay.desktop.core.store.RemoteStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the app-facing lifecycle of the local mirror: start opens a store and
 * mirrors the backend, a failing round degrades to an error status instead of
 * crashing, a later round recovers, stop closes the store, and a restart opens
 * a fresh one. Store and engine semantics live in their own module tests; what
 * matters here is that the controller the UI holds survives all of it.
 */
class DesktopLocalSyncTest {

    @Test
    fun `start mirrors the backend and reports the row counts`() = runBlocking {
        val controller = controller()
        controller.start(FakeRemoteStore())
        try {
            val status = controller.status
            assertFalse(status.syncing)
            assertNull(status.error)
            assertTrue(status.established)
            assertEquals(1, status.devices)
            assertEquals(1, status.records)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `a failing round degrades to an error status and the next round recovers`() = runBlocking {
        val remote = FakeRemoteStore()
        val controller = controller()
        try {
            controller.start(remote)
            remote.failDevices = true
            controller.sync()
            assertNotNull(controller.status.error)
            assertTrue(controller.active, "a failed round must not close the mirror")

            remote.failDevices = false
            controller.sync()
            assertNull(controller.status.error)
            assertEquals(1, controller.status.devices)
            assertEquals(1, controller.status.records)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `stop closes the store and a restart opens a fresh one`() = runBlocking {
        val controller = controller()
        controller.start(FakeRemoteStore())
        controller.stop()
        assertFalse(controller.active)
        assertFalse(controller.status.established)
        assertFalse(controller.status.syncing)

        controller.start(FakeRemoteStore())
        assertTrue(controller.active)
        assertEquals(1, controller.status.devices)
        controller.stop()
    }

    @Test
    fun `sync without a started runtime is a no-op`() = runBlocking {
        val controller = controller()
        controller.sync()
        assertFalse(controller.active)
        assertFalse(controller.status.established)
        assertFalse(controller.status.syncing)
    }

    private fun controller() = DesktopLocalSyncController { remote ->
        DesktopLocalRuntime.createInMemory(remote)
    }

    private class FakeRemoteStore(var failDevices: Boolean = false) : RemoteStore {

        val snapshot: JsonElement = JsonObject(mapOf("theme" to JsonPrimitive("dark")))

        private val device = Device(
            id = 7L,
            deviceName = "pixel",
            displayName = "Pixel",
            createdAt = "2026-10-03T00:00:00Z",
            updatedAt = "2026-10-03T00:00:00Z",
        )

        private val record = Record(
            id = 42L,
            deviceId = 7L,
            sender = "bank",
            body = "123456",
            occurredAt = "2026-10-03T00:00:00Z",
        )

        override suspend fun listDevices(): List<Device> {
            if (failDevices) error("backend unreachable")
            return listOf(device)
        }

        override suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState =
            DeviceConfigState(deviceId = deviceId, revision = 4L, snapshot = snapshot)

        override suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record> {
            if (failDevices) error("backend unreachable")
            return Paginated(items = listOf(record), limit = limit, offset = 0)
        }

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
