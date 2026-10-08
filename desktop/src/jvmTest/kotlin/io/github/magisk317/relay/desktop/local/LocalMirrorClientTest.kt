package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.contract.remote.DeviceConfigCommandRequest
import io.github.magisk317.relay.contract.remote.PatchDeviceRequest
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.store.DesktopClock
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.core.store.StoreError
import io.github.magisk317.relay.desktop.data.DesktopDatabaseFactory
import io.github.magisk317.relay.desktop.remote.ConsoleApiException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract of the Local run mode's data path (parity §5): every console call
 * answered from the SQLite mirror must come back as the same
 * `relay:contract` DTO the HTTP client returns, so no page can tell the
 * difference. Pinned here: the field-for-field mappings, the ack shape the
 * Rust `SqliteStore` echoes (`{"ok": true}`), the 404 for an unknown record
 * id, and that local writes land where the next read looks.
 */
class LocalMirrorClientTest {

    @Test
    fun `devices map field for field with the json fields as objects`() = runBlocking {
        val client = mirrorClient()

        val devices = client.devices().devices

        assertEquals(1, devices.size)
        val device = devices.first()
        assertEquals(7L, device.id)
        assertEquals("pixel", device.deviceName)
        assertEquals("Pixel", device.displayName)
        assertEquals("android", device.platform)
        assertTrue(device.enabled, "the mirror default is an enabled device")
        assertNull(device.revokedAt)
        assertEquals(
            JsonObject(mapOf("addr" to JsonPrimitive("10.0.0.2"))),
            device.localAddresses,
            "the stored address blob must survive as a json object",
        )
        assertEquals(JsonObject(emptyMap()), device.capabilities)
        assertEquals("2026-10-02T00:00:00Z", device.createdAt)
    }

    @Test
    fun `records echo the page and carry metadata as an object`() = runBlocking {
        val client = mirrorClient()

        val page = client.records(limit = 80, deviceId = 7L)

        assertEquals(1, page.records.size)
        val record = page.records.first()
        assertEquals(42L, record.id)
        assertEquals("bank", record.sender)
        assertEquals("123456", record.body)
        assertEquals(1, record.msgType)
        assertEquals(
            JsonObject(mapOf("sim" to JsonPrimitive(1))),
            record.metadata,
            "the stored metadata blob must survive as a json object",
        )
        assertEquals(80L, page.limit, "the page echoes the requested limit")
        assertEquals(0L, page.offset)
    }

    @Test
    fun `an unknown record id answers 404 like the remote endpoint`() = runBlocking {
        val client = mirrorClient()

        val failure = assertThrows(ConsoleApiException::class.java) {
            runBlocking { client.record(999L) }
        }

        assertEquals(404, failure.status)
    }

    @Test
    fun `device config carries the mirror snapshot and the pending queue`() = runBlocking {
        val client = mirrorClient()

        val config = client.deviceConfig(7L)

        assertNotNull(config)
        assertEquals(4L, config!!.revision)
        assertEquals(JsonObject(mapOf("theme" to JsonPrimitive("dark"))), config.mirrorContent)
        assertTrue(config.pendingCommands.isEmpty(), "the seed mirror holds no pending work")
        assertTrue(config.updatedAt.isNotBlank(), "the mirror stamps an updated_at")
    }

    @Test
    fun `queueing a command lands a pending row and an audit trail`() = runBlocking {
        val client = mirrorClient()
        val request = DeviceConfigCommandRequest(
            baseRevision = 4L,
            summary = "apps:update",
            mutation = buildJsonObject { put("op", "replace_device_apps") },
        )

        val queued = client.queueDeviceConfigCommand(7L, request)

        assertEquals(5L, queued.targetRevision, "one past the mirror revision")
        assertEquals("pending", queued.status)
        val pending = client.deviceConfig(7L)!!.pendingCommands
        assertEquals(1, pending.size, "the queue is now the fresh command")
        assertEquals("apps:update", pending.first().summary)

        val logs = client.deviceConfigAuditLogs(7L, 30, 0).logs
        assertEquals(1, logs.size, "a local queue writes one audit row, like the Rust store")
        assertEquals("command.queued", logs.first().eventType)
        assertEquals("apps:update", logs.first().summary)
        assertEquals(5L, logs.first().revision)
    }

    @Test
    fun `a stale base revision surfaces as a conflict`() = runBlocking {
        val client = mirrorClient()
        val request = DeviceConfigCommandRequest(
            baseRevision = 1L,
            summary = "apps:update",
            mutation = buildJsonObject { put("op", "noop") },
        )

        val failure = assertThrows(StoreError.Conflict::class.java) {
            runBlocking { client.queueDeviceConfigCommand(7L, request) }
        }

        assertEquals(4L, failure.local, "the mirror expects its own revision as the base")
    }

    @Test
    fun `system info comes from the store itself`() = runBlocking {
        val client = mirrorClient()

        val info = client.systemInfo()

        assertEquals("xinyi-relay-desktop", info.service)
        assertEquals("local://sqlite", info.localBaseUrl)
        assertTrue(info.databaseReady)
        assertEquals(1L, info.userCount, "one active device in the mirror")
        assertTrue(info.time.isNotBlank())
    }

    @Test
    fun `writes return the legacy ok ack and land in the store`() = runBlocking {
        val client = mirrorClient()
        val ok = JsonObject(mapOf("ok" to JsonPrimitive(true)))

        assertEquals(ok, client.patchDevice(7L, PatchDeviceRequest(displayName = "Pixel renamed")))
        assertEquals("Pixel renamed", client.devices().devices.first().displayName)

        assertEquals(ok, client.revokeDevice(7L))
        assertNotNull(client.devices().devices.first().revokedAt, "the mirror stamps the revocation")
    }

    @Test
    fun `a bind code is minted from the store`() = runBlocking {
        val client = mirrorClient()

        val bind = client.createBindCode()

        assertTrue(bind.code.isNotBlank())
        assertTrue(bind.expiresAt.isNotBlank())
    }

    private suspend fun mirrorClient(): LocalMirrorClient {
        val store = DesktopLocalStore(
            DesktopDatabaseFactory.inMemory("mirror-client-test"),
            DesktopClock(),
        )
        store.seed()
        return LocalMirrorClient(store)
    }

    private suspend fun DesktopLocalStore.seed() {
        upsertDevices(
            listOf(
                Device(
                    id = 7L,
                    userId = 1L,
                    deviceName = "pixel",
                    displayName = "Pixel",
                    platform = "android",
                    enabled = true,
                    localAddresses = JsonObject(mapOf("addr" to JsonPrimitive("10.0.0.2"))),
                    createdAt = "2026-10-02T00:00:00Z",
                    updatedAt = "2026-10-02T00:00:00Z",
                ),
            ),
        )
        upsertDeviceConfigMirror(
            deviceId = 7L,
            revision = 4L,
            snapshot = JsonObject(mapOf("theme" to JsonPrimitive("dark"))),
            updatedAt = "2026-10-02T00:00:00Z",
        )
        upsertRecords(
            listOf(
                Record(
                    id = 42L,
                    deviceId = 7L,
                    sender = "bank",
                    body = "123456",
                    msgType = 1,
                    occurredAt = "2026-10-02T00:00:00Z",
                    uploadedAt = "2026-10-02T00:00:00Z",
                    metadata = JsonObject(mapOf("sim" to JsonPrimitive(1))),
                ),
            ),
        )
    }
}
