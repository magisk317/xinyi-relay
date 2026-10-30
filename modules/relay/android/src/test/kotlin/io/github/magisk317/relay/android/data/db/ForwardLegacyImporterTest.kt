package io.github.magisk317.relay.android.data.db

import io.github.magisk317.forward.db.ForwardEventEntity
import io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySmsMsg as SmsMsg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForwardLegacyImporterTest {

    @Test
    fun `projects the forward fields of a legacy mixed row`() {
        val msg = SmsMsg(
            id = 7,
            sender = "13800138000",
            body = "您的验证码123456",
            date = 1_700_000_000_000,
            company = "Bank",
            smsCode = "123456",
            packageName = "com.android.mms",
            notifyChannelId = "ch",
            msgType = SmsMsg.MSG_TYPE_SMS,
            forwardStatus = SmsMsg.FORWARD_STATUS_SUCCESS,
            forwardTarget = "tg:1",
            forwardMessage = "forwarded body",
            forwardTime = 1_700_000_060_000,
            contactName = "Bank",
            phoneArea = "0755",
            sessionKey = "k1",
        )

        val event = ForwardLegacyImporter.projectEvent(msg)

        assertEquals(7, event.id)
        assertEquals(SmsMsg.MSG_TYPE_SMS, event.msgType)
        assertEquals("13800138000", event.sender)
        assertEquals("123456", event.smsCode)
        assertEquals(SmsMsg.FORWARD_STATUS_SUCCESS, event.forwardStatus)
        assertEquals("tg:1", event.forwardTarget)
        assertEquals(1_700_000_060_000, event.forwardTime)
        assertEquals("k1", event.sessionKey)
    }

    @Test
    fun `remaps dispatch log record ids through the event import map`() {
        val log = SenderDispatchLog(
            recordId = 7,
            senderId = 3,
            senderType = 1,
            msgType = 0,
            success = true,
            createdAt = 42,
        )

        val projected = ForwardLegacyImporter.projectDispatchLog(log, recordIdMap = mapOf(7L to 100L))

        assertEquals(100L, projected.recordId)
        assertEquals(3, projected.senderId)
        assertTrue(projected.success)
    }

    @Test
    fun `parity passes when every row round trips`() {
        val msgs = listOf(
            SmsMsg(id = 1, sender = "a", body = "b", date = 10, msgType = 0, forwardStatus = 1),
            SmsMsg(id = 2, sender = "c", body = "d", date = 20, msgType = 2, forwardStatus = 0),
        )
        val imported = msgs.map { ForwardLegacyImporter.projectEvent(it) }
        val logs = listOf(SenderDispatchLog(recordId = 1, senderId = 5, senderType = 0, msgType = 0, success = true, createdAt = 99))
        val importedLogs = logs.map { ForwardLegacyImporter.projectDispatchLog(it, mapOf(1L to 1L)) }

        val report = ForwardLegacyImporter.checkParity(
            legacy = msgs,
            imported = imported,
            legacyLogs = logs,
            importedLogs = importedLogs,
            recordIdMap = mapOf(1L to 1L, 2L to 2L),
        )

        assertTrue(report.ok, "report=$report")
    }

    @Test
    fun `parity flags a dropped event`() {
        val msgs = listOf(SmsMsg(id = 1, sender = "a", body = "b", date = 10, msgType = 0))
        val imported = emptyList<ForwardEventEntity>()

        val report = ForwardLegacyImporter.checkParity(
            legacy = msgs,
            imported = imported,
            legacyLogs = emptyList(),
            importedLogs = emptyList(),
            recordIdMap = emptyMap(),
        )

        assertFalse(report.ok)
        assertEquals("missing event for legacy id=1", report.firstEventMismatch)
    }

    @Test
    fun `parity flags a changed event field`() {
        val msg = SmsMsg(id = 1, sender = "a", body = "b", date = 10, msgType = 0)
        val imported = listOf(ForwardLegacyImporter.projectEvent(msg).copy(body = "changed"))

        val report = ForwardLegacyImporter.checkParity(
            legacy = listOf(msg),
            imported = imported,
            legacyLogs = emptyList(),
            importedLogs = emptyList(),
            recordIdMap = mapOf(1L to 1L),
        )

        assertFalse(report.ok)
        assertEquals("event mismatch id=1", report.firstEventMismatch)
    }

    @Test
    fun `parity flags a dispatch log with a broken record mapping`() {
        val msgs = listOf(SmsMsg(id = 1, sender = "a", body = "b", date = 10, msgType = 0))
        val imported = msgs.map { ForwardLegacyImporter.projectEvent(it) }
        val logs = listOf(SenderDispatchLog(recordId = 1, senderId = 5, senderType = 0, msgType = 0, success = true, createdAt = 99))

        val report = ForwardLegacyImporter.checkParity(
            legacy = msgs,
            imported = imported,
            legacyLogs = logs,
            importedLogs = emptyList(),
            recordIdMap = mapOf(1L to 1L),
        )

        assertFalse(report.ok)
        assertTrue(report.firstDispatchLogMismatch!!.contains("not imported"))
    }
}
