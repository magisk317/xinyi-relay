package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.dao.SmsMsgDao
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeRecordFacadeTest {

    @Test
    fun insertSmsRecord_delegatesToRelayRecordRepository() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val database = mockk<AppDatabase>(relaxed = true)
        val relayRecordRepository = mockk<RelayRecordRepository>()
        val smsMsg = SmsMsg(
            sender = "1068",
            body = "code 123456",
            date = 100L,
            company = "Bank",
            smsCode = "123456",
            packageName = "com.bank.app",
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
        coEvery { relayRecordRepository.insertRecord(smsMsg, true) } returns 42L

        val facade = RuntimeRecordFacade(context, database, relayRecordRepository)
        val recordId = facade.insertSmsRecord(smsMsg, isCodeSms = true)

        assertEquals(42L, recordId)
        coVerify(exactly = 1) { relayRecordRepository.insertRecord(smsMsg, true) }
    }

    @Test
    fun persistSmsForwardResult_updatesExistingRecord() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val database = mockk<AppDatabase>()
        val smsMsgDao = mockk<SmsMsgDao>(relaxed = true)
        val relayRecordRepository = mockk<RelayRecordRepository>(relaxed = true)
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
        every { smsMsgDao.update(any()) } answers {
            updatedArg = firstArg()
            Unit
        }

        val facade = RuntimeRecordFacade(context, database, relayRecordRepository)
        facade.persistSmsForwardResult(
            smsMsg = existing,
            success = false,
            target = "SmsCode Engine",
            message = "IPC token missing",
        )

        verify(exactly = 1) { smsMsgDao.update(any()) }
        requireNotNull(updatedArg)
        assertEquals(7L, updatedArg!!.id)
        assertEquals(SmsMsg.FORWARD_STATUS_FAILED, updatedArg!!.forwardStatus)
        assertEquals("SmsCode Engine", updatedArg!!.forwardTarget)
        assertEquals("IPC token missing", updatedArg!!.forwardMessage)
        assertTrue(updatedArg!!.forwardTime > 0L)
        verify(exactly = 0) { smsMsgDao.insert(any()) }
    }

    @Test
    fun persistSmsForwardResult_insertsWhenRecordMissing() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val database = mockk<AppDatabase>()
        val smsMsgDao = mockk<SmsMsgDao>(relaxed = true)
        val relayRecordRepository = mockk<RelayRecordRepository>(relaxed = true)
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
        every { smsMsgDao.insert(any()) } answers {
            insertedArg = firstArg()
            1L
        }

        val facade = RuntimeRecordFacade(context, database, relayRecordRepository)
        facade.persistSmsForwardResult(
            smsMsg = smsMsg,
            success = true,
            target = "SmsCode Engine",
            message = "ok",
        )

        verify(exactly = 1) { smsMsgDao.insert(any()) }
        requireNotNull(insertedArg)
        assertEquals(SmsMsg.FORWARD_STATUS_SUCCESS, insertedArg!!.forwardStatus)
        assertEquals("SmsCode Engine", insertedArg!!.forwardTarget)
        assertEquals("ok", insertedArg!!.forwardMessage)
        assertEquals("123456", insertedArg!!.smsCode)
        verify(exactly = 0) { smsMsgDao.update(any()) }
    }

    @Test
    fun duplicateQueries_delegateToSmsMsgDao() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val database = mockk<AppDatabase>()
        val smsMsgDao = mockk<SmsMsgDao>(relaxed = true)
        val relayRecordRepository = mockk<RelayRecordRepository>(relaxed = true)
        val hit = SmsMsg(id = 1L, sender = "1068", body = "code 123456", date = 100L)
        every { database.smsMsgDao() } returns smsMsgDao
        every { smsMsgDao.getByFingerprintInRange("1068", "code 123456", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit
        every { smsMsgDao.getByCodeAndPackageInRange("123456", "com.bank.app", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit
        every { smsMsgDao.getByCodeAndCompanyInRange("123456", "Bank", SmsMsg.MSG_TYPE_SMS, 90L, 110L) } returns hit

        val facade = RuntimeRecordFacade(context, database, relayRecordRepository)

        assertTrue(facade.hasSmsDuplicateInRange("1068", "code 123456", 90L, 110L))
        assertTrue(facade.hasSmsCodeDuplicateByPackageInRange("123456", "com.bank.app", 90L, 110L))
        assertTrue(facade.hasSmsCodeDuplicateByCompanyInRange("123456", "Bank", 90L, 110L))
    }
}
