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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class SenderSettingSanitizerTest {

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
            SenderType.TELEGRAM to """{"method":null,"apiBase":null,"apiToken":null,"chatId":null,"parseMode":null,"proxyType":null}""",
            SenderType.SMS to """{"mobiles":null,"simSlot":null}""",
            SenderType.FEISHU to """{"webhook":null,"msgType":null}""",
            SenderType.GOTIFY to """{"webServer":null,"title":null}""",
            SenderType.NTFY to """{"server":null,"topic":null,"priority":null,"tags":null}""",
            SenderType.DINGTALK_INNER_ROBOT to """{"agentID":null,"appKey":null,"appSecret":null,"proxyType":null}""",
            SenderType.FEISHU_APP to """{"appId":null,"appSecret":null,"receiveId":null,"msgType":null}""",
            SenderType.URL_SCHEME to """{"urlScheme":null}""",
            SenderType.SOCKET to """{"method":null,"address":null,"port":null,"uriType":null,"outCharset":null}""",
            SenderType.YUNHU to """{"token":null,"recvId":null,"recvType":null,"contentType":null}""",
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
        val setting = SenderSettingJson.decode<WebhookSetting>(sanitized.jsonSetting)
        assertTrue(setting.headers.isEmpty())
    }

    @Test
    fun sanitizeSenderLenient_webhookInvalidFieldTypes_preservesCoreSecrets() {
        val sender = newSender(
            SenderType.WEBHOOK,
            """{"method":"POST","webServer":"https://example.com/hook","secret":"signing-key","headers":"bad","proxyType":{"bad":true}}""",
        )

        val sanitized = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val setting = SenderSettingJson.decode<WebhookSetting>(sanitized.jsonSetting)

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
        val setting = SenderSettingJson.decode<EmailSetting>(sanitized.jsonSetting)
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
        val setting = SenderSettingJson.decode<EmailSetting>(sanitized.jsonSetting)

        assertEquals("relay@example.com", setting.authEmail)
        assertEquals("Android relay", setting.fromEmailAlias)
        assertEquals("Android relay", setting.nickname)
    }

    @Test
    fun sanitizeJsonLenient_emailR8ObfuscatedJson_preservesChannelParams() {
        val raw = """{"A":"keystore.p12","B":"cert-pass","C":"Plain","D":"Relay Bot","o":"SMTP","p":"auth@example.com","q":"from@example.com","r":"mail-pass","s":"Bot","t":"smtp.example.com","u":"465","v":true,"w":true,"x":"Message title","y":{},"z":"to@example.com"}"""

        val sanitized = SenderSettingSanitizer.sanitizeJsonLenient(SenderType.EMAIL, raw)
        val setting = SenderSettingJson.decode<EmailSetting>(sanitized)

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
        val setting = SenderSettingJson.decode<WebhookSetting>(sanitized)

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
        val setting = SenderSettingJson.decode<WeworkAgentSetting>(sanitized.jsonSetting)

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
        val setting = SenderSettingJson.decode<WebhookSetting>(sanitized)
        assertEquals("POST", setting.method)
        assertTrue(setting.headers.isEmpty())
    }

    @Test
    fun sanitizeJsonLenient_r8ObfuscatedJson_preservesAllChannelFields() {
        val dingtalk = SenderSettingJson.decode<DingtalkGroupRobotSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.DINGTALK_GROUP_ROBOT,
                """{"o":"ding-token","p":"ding-secret","q":true,"r":"13800000000","s":"user-a","t":"markdown","u":"title"}""",
            ),
        )
        assertEquals("ding-token", dingtalk.token)
        assertEquals("ding-secret", dingtalk.secret)
        assertTrue(dingtalk.atAll)
        assertEquals("markdown", dingtalk.msgtype)

        val barkJson = SenderSettingSanitizer.sanitizeJsonLenient(
            SenderType.BARK,
            """{"o":"https://api.day.app/key","p":"group","q":"https://example.com/icon.png","r":"bell","s":"1","t":"https://example.com","u":"active","v":"title","w":"none","x":"bark-secret","y":"bark-iv","z":"1","A":"copy"}""",
        )
        val bark = SenderSettingJson.decode<BarkSetting>(barkJson)
        assertEquals("https://api.day.app/key", bark.server)
        assertEquals("group", bark.group)
        assertEquals("https://example.com", bark.url)
        assertEquals("none", bark.transformation)
        assertEquals("copy", bark.autoCopy)
        assertNoAliasKeys(barkJson, "o", "p", "A")

        val weworkRobot = SenderSettingJson.decode<WeworkRobotSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.WEWORK_ROBOT,
                """{"o":"https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc","p":"markdown","q":true,"r":"user-a","s":"13800000000"}""",
            ),
        )
        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc", weworkRobot.webHook)
        assertEquals("markdown", weworkRobot.msgType)
        assertTrue(weworkRobot.atAll)

        val weworkAgent = SenderSettingJson.decode<WeworkAgentSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.WEWORK_AGENT,
                """{"o":"ww123456","p":"1000001","q":"corp-secret","r":true,"s":"@all","t":"party","u":"tag","v":"HTTP","w":"127.0.0.1","x":"8080","y":true,"z":"proxy-user","A":"proxy-pass","B":"https://qyapi.weixin.qq.com"}""",
            ),
        )
        assertEquals("ww123456", weworkAgent.corpID)
        assertEquals("1000001", weworkAgent.agentID)
        assertEquals("corp-secret", weworkAgent.secret)
        assertEquals(java.net.Proxy.Type.HTTP, weworkAgent.proxyType)
        assertEquals("https://qyapi.weixin.qq.com", weworkAgent.customizeAPI)

        val serverchan = SenderSettingJson.decode<ServerchanSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.SERVERCHAN,
                """{"o":"send-key","p":"9","q":"openid","r":"title"}""",
            ),
        )
        assertEquals("send-key", serverchan.sendKey)
        assertEquals("9", serverchan.channel)
        assertEquals("openid", serverchan.openid)

        val pushplus = SenderSettingJson.decode<PushplusSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.PUSHPLUS,
                """{"o":"www.pushplus.plus","p":"push-token","q":"topic","r":"html","s":"wechat","t":"https://example.com/webhook","u":"https://example.com/callback","v":"10","w":"title"}""",
            ),
        )
        assertEquals("www.pushplus.plus", pushplus.website)
        assertEquals("push-token", pushplus.token)
        assertEquals("https://example.com/webhook", pushplus.webhook)
        assertEquals("10", pushplus.validTime)

        val telegram = SenderSettingJson.decode<TelegramSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.TELEGRAM,
                """{"o":"POST","p":"123456:abcdefghijklmnopqrstuvwxyz","q":"-100123456","r":"7","s":"SOCKS","t":"127.0.0.1","u":"1080","v":true,"w":"proxy-user","x":"proxy-pass","y":"MarkdownV2","z":"https://telegram.example.com"}""",
            ),
        )
        assertEquals("POST", telegram.method)
        assertEquals("https://telegram.example.com", telegram.apiBase)
        assertEquals("123456:abcdefghijklmnopqrstuvwxyz", telegram.apiToken)
        assertEquals("-100123456", telegram.chatId)
        assertEquals("7", telegram.messageThreadId)
        assertEquals(java.net.Proxy.Type.SOCKS, telegram.proxyType)
        assertEquals("MarkdownV2", telegram.parseMode)

        val sms = SenderSettingJson.decode<SmsSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.SMS,
                """{"o":1,"p":"13800000000","q":true}""",
            ),
        )
        assertEquals(1, sms.simSlot)
        assertEquals("13800000000", sms.mobiles)
        assertTrue(sms.onlyNoNetwork)

        val feishu = SenderSettingJson.decode<FeishuSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.FEISHU,
                """{"o":"https://open.feishu.cn/open-apis/bot/v2/hook/abc","p":"feishu-secret","q":"text","r":"title","s":"{}"}""",
            ),
        )
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/abc", feishu.webhook)
        assertEquals("feishu-secret", feishu.secret)
        assertEquals("text", feishu.msgType)

        val gotify = SenderSettingJson.decode<GotifySetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.GOTIFY,
                """{"o":"https://gotify.example.com","p":"title","q":"5"}""",
            ),
        )
        assertEquals("https://gotify.example.com", gotify.webServer)
        assertEquals("5", gotify.priority)

        val ntfy = SenderSettingJson.decode<NtfySetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.NTFY,
                """{"o":"https://ntfy.sh","p":"topic","q":"bearer-token","r":"title","s":"4","t":"tag1,tag2"}""",
            ),
        )
        assertEquals("https://ntfy.sh", ntfy.server)
        assertEquals("topic", ntfy.topic)
        assertEquals("bearer-token", ntfy.token)
        assertEquals("4", ntfy.priority)

        val dingtalkInner = SenderSettingJson.decode<DingtalkInnerRobotSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.DINGTALK_INNER_ROBOT,
                """{"o":"1000001","p":"ding-app-key","q":"ding-app-secret","r":"user-a","s":"sampleMarkdown","t":"title","u":"HTTP","v":"127.0.0.1","w":"8080","x":true,"y":"proxy-user","z":"proxy-pass"}""",
            ),
        )
        assertEquals("1000001", dingtalkInner.agentID)
        assertEquals("ding-app-key", dingtalkInner.appKey)
        assertEquals("ding-app-secret", dingtalkInner.appSecret)
        assertEquals("sampleMarkdown", dingtalkInner.msgKey)
        assertEquals(java.net.Proxy.Type.HTTP, dingtalkInner.proxyType)

        val feishuApp = SenderSettingJson.decode<FeishuAppSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.FEISHU_APP,
                """{"o":"cli_a123","p":"app-secret","q":"receive-id","r":"text","s":"title","t":"chat_id","u":"{}"}""",
            ),
        )
        assertEquals("cli_a123", feishuApp.appId)
        assertEquals("app-secret", feishuApp.appSecret)
        assertEquals("receive-id", feishuApp.receiveId)
        assertEquals("chat_id", feishuApp.receiveIdType)

        val urlScheme = SenderSettingJson.decode<UrlSchemeSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.URL_SCHEME,
                """{"o":"relay://send?text=[msg]"}""",
            ),
        )
        assertEquals("relay://send?text=[msg]", urlScheme.urlScheme)

        val socket = SenderSettingJson.decode<SocketSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.SOCKET,
                """{"o":"MQTT","p":"mqtt.example.com","q":1883,"r":"{\"msg\":\"[msg]\"}","s":"secret","t":"ok","u":"user","v":"pass","w":"UTF-8","x":"UTF-8","y":"in/topic","z":"out/topic","A":"tcp","B":"/mqtt","C":"client","D":1,"E":true}""",
            ),
        )
        assertEquals("MQTT", socket.method)
        assertEquals("mqtt.example.com", socket.address)
        assertEquals(1883, socket.port)
        assertEquals("tcp", socket.uriType)
        assertEquals(1, socket.qos)
        assertTrue(socket.retained)
    }

    @Test
    fun sanitizeJsonLenient_shiftedTelegramCanonicalJson_repairsTokenAndChatId() {
        val raw = """{"method":"POST","apiToken":"POST","chatId":"123456:abcdefghijklmnopqrstuvwxyz","messageThreadId":"-100123456","parseMode":"MarkdownV2"}"""

        val sanitized = SenderSettingSanitizer.sanitizeJsonLenient(SenderType.TELEGRAM, raw)
        val setting = SenderSettingJson.decode<TelegramSetting>(sanitized)

        assertEquals("POST", setting.method)
        assertEquals("https://api.telegram.org", setting.apiBase)
        assertEquals("123456:abcdefghijklmnopqrstuvwxyz", setting.apiToken)
        assertEquals("-100123456", setting.chatId)
        assertEquals("", setting.messageThreadId)
        assertEquals("MarkdownV2", setting.parseMode)
    }

    @Test
    fun sanitizeJsonLenient_shiftedFeishuAndWebhookCanonicalJson_repairsUrlFields() {
        val feishu = SenderSettingJson.decode<FeishuSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.FEISHU,
                """{"webhook":"","secret":"https://open.feishu.cn/open-apis/bot/v2/hook/abc","msgType":"feishu-secret","titleTemplate":"title"}""",
            ),
        )
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/abc", feishu.webhook)
        assertEquals("feishu-secret", feishu.secret)
        assertEquals("interactive", feishu.msgType)

        val webhook = SenderSettingJson.decode<WebhookSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(
                SenderType.WEBHOOK,
                """{"method":"POST","webServer":"","secret":"https://example.com/hook","response":"signing-key","webParams":"a=1"}""",
            ),
        )
        assertEquals("POST", webhook.method)
        assertEquals("https://example.com/hook", webhook.webServer)
        assertEquals("signing-key", webhook.secret)
        assertEquals("", webhook.response)
    }

    @Test
    fun sanitizeJsonLenient_plainTextFields_areNotMovedAsSecrets() {
        val raw = """{"webhook":"","secret":"normal title with spaces","msgType":"text","titleTemplate":"https://example.com/not-a-webhook"}"""

        val setting = SenderSettingJson.decode<FeishuSetting>(
            SenderSettingSanitizer.sanitizeJsonLenient(SenderType.FEISHU, raw),
        )

        assertEquals("", setting.webhook)
        assertEquals("normal title with spaces", setting.secret)
        assertEquals("text", setting.msgType)
        assertEquals("https://example.com/not-a-webhook", setting.titleTemplate)
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

    private fun assertNoAliasKeys(json: String, vararg aliases: String) {
        val obj = SenderSettingJson.parseObject(json) ?: error("Expected JSON object: $json")
        aliases.forEach { alias ->
            assertFalse(alias in obj, "Unexpected obfuscated key '$alias' in $json")
        }
    }

    private fun assertNoDangerousNullAccess(type: Int, json: String) {
        when (type) {
            SenderType.DINGTALK_GROUP_ROBOT -> {
                val setting = SenderSettingJson.decode<DingtalkGroupRobotSetting>(json)
                setting.token.length
                setting.msgtype.length
            }
            SenderType.EMAIL -> {
                val setting = SenderSettingJson.decode<EmailSetting>(json)
                setting.mailType.length
                setting.fromEmail.length
                setting.toEmail.length
                setting.recipients.isEmpty()
            }
            SenderType.BARK -> {
                val setting = SenderSettingJson.decode<BarkSetting>(json)
                setting.server.length
                setting.level.length
            }
            SenderType.WEBHOOK -> {
                val setting = SenderSettingJson.decode<WebhookSetting>(json)
                setting.method.length
                setting.webServer.length
                setting.webParams.length
                setting.proxyType.name.length
                setting.headers.isEmpty()
            }
            SenderType.WEWORK_ROBOT -> {
                val setting = SenderSettingJson.decode<WeworkRobotSetting>(json)
                setting.webHook.length
                setting.msgType.length
            }
            SenderType.WEWORK_AGENT -> {
                val setting = SenderSettingJson.decode<WeworkAgentSetting>(json)
                setting.corpID.length
                setting.agentID.length
                setting.secret.length
                setting.proxyType.name.length
            }
            SenderType.SERVERCHAN -> {
                val setting = SenderSettingJson.decode<ServerchanSetting>(json)
                setting.sendKey.length
            }
            SenderType.PUSHPLUS -> {
                val setting = SenderSettingJson.decode<PushplusSetting>(json)
                setting.website.length
                setting.token.length
            }
            SenderType.TELEGRAM -> {
                val setting = SenderSettingJson.decode<TelegramSetting>(json)
                setting.method.length
                setting.apiBase.length
                setting.apiToken.length
                setting.chatId.length
                setting.parseMode.length
                setting.proxyType.name.length
            }
            SenderType.SMS -> {
                val setting = SenderSettingJson.decode<SmsSetting>(json)
                setting.mobiles.length
                setting.simSlot.toString().length
            }
            SenderType.FEISHU -> {
                val setting = SenderSettingJson.decode<FeishuSetting>(json)
                setting.webhook.length
                setting.msgType.length
            }
            SenderType.GOTIFY -> {
                val setting = SenderSettingJson.decode<GotifySetting>(json)
                setting.webServer.length
                setting.title.length
            }
            SenderType.NTFY -> {
                val setting = SenderSettingJson.decode<NtfySetting>(json)
                setting.server.length
                setting.topic.length
                setting.priority.length
                setting.tags.length
            }
            SenderType.DINGTALK_INNER_ROBOT -> {
                val setting = SenderSettingJson.decode<DingtalkInnerRobotSetting>(json)
                setting.agentID.length
                setting.appKey.length
                setting.appSecret.length
                setting.proxyType.name.length
            }
            SenderType.FEISHU_APP -> {
                val setting = SenderSettingJson.decode<FeishuAppSetting>(json)
                setting.appId.length
                setting.appSecret.length
                setting.receiveId.length
                setting.msgType.length
            }
            SenderType.URL_SCHEME -> {
                val setting = SenderSettingJson.decode<UrlSchemeSetting>(json)
                setting.urlScheme.length
            }
            SenderType.SOCKET -> {
                val setting = SenderSettingJson.decode<SocketSetting>(json)
                setting.method.length
                setting.address.length
                setting.uriType.length
                setting.outCharset.length
            }
        }
    }
}
