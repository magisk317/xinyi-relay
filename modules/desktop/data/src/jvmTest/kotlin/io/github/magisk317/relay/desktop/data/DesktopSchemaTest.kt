package io.github.magisk317.relay.desktop.data

import io.github.magisk317.relay.desktop.data.entity.DeviceConfigCommandEntity
import io.github.magisk317.relay.desktop.data.entity.RelayRecordEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.runBlocking

/**
 * Schema-level guarantees that cannot be expressed with Room annotations.
 */
class DesktopSchemaTest {

    @Test
    fun `duplicate (device_id, event_id) rows are rejected`() = runBlocking {
        val database = DesktopDatabaseFactory.inMemory()
        try {
            val dao = database.relayRecordDao()
            dao.insert(record(eventId = "evt-1", body = "first"))
            dao.insert(record(eventId = "evt-2", body = "second"))

            assertFails("the partial unique index must reject a repeated event_id") {
                dao.insert(record(eventId = "evt-1", body = "duplicate"))
            }

            assertEquals(2, dao.listEventIds(1L).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `rows with a null event_id may repeat`() = runBlocking {
        val database = DesktopDatabaseFactory.inMemory()
        try {
            val dao = database.relayRecordDao()
            dao.insert(record(eventId = null, body = "a"))
            dao.insert(record(eventId = null, body = "b"))
            assertEquals(2, dao.listPage(10, 0).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `duplicate pending target revisions are rejected`() = runBlocking {
        val database = DesktopDatabaseFactory.inMemory()
        try {
            val dao = database.deviceConfigDao()
            dao.insertCommand(pendingCommand(targetRevision = 1L, createdAt = "2026-10-02T00:00:00Z"))
            assertFails("the pending partial unique index must reject a repeated target revision") {
                dao.insertCommand(pendingCommand(targetRevision = 1L, createdAt = "2026-10-02T00:00:01Z"))
            }
        } finally {
            database.close()
        }
    }

    private fun pendingCommand(targetRevision: Long, createdAt: String): DeviceConfigCommandEntity =
        DeviceConfigCommandEntity(
            deviceId = 7L,
            baseRevision = 0L,
            targetRevision = targetRevision,
            status = "pending",
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun record(eventId: String?, body: String): RelayRecordEntity =
        RelayRecordEntity(
            id = 0L,
            userId = 1L,
            deviceId = 1L,
            eventId = eventId,
            recordType = "sms",
            sender = "10086",
            body = body,
            occurredAt = "2026-10-02T00:00:00Z",
            uploadedAt = "2026-10-02T00:00:00Z",
        )
}
