package io.github.magisk317.relay.domain.system

import io.mockk.coEvery
import io.mockk.every
import io.github.magisk317.relay.android.data.db.entity.SmsBlacklistHit
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.relay.testing.relaxedContext
import io.github.magisk317.relay.testing.runtimeSmsMsg
import io.github.magisk317.relay.testing.smsMsgDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeRecordFacadeTest {

    @Test
    fun insertSmsRecord_delegatesToRelayRecordRepository() = runBlocking {
        val context = relaxedContext()
        val database = smsMsgDatabaseFixture().database
        val smsMsg = runtimeSmsMsg()
        var insertedSmsMsg: SmsMsg? = null
        var insertedIsCodeSms: Boolean? = null

        val facade = RuntimeRecordFacade(
            context = context,
            db = database,
            recordInserter = { incomingSmsMsg, isCodeSms ->
                insertedSmsMsg = incomingSmsMsg
                insertedIsCodeSms = isCodeSms
                42L
            },
        )
        val recordId = facade.insertSmsRecord(smsMsg, isCodeSms = true)

        assertEquals(42L, recordId)
        assertEquals(smsMsg, insertedSmsMsg)
        assertEquals(true, insertedIsCodeSms)
    }

    @Test
    fun insertSmsBlacklistHit_persistsHookHitRecord() = runBlocking {
        val context = relaxedContext()
        val database = smsMsgDatabaseFixture().database
        var insertedArg: SmsBlacklistHit? = null

        val facade = RuntimeRecordFacade(
            context = context,
            db = database,
            smsBlacklistHitInserter = { incoming ->
                insertedArg = incoming
                51L
            },
        )
        val id = facade.insertSmsBlacklistHit(
            XpSmsBlacklistHitRecord(
                eventId = "evt-1",
                source = "dispatch_intent",
                sender = "1068",
                body = "otp 123456",
                smsDate = 100L,
                matchType = "number",
                pattern = "1068",
                actionDelete = true,
                actionBlock = true,
                blockReason = "blacklist_block",
                createdAt = 200L,
            ),
        )

        val inserted = requireNotNull(insertedArg)
        assertEquals(51L, id)
        assertEquals("evt-1", inserted.eventId)
        assertEquals("dispatch_intent", inserted.source)
        assertEquals("1068", inserted.sender)
        assertEquals("otp 123456", inserted.body)
        assertEquals(100L, inserted.smsDate)
        assertEquals("number", inserted.matchType)
        assertEquals("1068", inserted.pattern)
        assertTrue(inserted.actionDelete)
        assertTrue(inserted.actionBlock)
        assertEquals("blacklist_block", inserted.blockReason)
        assertEquals(200L, inserted.createdAt)
    }

    @Test
    fun persistSmsForwardResult_updatesExistingRecord() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val existing = runtimeSmsMsg(
            id = 7L,
            sender = "1068",
            body = "code 123456",
            date = 100L,
        )
        coEvery { smsMsgDao.getByFingerprint("1068", "code 123456", 100L, SmsMsg.MSG_TYPE_SMS) } returns existing
        var updatedArg: SmsMsg? = null
        coEvery { smsMsgDao.update(any()) } coAnswers {
            updatedArg = firstArg<SmsMsg>()
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        facade.persistSmsForwardResult(
            smsMsg = existing,
            success = false,
            target = "SmsCode Engine",
            message = "IPC token missing",
        )

        val updated = requireNotNull(updatedArg)
        assertEquals(7L, updated.id)
        assertEquals(SmsMsg.FORWARD_STATUS_FAILED, updated.forwardStatus)
        assertEquals("SmsCode Engine", updated.forwardTarget)
        assertEquals("IPC token missing", updated.forwardMessage)
        assertTrue(updated.forwardTime > 0L)
    }

    @Test
    fun persistSmsHookDispatchFailure_usesDefaultTargetAndFailedStatus() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val existing = runtimeSmsMsg(
            id = 9L,
            sender = "1068",
            body = "code 123456",
            date = 100L,
        )
        coEvery { smsMsgDao.getByFingerprint("1068", "code 123456", 100L, SmsMsg.MSG_TYPE_SMS) } returns existing
        var updatedArg: SmsMsg? = null
        coEvery { smsMsgDao.update(any()) } coAnswers {
            updatedArg = firstArg<SmsMsg>()
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        facade.persistSmsHookDispatchFailure(
            smsMsg = existing,
            message = "IPC token missing",
        )

        val updated = requireNotNull(updatedArg)
        assertEquals(SmsMsg.FORWARD_STATUS_FAILED, updated.forwardStatus)
        assertEquals("SmsCode Engine", updated.forwardTarget)
        assertEquals("IPC token missing", updated.forwardMessage)
    }

    @Test
    fun persistSmsForwardResult_insertsWhenRecordMissing() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val smsMsg = runtimeSmsMsg()
        coEvery { smsMsgDao.getByFingerprint("1068", "code 123456", 100L, SmsMsg.MSG_TYPE_SMS) } returns null
        var insertedArg: SmsMsg? = null
        coEvery { smsMsgDao.insert(any()) } coAnswers {
            insertedArg = firstArg<SmsMsg>()
            1L
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        facade.persistSmsForwardResult(
            smsMsg = smsMsg,
            success = true,
            target = "SmsCode Engine",
            message = "ok",
        )

        val inserted = requireNotNull(insertedArg)
        assertEquals(SmsMsg.FORWARD_STATUS_SUCCESS, inserted.forwardStatus)
        assertEquals("SmsCode Engine", inserted.forwardTarget)
        assertEquals("ok", inserted.forwardMessage)
        assertEquals("123456", inserted.smsCode)
    }

    @Test
    fun duplicateQueries_delegateToSmsMsgDao() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val hit = runtimeSmsMsg(id = 1L)
        every { smsMsgDao.getByFingerprintInRange("1068", "code 123456", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit
        every { smsMsgDao.getByCodeAndPackageInRange("123456", "com.bank.app", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit
        every { smsMsgDao.getByCodeAndCompanyInRange("123456", "Bank", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit

        val facade = RuntimeRecordFacade(context = context, db = database)

        assertTrue(facade.hasSmsDuplicateInRange("1068", "code 123456", 90L, 110L))
        assertTrue(facade.hasSmsCodeDuplicateByPackageInRange("123456", "com.bank.app", 90L, 110L))
        assertTrue(facade.hasSmsCodeDuplicateByCompanyInRange("123456", "Bank", 90L, 110L))
    }

    @Test
    fun backfillSmsRouting_updatesExactMissingRoutingRecord() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        val existing = runtimeSmsMsg(
            id = 11L,
            sender = "1068",
            body = "code 123456",
            date = 100L,
        ).copy(simSlot = -1, subId = 0)
        coEvery { smsMsgDao.getAll() } returns listOf(existing)
        var updatedArg: SmsMsg? = null
        coEvery { smsMsgDao.update(any()) } coAnswers {
            updatedArg = firstArg<SmsMsg>()
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        val updated = facade.backfillSmsRouting(
            sender = "1068",
            body = "code 123456",
            date = 105L,
            simSlot = 1,
            subId = 12,
            msgType = SmsMsg.MSG_TYPE_SMS,
            windowMs = 30_000L,
        )

        assertTrue(updated)
        val record = requireNotNull(updatedArg)
        assertEquals(11L, record.id)
        assertEquals(1, record.simSlot)
        assertEquals(12, record.subId)
    }

    @Test
    fun backfillSmsRouting_skipsAmbiguousTimeFallback() = runBlocking {
        val context = relaxedContext()
        val (database, smsMsgDao) = smsMsgDatabaseFixture()
        coEvery { smsMsgDao.getAll() } returns listOf(
            runtimeSmsMsg(id = 21L, sender = "10010", body = "code 111111", date = 100L)
                .copy(simSlot = -1, subId = 0),
            runtimeSmsMsg(id = 22L, sender = "10086", body = "code 222222", date = 101L)
                .copy(simSlot = -1, subId = 0),
        )
        var updatedArg: SmsMsg? = null
        coEvery { smsMsgDao.update(any()) } coAnswers {
            updatedArg = firstArg<SmsMsg>()
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        val updated = facade.backfillSmsRouting(
            sender = "1068",
            body = "code 123456",
            date = 100L,
            simSlot = 0,
            subId = 1,
            msgType = SmsMsg.MSG_TYPE_SMS,
            windowMs = 30_000L,
        )

        assertFalse(updated)
        assertNull(updatedArg)
    }
}
