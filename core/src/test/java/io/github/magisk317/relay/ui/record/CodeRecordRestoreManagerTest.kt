package io.github.magisk317.relay.ui.record

import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CodeRecordRestoreManagerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun importRecordFiles_insertsFallbackRecordsWithoutApplyingHistoryTrim() {
        val recordFile = writeRecord(
            SmsMsg(
                sender = "1068",
                body = "code 123456",
                date = 100L,
                company = "Bank",
                smsCode = "123456",
                msgType = SmsMsg.MSG_TYPE_SMS,
            ),
        )
        var insertedRecords: List<SmsMsg>? = null

        val result = CodeRecordRestoreManager.importRecordFiles(
            recordFiles = arrayOf(recordFile),
            insertRecords = { records -> insertedRecords = records },
            logSuccess = {},
            logError = { _, _ -> },
        )

        assertTrue(result)
        assertEquals(listOf("123456"), insertedRecords?.map { it.smsCode })
        assertFalse(recordFile.exists())
    }

    @Test
    fun importRecordFiles_keepsFallbackFilesWhenInsertFails() {
        val recordFile = writeRecord(SmsMsg(sender = "1068", body = "code 123456", date = 100L))

        val result = CodeRecordRestoreManager.importRecordFiles(
            recordFiles = arrayOf(recordFile),
            insertRecords = { error("database unavailable") },
            logSuccess = {},
            logError = { _, _ -> },
        )

        assertFalse(result)
        assertTrue(recordFile.exists())
    }

    @Test
    fun importRecordFiles_ignoresMalformedFilesAfterSuccessfulInsert() {
        val recordFile = writeRecord(SmsMsg(sender = "1068", body = "code 123456", date = 100L))
        val malformedFile = File(tempDir, "CodeRecord_bad").apply {
            writeText("{", Charsets.UTF_8)
        }

        val result = CodeRecordRestoreManager.importRecordFiles(
            recordFiles = arrayOf(recordFile, malformedFile),
            insertRecords = { assertEquals(1, it.size) },
            logSuccess = {},
            logError = { _, _ -> },
        )

        assertTrue(result)
        assertFalse(recordFile.exists())
        assertTrue(malformedFile.exists())
    }

    private fun writeRecord(smsMsg: SmsMsg): File {
        return File(tempDir, "CodeRecord_${smsMsg.date}").apply {
            writeText(JsonUtils.toJson(smsMsg), Charsets.UTF_8)
        }
    }
}
