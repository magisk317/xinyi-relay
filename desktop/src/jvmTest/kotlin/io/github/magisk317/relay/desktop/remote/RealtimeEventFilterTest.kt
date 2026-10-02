package io.github.magisk317.relay.desktop.remote

import io.github.magisk317.relay.contract.remote.RealtimeEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealtimeEventFilterTest {

    @Test
    fun `summary filter accepts every device and records mutation`() {
        SUMMARY_PAGE_EVENTS.forEach { eventType ->
            assertTrue(shouldRefreshSummaryOn(eventType), eventType)
        }
        assertTrue(SUMMARY_PAGE_EVENTS.contains("records.ingested"))
    }

    @Test
    fun `summary filter rejects unrelated events`() {
        listOf("app.created", "sender.toggled", "records.deleted", "").forEach { eventType ->
            assertFalse(shouldRefreshSummaryOn(eventType), eventType)
        }
    }

    @Test
    fun `summary filter rejects a missing event`() {
        assertFalse(shouldRefreshSummaryOn(null))
    }

    @Test
    fun `summary filter matches the parsed event type`() {
        val payload: JsonObject = buildJsonObject { }
        val event = RealtimeEvent(type = "device.heartbeat", time = "2026-10-02T00:00:00Z", data = payload)
        assertTrue(shouldRefreshSummaryOn(event.type))
    }
}
