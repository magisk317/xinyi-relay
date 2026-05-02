package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderActiveSchedule
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleConst
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleRange
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleRule
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.config.BarkSetting
import io.github.magisk317.relay.sender.config.DingtalkGroupRobotSetting
import io.github.magisk317.relay.sender.config.DingtalkInnerRobotSetting
import io.github.magisk317.relay.sender.config.EmailSetting
import io.github.magisk317.relay.sender.config.FeishuAppSetting
import io.github.magisk317.relay.sender.config.FeishuSetting
import io.github.magisk317.relay.sender.config.GotifySetting
import io.github.magisk317.relay.sender.config.NtfySetting
import io.github.magisk317.relay.sender.config.PushplusSetting
import io.github.magisk317.relay.sender.config.ServerchanSetting
import io.github.magisk317.relay.sender.config.SmsSetting
import io.github.magisk317.relay.sender.config.SocketSetting
import io.github.magisk317.relay.sender.config.TelegramSetting
import io.github.magisk317.relay.sender.config.UrlSchemeSetting
import io.github.magisk317.relay.sender.config.WebhookSetting
import io.github.magisk317.relay.sender.config.WeworkAgentSetting
import io.github.magisk317.relay.sender.config.WeworkRobotSetting
import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
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
            SenderType.NTFY to """{"server":null,"topic":null,"priority":null,"tags":null}""",
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
    fun sanitizeSenderLenient_webhookInvalidFieldTypes_preservesCoreSecrets() {
        val sender = newSender(
            SenderType.WEBHOOK,
            """{"method":"POST","webServer":"https://example.com/hook","secret":"signing-key","headers":"bad","proxyType":{"bad":true}}""",
        )

        val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val setting = gson.fromJson(sanitized.jsonSetting, WebhookSetting::class.java)

        assertEquals("https://example.com/hook", setting.webServer)
        assertEquals("signing-key", setting.secret)
        assertTrue(setting.headers.isEmpty())
        assertEquals(java.net.Proxy.Type.DIRECT, setting.proxyType)
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
    fun sanitizeSenderLenient_emailFallsBackToVisibleAndLegacyAlias() {
        val sender = newSender(
            SenderType.EMAIL,
            """{"fromEmail":"relay@example.com","authEmail":"","nickname":"Android relay","fromEmailAlias":""}""",
        )

        val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val setting = gson.fromJson(sanitized.jsonSetting, EmailSetting::class.java)

        assertEquals("relay@example.com", setting.authEmail)
        assertEquals("Android relay", setting.fromEmailAlias)
        assertEquals("Android relay", setting.nickname)
    }

    @Test
    fun sanitizeJsonLenient_emailR8ObfuscatedJson_preservesChannelParams() {
        val raw = """{"A":"keystore.p12","B":"cert-pass","C":"Plain","D":"Relay Bot","o":"SMTP","p":"auth@example.com","q":"from@example.com","r":"mail-pass","s":"Bot","t":"smtp.example.com","u":"465","v":true,"w":true,"x":"Message title","y":{},"z":"to@example.com"}"""

        val sanitized = SenderSettingSanitizer.sanitizeJsonLenient(SenderType.EMAIL, raw)
        val setting = gson.fromJson(sanitized, EmailSetting::class.java)

        assertEquals("SMTP", setting.mailType)
        assertEquals("auth@example.com", setting.authEmail)
        assertEquals("from@example.com", setting.fromEmail)
        assertEquals("mail-pass", setting.pwd)
        assertEquals("smtp.example.com", setting.host)
        assertEquals("465", setting.port)
        assertTrue(setting.ssl)
        assertTrue(setting.startTls)
        assertEquals("Message title", setting.title)
        assertEquals("to@example.com", setting.toEmail)
        assertEquals("keystore.p12", setting.keystore)
        assertEquals("cert-pass", setting.password)
        assertEquals("Plain", setting.encryptionProtocol)
        assertEquals("Relay Bot", setting.fromEmailAlias)
        assertTrue(sanitized.contains(""""host":"smtp.example.com""""))
        assertFalse(sanitized.contains(""""t":"smtp.example.com""""))
    }

    @Test
    fun sanitizeJsonLenient_webhookR8ObfuscatedJson_preservesChannelParams() {
        val raw = """{"o":"POST","p":"https://example.com/hook","q":"signing-key","r":"ok","s":"a=1","t":{"X-Token":"token"},"u":"HTTP","v":"127.0.0.1","w":"8080","x":true,"y":"proxy-user","z":"proxy-pass"}"""

        val sanitized = SenderSettingSanitizer.sanitizeJsonLenient(SenderType.WEBHOOK, raw)
        val setting = gson.fromJson(sanitized, WebhookSetting::class.java)

        assertEquals("POST", setting.method)
        assertEquals("https://example.com/hook", setting.webServer)
        assertEquals("signing-key", setting.secret)
        assertEquals("ok", setting.response)
        assertEquals("a=1", setting.webParams)
        assertEquals("token", setting.headers["X-Token"])
        assertEquals(java.net.Proxy.Type.HTTP, setting.proxyType)
        assertEquals("127.0.0.1", setting.proxyHost)
        assertEquals("8080", setting.proxyPort)
        assertTrue(setting.proxyAuthenticator)
        assertEquals("proxy-user", setting.proxyUsername)
        assertEquals("proxy-pass", setting.proxyPassword)
    }

    @Test
    fun sanitizeSenderLenient_weworkAgentInvalidProxy_preservesSecretFields() {
        val sender = newSender(
            SenderType.WEWORK_AGENT,
            """{"corpID":"corp-id","agentID":"1000001","secret":"corp-secret","proxyType":{"bad":true},"proxyPort":["oops"]}""",
        )

        val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val setting = gson.fromJson(sanitized.jsonSetting, WeworkAgentSetting::class.java)

        assertEquals("corp-id", setting.corpID)
        assertEquals("1000001", setting.agentID)
        assertEquals("corp-secret", setting.secret)
        assertEquals(java.net.Proxy.Type.DIRECT, setting.proxyType)
        assertEquals("", setting.proxyPort)
    }

    @Test
    fun sanitizeSenderLenient_normalizesInvalidActiveSchedule() {
        val sender = newSender(SenderType.WEBHOOK, """{"method":"POST","webServer":"https://example.com"}""").copy(
            activeSchedule = SenderActiveSchedule(
                appNotify = SenderActiveScheduleRule(
                    enabled = true,
                    mode = "unknown",
                    weekdays = listOf(9),
                    ranges = listOf(
                        SenderActiveScheduleRange("bad", "18:00"),
                    ),
                ),
            ),
        )

        val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(sender)

        assertFalse(sanitized.activeSchedule.appNotify.enabled)
        assertEquals(SenderActiveScheduleConst.ALL_WEEKDAYS, sanitized.activeSchedule.appNotify.weekdays)
        assertTrue(sanitized.activeSchedule.appNotify.ranges.isEmpty())
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
            SenderType.NTFY -> {
                val setting = gson.fromJson(json, NtfySetting::class.java)
                setting.server.length
                setting.topic.length
                setting.priority.length
                setting.tags.length
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
