package io.github.magisk317.relay.ui.record

import io.github.magisk317.forward.engine.model.ReadRecordData
import io.github.magisk317.forward.engine.model.ReadRecordDataImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordQueryStateTest {

    @Test
    fun buildRecordQueryState_splitsRecordsByTab() {
        val state = buildRecordQueryState(
            listOf(
                record(id = 1, msgType = ReadRecordData.MSG_TYPE_SMS, smsCode = "123456"),
                record(id = 2, msgType = ReadRecordData.MSG_TYPE_SMS, smsCode = ""),
                record(id = 3, msgType = ReadRecordData.MSG_TYPE_APP_NOTIFY, packageName = "com.example.app"),
                record(id = 4, msgType = ReadRecordData.MSG_TYPE_CALL_NOTIFY),
            ),
        )

        assertEquals(listOf(1L), state.codeRecords.map { it.id })
        assertEquals(listOf(2L), state.plainSmsRecords.map { it.id })
        assertEquals(listOf(3L), state.appNotifyRecords.map { it.id })
        assertEquals(listOf(4L), state.callNotifyRecords.map { it.id })
        assertEquals(state.appNotifyRecords, state.recordsForTab(2))
    }

    @Test
    fun buildRecordQueryState_deduplicatesSimilarCodeRecordsKeepingNewest() {
        val state = buildRecordQueryState(
            listOf(
                record(id = 1, body = "Your code is 123456", smsCode = "123456", date = 1_000L),
                record(id = 2, body = "Your code is 123456", smsCode = "123456", date = 2_000L),
                record(id = 3, body = "Your code is 654321", smsCode = "654321", date = 3_000L),
            ),
        )

        assertEquals(listOf(3L, 2L), state.codeRecords.map { it.id })
    }

    private fun record(
        id: Long,
        msgType: Int = ReadRecordData.MSG_TYPE_SMS,
        body: String = "body",
        smsCode: String? = null,
        packageName: String? = null,
        date: Long = id * 10_000L,
    ): ReadRecordData {
        return ReadRecordDataImpl(
            id = id,
            sender = "Bank",
            body = body,
            date = date,
            processedTime = date,
            company = "Bank",
            smsCode = smsCode,
            packageName = packageName,
            notifyChannelId = "",
            simSlot = -1,
            subId = 0,
            contactName = "",
            phoneArea = "",
            forwardStatus = 0,
            forwardTarget = null,
            forwardMessage = null,
            forwardTime = 0L,
            msgType = msgType,
            callType = 0,
            sessionKey = "",
        )
    }
}
