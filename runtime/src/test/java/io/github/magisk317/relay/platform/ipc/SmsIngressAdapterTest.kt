package io.github.magisk317.relay.platform.ipc

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.utils.SmsCodeUtils
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsIngressAdapterTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun toPayload_returnsCodeMessageWithResolvedPackage() = runBlocking {
        mockkObject(SmsCodeUtils)
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>()
        val sourceIntent = mockk<Intent>(relaxed = true)
        val appInfo = ApplicationInfo().apply {
            packageName = "com.bank.app"
        }
        val smsMsg = SmsMsg(
            sender = "1068",
            body = "【Bank】code 123456",
            date = 123L,
        )

        coEvery {
            SmsCodeUtils.parseSmsCodeIfExists(pluginContext, "【Bank】code 123456", null)
        } returns "123456"
        every { phoneContext.packageManager } returns packageManager
        every { packageManager.getInstalledApplications(PackageManager.MATCH_ALL) } returns listOf(appInfo)
        every { packageManager.getApplicationLabel(appInfo) } returns "Bank"

        val result = SmsIngressAdapter.toPayload(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = sourceIntent,
            eventId = "sms_test",
        )

        requireNotNull(result)
        assertEquals(MessageType.SMS_CODE, result.messageType)
        assertEquals("123456", result.smsMsg.smsCode)
        assertEquals("Bank", result.smsMsg.company)
        assertEquals("com.bank.app", result.smsMsg.packageName)
        assertEquals("sms_test", result.payload.eventId)
    }

    @Test
    fun toPayload_returnsPlainMessageWhenNoCodeMatched() = runBlocking {
        mockkObject(SmsCodeUtils)
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val sourceIntent = mockk<Intent>(relaxed = true)
        val smsMsg = SmsMsg(
            sender = "service",
            body = "hello world",
            date = 0L,
        )

        coEvery { SmsCodeUtils.parseSmsCodeIfExists(pluginContext, "hello world", null) } returns ""

        val result = SmsIngressAdapter.toPayload(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = sourceIntent,
            eventId = "sms_plain",
        )

        requireNotNull(result)
        assertEquals(MessageType.SMS_PLAIN, result.messageType)
        assertEquals("", result.smsMsg.company)
        assertNull(result.smsMsg.packageName)
        assertEquals("", result.smsMsg.smsCode)
        assertEquals("sms_plain", result.payload.eventId)
    }

    @Test
    fun enrichSmsMsg_usesProvidedCodeAndNormalizesDate() {
        val phoneContext = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>()
        val appInfo = ApplicationInfo().apply {
            packageName = "com.bank.app"
        }

        every { phoneContext.packageManager } returns packageManager
        every { packageManager.getInstalledApplications(PackageManager.MATCH_ALL) } returns listOf(appInfo)
        every { packageManager.getApplicationLabel(appInfo) } returns "Bank"

        val result = SmsIngressAdapter.enrichSmsMsg(
            phoneContext = phoneContext,
            smsMsg = SmsMsg(
                sender = "1068",
                body = "【Bank】code 123456",
                date = 0L,
            ),
            smsCode = "123456",
        )

        assertEquals("123456", result.smsCode)
        assertEquals("Bank", result.company)
        assertEquals("com.bank.app", result.packageName)
        assertTrue(result.date > 0L)
    }
}
