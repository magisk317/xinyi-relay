package io.github.magisk317.relay.data.db

import io.github.magisk317.forward.db.ForwardEventEntity
import io.github.magisk317.relay.android.data.mapper.mergeForwardEventForInsert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the merge the record upsert uses when a duplicate insert lands on an
 * existing row: richer metadata wins, and an incoming forward result always wins.
 */
class ForwardRecordMergeForInsertTest {

    @Test
    fun `merge preserves forward result when duplicate insert carries default status`() {
        val existing = ForwardEventEntity(
            id = 16,
            sender = "10010",
            body = "验证码：718051",
            date = 1_775_007_198_000L,
            company = "",
            smsCode = "718051",
            packageName = "",
            msgType = ForwardEventEntity.MSG_TYPE_SMS,
            forwardStatus = ForwardEventEntity.FORWARD_STATUS_SUCCESS,
            forwardTarget = "钉钉",
            forwardMessage = "钉钉通道转发成功",
            forwardTime = 1_775_007_201_700L,
        )
        val incoming = existing.copy(
            id = 0,
            company = "中国联通",
            packageName = "com.sinovatech.unicom.ui",
            forwardStatus = ForwardEventEntity.FORWARD_STATUS_NONE,
            forwardTarget = null,
            forwardMessage = null,
            forwardTime = 0L,
        )

        val merged = mergeForwardEventForInsert(existing, incoming)

        assertEquals(16, merged.id)
        assertEquals("中国联通", merged.company)
        assertEquals("com.sinovatech.unicom.ui", merged.packageName)
        assertEquals(ForwardEventEntity.FORWARD_STATUS_SUCCESS, merged.forwardStatus)
        assertEquals("钉钉", merged.forwardTarget)
        assertEquals("钉钉通道转发成功", merged.forwardMessage)
        assertEquals(1_775_007_201_700L, merged.forwardTime)
    }

    @Test
    fun `merge applies incoming forward result when duplicate insert already has one`() {
        val existing = ForwardEventEntity(
            id = 7,
            sender = "10010",
            body = "验证码：999420",
            date = 1_774_997_673_000L,
            msgType = ForwardEventEntity.MSG_TYPE_SMS,
        )
        val incoming = existing.copy(
            id = 0,
            forwardStatus = ForwardEventEntity.FORWARD_STATUS_FAILED,
            forwardTarget = "SmsCode Engine",
            forwardMessage = "IPC token missing",
            forwardTime = 1_774_997_674_000L,
        )

        val merged = mergeForwardEventForInsert(existing, incoming)

        assertEquals(ForwardEventEntity.FORWARD_STATUS_FAILED, merged.forwardStatus)
        assertEquals("SmsCode Engine", merged.forwardTarget)
        assertEquals("IPC token missing", merged.forwardMessage)
        assertEquals(1_774_997_674_000L, merged.forwardTime)
    }
}
