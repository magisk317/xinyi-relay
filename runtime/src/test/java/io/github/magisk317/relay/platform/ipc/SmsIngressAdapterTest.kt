package io.github.magisk317.relay.platform.ipc

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import dev.mokkery.MockMode.autofill
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.answering.returns
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.data.db.entity.SmsMsg
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsIngressAdapterTest {

    @Test
    fun toPayload_returnsCodeMessageWithResolvedPackage() = runBlocking {
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val packageManager = mock<PackageManager>(autofill)
        val appInfo = ApplicationInfo().apply {
            packageName = "com.bank.app"
        }
        val smsMsg = SmsMsg(
            sender = "1068",
            body = "【Bank】code 123456",
            date = 123L,
        )

        every { phoneContext.packageManager } returns packageManager
        every { packageManager.getInstalledApplications(PackageManager.MATCH_ALL) } returns listOf(appInfo)
        every { packageManager.getApplicationLabel(appInfo) } returns "Bank"

        val result = SmsIngressAdapter.toPayload(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = null,
            eventId = "sms_test",
            smsCodeParser = { _, body ->
                if (body == "【Bank】code 123456") "123456" else ""
            },
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
        val pluginContext = mock<Context>(autofill)
        val phoneContext = mock<Context>(autofill)
        val smsMsg = SmsMsg(
            sender = "service",
            body = "hello world",
            date = 0L,
        )

        val result = SmsIngressAdapter.toPayload(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            sourceIntent = null,
            eventId = "sms_plain",
            smsCodeParser = { _, _ -> "" },
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
        val phoneContext = mock<Context>(autofill)
        val packageManager = mock<PackageManager>(autofill)
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
