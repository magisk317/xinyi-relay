package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.db.entity.SmsBlacklistHit
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.engine.service.SenderDispatchResult
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
    fun `persistForwardResult replaces generated numeric sender name with channel type name`() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        val existing = runtimeSmsMsg(id = 12L)
        var updatedArg: SmsMsg? = null

        coEvery { smsMsgDao.getById(12L) } returns existing
        coEvery { smsMsgDao.update(any()) } coAnswers {
            updatedArg = firstArg<SmsMsg>()
        }

        val repository = RelayRecordRepository(
            context = context,
            db = database,
            preferenceDataSource = preferences,
            recordUploadScheduler = {},
        )
        repository.persistForwardResult(
            recordId = 12L,
            results = listOf(
                SenderDispatchResult(
                    senderId = 13L,
                    senderType = SenderType.FEISHU_APP,
                    senderName = "13",
                    success = true,
                    message = "OK",
                ),
            ),
            defaultMessage = "default",
            forceFailed = false,
            forcedStatus = null,
        )

        val updated = requireNotNull(updatedArg)
        assertEquals("飞书应用", updated.forwardTarget)
        assertEquals("飞书应用通道转发成功", updated.forwardMessage)
    }

    @Test
    fun `persistForwardResult does not duplicate channel suffix`() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        val existing = runtimeSmsMsg(id = 13L)
        var updatedArg: SmsMsg? = null

        coEvery { smsMsgDao.getById(13L) } returns existing
        coEvery { smsMsgDao.update(any()) } coAnswers {
            updatedArg = firstArg<SmsMsg>()
        }

        val repository = RelayRecordRepository(
            context = context,
            db = database,
            preferenceDataSource = preferences,
            recordUploadScheduler = {},
        )
        repository.persistForwardResult(
            recordId = 13L,
            results = listOf(
                SenderDispatchResult(
                    senderId = 9L,
                    senderType = SenderType.FEISHU,
                    senderName = "飞书机器人通道",
                    success = true,
                    message = "OK",
                ),
            ),
            defaultMessage = "default",
            forceFailed = false,
            forcedStatus = null,
        )

        val updated = requireNotNull(updatedArg)
        assertEquals("飞书机器人通道", updated.forwardTarget)
        assertEquals("飞书机器人通道转发成功", updated.forwardMessage)
    }

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

    @Test
    fun `blacklist hit remove restore and clear use blacklist hit dao`() = runBlocking {
        val context = relaxedContext()
        val (database, _, smsBlacklistHitDao) = smsMsgDatabaseFixture()
        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        val hit = SmsBlacklistHit(
            id = 8L,
            eventId = "evt-8",
            source = "dispatch_intent",
            sender = "1068",
            body = "blocked",
            smsDate = 100L,
            matchType = "number",
            pattern = "1068",
            actionDelete = true,
            actionBlock = true,
            blockReason = "blacklist_block",
            createdAt = 200L,
        )

        val repository = RelayRecordRepository(
            context = context,
            db = database,
            preferenceDataSource = preferences,
            recordUploadScheduler = {},
        )
        repository.removeSmsBlacklistHits(listOf(hit))
        repository.restoreSmsBlacklistHits(listOf(hit))
        repository.clearSmsBlacklistHits()

        coVerify(exactly = 1) { smsBlacklistHitDao.deleteAll(listOf(hit)) }
        coVerify(exactly = 1) { smsBlacklistHitDao.insertAll(listOf(hit)) }
        coVerify(exactly = 1) { smsBlacklistHitDao.clearAll() }
    }

    @Test
    fun `insert blacklist hit respects record setting and history limit`() = runBlocking {
        val context = relaxedContext()
        val (database, _, smsBlacklistHitDao) = smsMsgDatabaseFixture()
        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        val hit = SmsBlacklistHit(
            id = 8L,
            eventId = "evt-8",
            source = "dispatch_intent",
            sender = "1068",
            body = "blocked",
            smsDate = 100L,
            matchType = "number",
            pattern = "1068",
            actionDelete = true,
            actionBlock = true,
            blockReason = "blacklist_block",
            createdAt = 200L,
        )
        coEvery { preferences.getBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST_HIT_RECORDS, true) } returns true
        coEvery {
            preferences.getString(
                PrefConst.KEY_HISTORY_LIMIT_SMS_BLACKLIST_HIT,
                PrefConst.SMS_BLACKLIST_HIT_HISTORY_LIMIT_DEFAULT,
            )
        } returns "20"
        coEvery { smsBlacklistHitDao.insert(hit) } returns 9L

        val repository = RelayRecordRepository(
            context = context,
            db = database,
            preferenceDataSource = preferences,
            recordUploadScheduler = {},
        )
        val id = repository.insertSmsBlacklistHit(hit)

        assertEquals(9L, id)
        coVerify(exactly = 1) { smsBlacklistHitDao.insert(hit) }
        coVerify(exactly = 1) { smsBlacklistHitDao.trimToLimit(20) }
    }

    @Test
    fun `insert blacklist hit skips when record setting disabled`() = runBlocking {
        val context = relaxedContext()
        val (database, _, smsBlacklistHitDao) = smsMsgDatabaseFixture()
        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        val hit = SmsBlacklistHit(
            id = 8L,
            eventId = "evt-8",
            source = "dispatch_intent",
            sender = "1068",
            body = "blocked",
            smsDate = 100L,
            matchType = "number",
            pattern = "1068",
            actionDelete = true,
            actionBlock = true,
            blockReason = "blacklist_block",
            createdAt = 200L,
        )
        coEvery { preferences.getBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST_HIT_RECORDS, true) } returns false

        val repository = RelayRecordRepository(
            context = context,
            db = database,
            preferenceDataSource = preferences,
            recordUploadScheduler = {},
        )
        val id = repository.insertSmsBlacklistHit(hit)

        assertEquals(null, id)
        coVerify(exactly = 0) { smsBlacklistHitDao.insert(any()) }
        coVerify(exactly = 0) { smsBlacklistHitDao.trimToLimit(any()) }
    }
}
