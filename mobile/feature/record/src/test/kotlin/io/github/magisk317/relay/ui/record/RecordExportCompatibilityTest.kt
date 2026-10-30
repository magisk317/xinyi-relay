package io.github.magisk317.relay.ui.record

import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySmsMsg as SmsMsg
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecordData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordExportCompatibilityTest {

    @Test
    fun recordFileData_serializesExactlyLikeTheSmsMsgEntity() {
        val entity = SmsMsg(
            id = 7L,
            sender = "Bank",
            body = "Your code is 123456",
            date = 1_700_000_000_000L,
            processedTime = 1_700_000_000_500L,
            company = "Bank",
            smsCode = "123456",
            packageName = "com.example.bank",
            notifyChannelId = "channel",
            simSlot = 1,
            subId = 2,
            contactName = "Alice",
            phoneArea = "+86",
            msgType = SmsMsg.MSG_TYPE_SMS,
            callType = 3,
            sessionKey = "session",
        )

        val record = SmsMsgRecordData(
            id = entity.id,
            sender = entity.sender,
            body = entity.body,
            date = entity.date,
            processedTime = entity.processedTime,
            company = entity.company,
            smsCode = entity.smsCode,
            packageName = entity.packageName,
            notifyChannelId = entity.notifyChannelId,
            simSlot = entity.simSlot,
            subId = entity.subId,
            contactName = entity.contactName,
            phoneArea = entity.phoneArea,
            msgType = entity.msgType,
            callType = entity.callType,
            sessionKey = entity.sessionKey,
        )

        assertEquals(
            Json.encodeToString(entity),
            Json.encodeToString(record),
        )
    }
}
