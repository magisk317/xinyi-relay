package com.github.magisk317.smscode.forwarder.utils

import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.entity.setting.BarkSetting
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkGroupRobotSetting
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkInnerRobotSetting
import com.github.magisk317.smscode.forwarder.entity.setting.EmailSetting
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuAppSetting
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuSetting
import com.github.magisk317.smscode.forwarder.entity.setting.GotifySetting
import com.github.magisk317.smscode.forwarder.entity.setting.PushplusSetting
import com.github.magisk317.smscode.forwarder.entity.setting.ServerchanSetting
import com.github.magisk317.smscode.forwarder.entity.setting.SmsSetting
import com.github.magisk317.smscode.forwarder.entity.setting.SocketSetting
import com.github.magisk317.smscode.forwarder.entity.setting.TelegramSetting
import com.github.magisk317.smscode.forwarder.entity.setting.UrlSchemeSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WebhookSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkAgentSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkRobotSetting
import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class SenderSettingSanitizerTest {
    private val gson = Gson()

    @Test
    fun sanitizeSenderLenient_handlesNullFieldsForAllChannels() {
        val dirtyCases = listOf(
            SenderType.DINGTALK_GROUP_ROBOT to """{"token":null,"msgtype":null}""",
            SenderType.EMAIL to """{"mailType":null,"fromEmail":null,"recipients":null,"toEmail":null}""",
            SenderType.BARK to """{"server":null,"level":null}""",
            SenderType.WEBHOOK to """{"method":null,"webServer":null,"headers":null,"webParams":null,"proxyType":null}""",
            SenderType.WEWORK_ROBOT to """{"webHook":null,"msgType":null}""",
            SenderType.WEWORK_AGENT to """{"corpID":null,"agentID":null,"secret":null,"proxyType":null}""",
            SenderType.SERVERCHAN to """{"sendKey":null}""",
            SenderType.PUSHPLUS to """{"website":null,"token":null}""",
            SenderType.TELEGRAM to """{"method":null,"apiToken":null,"chatId":null,"parseMode":null,"proxyType":null}""",
            SenderType.SMS to """{"mobiles":null,"simSlot":null}""",
            SenderType.FEISHU to """{"webhook":null,"msgType":null}""",
            SenderType.GOTIFY to """{"webServer":null,"title":null}""",
            SenderType.DINGTALK_INNER_ROBOT to """{"agentID":null,"appKey":null,"appSecret":null,"proxyType":null}""",
            SenderType.FEISHU_APP to """{"appId":null,"appSecret":null,"receiveId":null,"msgType":null}""",
            SenderType.URL_SCHEME to """{"urlScheme":null}""",
            SenderType.SOCKET to """{"method":null,"address":null,"port":null,"uriType":null,"outCharset":null}""",
        )

        dirtyCases.forEach { (type, dirtyJson) ->
            val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(newSender(type, dirtyJson))
            assertNotNull(sanitized.jsonSetting)
            assertFalse(sanitized.jsonSetting.isBlank())
            assertNoDangerousNullAccess(type, sanitized.jsonSetting)
        }
    }

    @Test
    fun sanitizeSenderLenient_webhookHeadersNull_becomesEmptyMap() {
        val sender = newSender(
            SenderType.WEBHOOK,
            """{"method":"POST","webServer":"https://example.com","headers":null}""",
        )
        val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val setting = gson.fromJson(sanitized.jsonSetting, WebhookSetting::class.java)
        assertTrue(setting.headers.isEmpty())
    }

    @Test
    fun sanitizeSenderLenient_emailNullFields_areSafeForValidation() {
        val sender = newSender(
            SenderType.EMAIL,
            """{"mailType":null,"fromEmail":null,"recipients":null,"toEmail":null}""",
        )
        val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val setting = gson.fromJson(sanitized.jsonSetting, EmailSetting::class.java)
        assertEquals("", setting.mailType)
        assertTrue(setting.recipients.isEmpty())
        val result = SenderValidator.validateForEnable(sanitized)
        assertFalse(result.valid)
    }

    @Test
    fun sanitizeJsonLenient_invalidJson_fallsBackToDefaults() {
        val sanitized = SenderSettingSanitizer.sanitizeJsonLenient(SenderType.WEBHOOK, "{broken")
        val setting = gson.fromJson(sanitized, WebhookSetting::class.java)
        assertEquals("POST", setting.method)
        assertTrue(setting.headers.isEmpty())
    }

    private fun newSender(type: Int, json: String): Sender {
        return Sender(
            id = 1L,
            type = type,
            name = "sender-$type",
            jsonSetting = json,
            status = 1,
            time = Date(),
            receiveCode = 1,
            receiveNonCode = 1,
            receiveAppNotify = 1,
        )
    }

    private fun assertNoDangerousNullAccess(type: Int, json: String) {
        when (type) {
            SenderType.DINGTALK_GROUP_ROBOT -> {
                val setting = gson.fromJson(json, DingtalkGroupRobotSetting::class.java)
                setting.token.length
                setting.msgtype.length
            }
            SenderType.EMAIL -> {
                val setting = gson.fromJson(json, EmailSetting::class.java)
                setting.mailType.length
                setting.fromEmail.length
                setting.toEmail.length
                setting.recipients.isEmpty()
            }
            SenderType.BARK -> {
                val setting = gson.fromJson(json, BarkSetting::class.java)
                setting.server.length
                setting.level.length
            }
            SenderType.WEBHOOK -> {
                val setting = gson.fromJson(json, WebhookSetting::class.java)
                setting.method.length
                setting.webServer.length
                setting.webParams.length
                setting.proxyType.name.length
                setting.headers.isEmpty()
            }
            SenderType.WEWORK_ROBOT -> {
                val setting = gson.fromJson(json, WeworkRobotSetting::class.java)
                setting.webHook.length
                setting.msgType.length
            }
            SenderType.WEWORK_AGENT -> {
                val setting = gson.fromJson(json, WeworkAgentSetting::class.java)
                setting.corpID.length
                setting.agentID.length
                setting.secret.length
                setting.proxyType.name.length
            }
            SenderType.SERVERCHAN -> {
                val setting = gson.fromJson(json, ServerchanSetting::class.java)
                setting.sendKey.length
            }
            SenderType.PUSHPLUS -> {
                val setting = gson.fromJson(json, PushplusSetting::class.java)
                setting.website.length
                setting.token.length
            }
            SenderType.TELEGRAM -> {
                val setting = gson.fromJson(json, TelegramSetting::class.java)
                setting.method.length
                setting.apiToken.length
                setting.chatId.length
                setting.parseMode.length
                setting.proxyType.name.length
            }
            SenderType.SMS -> {
                val setting = gson.fromJson(json, SmsSetting::class.java)
                setting.mobiles.length
                setting.simSlot.toString().length
            }
            SenderType.FEISHU -> {
                val setting = gson.fromJson(json, FeishuSetting::class.java)
                setting.webhook.length
                setting.msgType.length
            }
            SenderType.GOTIFY -> {
                val setting = gson.fromJson(json, GotifySetting::class.java)
                setting.webServer.length
                setting.title.length
            }
            SenderType.DINGTALK_INNER_ROBOT -> {
                val setting = gson.fromJson(json, DingtalkInnerRobotSetting::class.java)
                setting.agentID.length
                setting.appKey.length
                setting.appSecret.length
                setting.proxyType.name.length
            }
            SenderType.FEISHU_APP -> {
                val setting = gson.fromJson(json, FeishuAppSetting::class.java)
                setting.appId.length
                setting.appSecret.length
                setting.receiveId.length
                setting.msgType.length
            }
            SenderType.URL_SCHEME -> {
                val setting = gson.fromJson(json, UrlSchemeSetting::class.java)
                setting.urlScheme.length
            }
            SenderType.SOCKET -> {
                val setting = gson.fromJson(json, SocketSetting::class.java)
                setting.method.length
                setting.address.length
                setting.uriType.length
                setting.outCharset.length
            }
        }
    }
}
