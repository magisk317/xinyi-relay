package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.testing.relaxedContext
import io.github.magisk317.relay.testing.runtimeSmsMsg
import io.github.magisk317.relay.testing.smsMsgDatabaseFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelayRecordRepositoryTest {

    @Test
    fun `insertList merges duplicate import and preserves forward result`() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        val existing = runtimeSmsMsg(
            id = 22L,
            sender = "1068",
            body = "code 123456",
            date = 100L,
            company = "",
            packageName = "",
        ).copy(
            simSlot = 0,
            subId = 1,
            forwardStatus = SmsMsg.FORWARD_STATUS_SUCCESS,
            forwardTarget = "Yunhu",
            forwardMessage = "ok",
            forwardTime = 200L,
        )
        val incoming = existing.copy(
            id = 0L,
            company = "Bank",
            packageName = "com.bank.app",
            simSlot = -1,
            subId = 0,
            forwardStatus = SmsMsg.FORWARD_STATUS_NONE,
            forwardTarget = null,
            forwardMessage = null,
            forwardTime = 0L,
        )
        var updatedArg: SmsMsg? = null

        coEvery {
            smsMsgDao.getByFingerprint("1068", "code 123456", 100L, SmsMsg.MSG_TYPE_SMS)
        } returns existing
        coEvery { smsMsgDao.update(any()) } coAnswers {
            updatedArg = firstArg<SmsMsg>()
        }

        val repository = RelayRecordRepository(
            context = context,
            db = database,
            preferenceDataSource = preferences,
            recordUploadScheduler = {},
        )
        repository.insertList(listOf(incoming))

        val updated = requireNotNull(updatedArg)
        assertEquals(22L, updated.id)
        assertEquals("Bank", updated.company)
        assertEquals("com.bank.app", updated.packageName)
        assertEquals(0, updated.simSlot)
        assertEquals(1, updated.subId)
        assertEquals(SmsMsg.FORWARD_STATUS_SUCCESS, updated.forwardStatus)
        assertEquals("Yunhu", updated.forwardTarget)
        assertEquals("ok", updated.forwardMessage)
        assertEquals(200L, updated.forwardTime)
        coVerify(exactly = 0) { smsMsgDao.insert(any()) }
    }
}
