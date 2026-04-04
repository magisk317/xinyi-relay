package io.github.magisk317.relay.domain.system

import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.dao.SmsMsgDao
import io.github.magisk317.relay.data.db.entity.SmsMsg
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeRecordFacadeTest {

    @Test
    fun insertSmsRecord_delegatesToRelayRecordRepository() = runBlocking {
        val context = mock<Context>(autofill)
        val database = mock<AppDatabase>(autofill)
        val smsMsg = SmsMsg(
            sender = "1068",
            body = "code 123456",
            date = 100L,
            company = "Bank",
            smsCode = "123456",
            packageName = "com.bank.app",
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
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
    fun persistSmsForwardResult_updatesExistingRecord() = runBlocking {
        val context = mock<Context>(autofill)
        val database = mock<AppDatabase>(autofill)
        val smsMsgDao = mock<SmsMsgDao>(autofill)
        val existing = SmsMsg(
            id = 7L,
            sender = "1068",
            body = "code 123456",
            date = 100L,
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
        every { database.smsMsgDao() } returns smsMsgDao
        every { smsMsgDao.getByFingerprint("1068", "code 123456", 100L, SmsMsg.MSG_TYPE_SMS) } returns existing
        var updatedArg: SmsMsg? = null
        every { smsMsgDao.update(any()) } calls { args ->
            updatedArg = args.arg<SmsMsg>(0)
            Unit
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        facade.persistSmsForwardResult(
            smsMsg = existing,
            success = false,
            target = "SmsCode Engine",
            message = "IPC token missing",
        )

        verify(exactly(1)) { smsMsgDao.update(any()) }
        val updated = requireNotNull(updatedArg)
        assertEquals(7L, updated.id)
        assertEquals(SmsMsg.FORWARD_STATUS_FAILED, updated.forwardStatus)
        assertEquals("SmsCode Engine", updated.forwardTarget)
        assertEquals("IPC token missing", updated.forwardMessage)
        assertTrue(updated.forwardTime > 0L)
        verify(exactly(0)) { smsMsgDao.insert(any()) }
    }

    @Test
    fun persistSmsHookDispatchFailure_usesDefaultTargetAndFailedStatus() = runBlocking {
        val context = mock<Context>(autofill)
        val database = mock<AppDatabase>(autofill)
        val smsMsgDao = mock<SmsMsgDao>(autofill)
        val existing = SmsMsg(
            id = 9L,
            sender = "1068",
            body = "code 123456",
            date = 100L,
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
        every { database.smsMsgDao() } returns smsMsgDao
        every { smsMsgDao.getByFingerprint("1068", "code 123456", 100L, SmsMsg.MSG_TYPE_SMS) } returns existing
        var updatedArg: SmsMsg? = null
        every { smsMsgDao.update(any()) } calls { args ->
            updatedArg = args.arg<SmsMsg>(0)
            Unit
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        facade.persistSmsHookDispatchFailure(
            smsMsg = existing,
            message = "IPC token missing",
        )

        verify(exactly(1)) { smsMsgDao.update(any()) }
        val updated = requireNotNull(updatedArg)
        assertEquals(SmsMsg.FORWARD_STATUS_FAILED, updated.forwardStatus)
        assertEquals("SmsCode Engine", updated.forwardTarget)
        assertEquals("IPC token missing", updated.forwardMessage)
    }

    @Test
    fun persistSmsForwardResult_insertsWhenRecordMissing() = runBlocking {
        val context = mock<Context>(autofill)
        val database = mock<AppDatabase>(autofill)
        val smsMsgDao = mock<SmsMsgDao>(autofill)
        val smsMsg = SmsMsg(
            sender = "1068",
            body = "code 123456",
            date = 100L,
            company = "Bank",
            smsCode = "123456",
            packageName = "com.bank.app",
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
        every { database.smsMsgDao() } returns smsMsgDao
        every { smsMsgDao.getByFingerprint("1068", "code 123456", 100L, SmsMsg.MSG_TYPE_SMS) } returns null
        var insertedArg: SmsMsg? = null
        every { smsMsgDao.insert(any()) } calls { args ->
            insertedArg = args.arg<SmsMsg>(0)
            1L
        }

        val facade = RuntimeRecordFacade(context = context, db = database)
        facade.persistSmsForwardResult(
            smsMsg = smsMsg,
            success = true,
            target = "SmsCode Engine",
            message = "ok",
        )

        verify(exactly(1)) { smsMsgDao.insert(any()) }
        val inserted = requireNotNull(insertedArg)
        assertEquals(SmsMsg.FORWARD_STATUS_SUCCESS, inserted.forwardStatus)
        assertEquals("SmsCode Engine", inserted.forwardTarget)
        assertEquals("ok", inserted.forwardMessage)
        assertEquals("123456", inserted.smsCode)
        verify(exactly(0)) { smsMsgDao.update(any()) }
    }

    @Test
    fun duplicateQueries_delegateToSmsMsgDao() = runBlocking {
        val context = mock<Context>(autofill)
        val database = mock<AppDatabase>(autofill)
        val smsMsgDao = mock<SmsMsgDao>(autofill)
        val hit = SmsMsg(id = 1L, sender = "1068", body = "code 123456", date = 100L)
        every { database.smsMsgDao() } returns smsMsgDao
        every { smsMsgDao.getByFingerprintInRange("1068", "code 123456", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit
        every { smsMsgDao.getByCodeAndPackageInRange("123456", "com.bank.app", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit
        every { smsMsgDao.getByCodeAndCompanyInRange("123456", "Bank", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit

        val facade = RuntimeRecordFacade(context = context, db = database)

        assertTrue(facade.hasSmsDuplicateInRange("1068", "code 123456", 90L, 110L))
        assertTrue(facade.hasSmsCodeDuplicateByPackageInRange("123456", "com.bank.app", 90L, 110L))
        assertTrue(facade.hasSmsCodeDuplicateByCompanyInRange("123456", "Bank", 90L, 110L))
    }
}
