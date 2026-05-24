package io.github.magisk317.relay.sender

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
import java.net.Proxy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SenderSettingDraftsTest {

    @Test
    fun fromJson_canonicalizesLegacyAliases() {
        val draft = SenderSettingDrafts.fromJson(
            SenderType.WEBHOOK,
            """{"o":"POST","p":"https://example.com/hook","q":"secret","t":{"X-Token":"abc"}}""",
        )

        assertEquals("POST", draft.string("method"))
        assertEquals("https://example.com/hook", draft.string("webServer"))
        assertEquals("secret", draft.string("secret"))
        assertEquals(mapOf("X-Token" to "abc"), draft.stringMap("headers"))
        assertTrue("webServer" in draft.toJson())
        assertTrue("\"p\"" !in draft.toJson())
    }

    @Test
    fun withField_updatesTypedValuesAndOutputsCanonicalJson() {
        val draft = SenderSettingDrafts.empty(SenderType.SMS)
            .withInt("simSlot", 1)
            .withString("mobiles", "13800000000")
            .withBoolean("onlyNoNetwork", true)

        assertEquals(1, draft.int("simSlot"))
        assertEquals("13800000000", draft.string("mobiles"))
        assertEquals(true, draft.boolean("onlyNoNetwork"))
        assertEquals("""{"simSlot":1,"mobiles":"13800000000","onlyNoNetwork":true}""", draft.toJson())
    }

    @Test
    fun draftJson_decodesAsExistingConfigModelsForMigratedForms() {
        val sms = SenderSettingJson.decode<SmsSetting>(
            SenderSettingDrafts.empty(SenderType.SMS)
                .withInt("simSlot", 1)
                .withString("mobiles", "13800000000")
                .withBoolean("onlyNoNetwork", true)
                .toJson(),
        )
        assertEquals(1, sms.simSlot)
        assertEquals("13800000000", sms.mobiles)
        assertEquals(true, sms.onlyNoNetwork)

        val gotify = SenderSettingJson.decode<GotifySetting>(
            SenderSettingDrafts.empty(SenderType.GOTIFY)
                .withString("webServer", "https://gotify.example.com")
                .withString("title", "Relay")
                .withString("priority", "5")
                .toJson(),
        )
        assertEquals("https://gotify.example.com", gotify.webServer)
        assertEquals("Relay", gotify.title)
        assertEquals("5", gotify.priority)

        val urlScheme = SenderSettingJson.decode<UrlSchemeSetting>(
            SenderSettingDrafts.empty(SenderType.URL_SCHEME)
                .withString("urlScheme", "relay://send?text=[msg]")
                .toJson(),
        )
        assertEquals("relay://send?text=[msg]", urlScheme.urlScheme)

        val dingtalk = SenderSettingJson.decode<DingtalkGroupRobotSetting>(
            SenderSettingDrafts.empty(SenderType.DINGTALK_GROUP_ROBOT)
                .withString("token", "ding-token")
                .withString("secret", "ding-secret")
                .withString("msgtype", "markdown")
                .withBoolean("atAll", true)
                .withString("titleTemplate", "Relay")
                .toJson(),
        )
        assertEquals("ding-token", dingtalk.token)
        assertEquals("ding-secret", dingtalk.secret)
        assertEquals("markdown", dingtalk.msgtype)
        assertEquals(true, dingtalk.atAll)
        assertEquals("Relay", dingtalk.titleTemplate)

        val pushplus = SenderSettingJson.decode<PushplusSetting>(
            SenderSettingDrafts.empty(SenderType.PUSHPLUS)
                .withString("website", "www.pushplus.plus")
                .withString("token", "push-token")
                .withString("topic", "topic")
                .withString("template", "html")
                .withString("channel", "wechat")
                .withString("titleTemplate", "Relay")
                .toJson(),
        )
        assertEquals("www.pushplus.plus", pushplus.website)
        assertEquals("push-token", pushplus.token)
        assertEquals("topic", pushplus.topic)
        assertEquals("html", pushplus.template)
        assertEquals("wechat", pushplus.channel)
        assertEquals("Relay", pushplus.titleTemplate)

        val serverchan = SenderSettingJson.decode<ServerchanSetting>(
            SenderSettingDrafts.empty(SenderType.SERVERCHAN)
                .withString("sendKey", "send-key")
                .withString("channel", "9")
                .withString("openid", "openid")
                .withString("titleTemplate", "Relay")
                .toJson(),
        )
        assertEquals("send-key", serverchan.sendKey)
        assertEquals("9", serverchan.channel)
        assertEquals("openid", serverchan.openid)
        assertEquals("Relay", serverchan.titleTemplate)

        val telegram = SenderSettingJson.decode<TelegramSetting>(
            SenderSettingDrafts.empty(SenderType.TELEGRAM)
                .withString("method", "POST")
                .withString("apiToken", "bot-token")
                .withString("chatId", "123")
                .withString("messageThreadId", "456")
                .withString("proxyType", "DIRECT")
                .withString("proxyHost", "")
                .withString("proxyPort", "")
                .withString("parseMode", "HTML")
                .toJson(),
        )
        assertEquals("POST", telegram.method)
        assertEquals("bot-token", telegram.apiToken)
        assertEquals("123", telegram.chatId)
        assertEquals("456", telegram.messageThreadId)
        assertEquals(Proxy.Type.DIRECT, telegram.proxyType)
        assertEquals("HTML", telegram.parseMode)

        val feishu = SenderSettingJson.decode<FeishuSetting>(
            SenderSettingDrafts.empty(SenderType.FEISHU)
                .withString("webhook", "https://open.feishu.cn/hook")
                .withString("secret", "secret")
                .withString("msgType", "interactive")
                .withString("titleTemplate", "Relay")
                .withString("messageCard", "{\"config\":{}}")
                .toJson(),
        )
        assertEquals("https://open.feishu.cn/hook", feishu.webhook)
        assertEquals("secret", feishu.secret)
        assertEquals("interactive", feishu.msgType)
        assertEquals("Relay", feishu.titleTemplate)
        assertEquals("{\"config\":{}}", feishu.messageCard)

        val feishuApp = SenderSettingJson.decode<FeishuAppSetting>(
            SenderSettingDrafts.empty(SenderType.FEISHU_APP)
                .withString("appId", "cli_id")
                .withString("appSecret", "app-secret")
                .withString("receiveId", "ou_xxx")
                .withString("msgType", "interactive")
                .withString("titleTemplate", "Relay")
                .withString("receiveIdType", "open_id")
                .withString("messageCard", "{\"elements\":[]}")
                .toJson(),
        )
        assertEquals("cli_id", feishuApp.appId)
        assertEquals("app-secret", feishuApp.appSecret)
        assertEquals("ou_xxx", feishuApp.receiveId)
        assertEquals("interactive", feishuApp.msgType)
        assertEquals("Relay", feishuApp.titleTemplate)
        assertEquals("open_id", feishuApp.receiveIdType)
        assertEquals("{\"elements\":[]}", feishuApp.messageCard)

        val ntfy = SenderSettingJson.decode<NtfySetting>(
            SenderSettingDrafts.empty(SenderType.NTFY)
                .withString("server", "https://ntfy.sh")
                .withString("topic", "relay")
                .withString("token", "token")
                .withString("title", "Relay")
                .withString("priority", "4")
                .withString("tags", "sms,relay")
                .toJson(),
        )
        assertEquals("https://ntfy.sh", ntfy.server)
        assertEquals("relay", ntfy.topic)
        assertEquals("token", ntfy.token)
        assertEquals("Relay", ntfy.title)
        assertEquals("4", ntfy.priority)
        assertEquals("sms,relay", ntfy.tags)

        val dingtalkInner = SenderSettingJson.decode<DingtalkInnerRobotSetting>(
            SenderSettingDrafts.empty(SenderType.DINGTALK_INNER_ROBOT)
                .withString("agentID", "100001")
                .withString("appKey", "app-key")
                .withString("appSecret", "app-secret")
                .withString("userIds", "user1,user2")
                .withString("msgKey", "sampleMarkdown")
                .withString("titleTemplate", "Relay")
                .withString("proxyType", "DIRECT")
                .toJson(),
        )
        assertEquals("100001", dingtalkInner.agentID)
        assertEquals("app-key", dingtalkInner.appKey)
        assertEquals("app-secret", dingtalkInner.appSecret)
        assertEquals("user1,user2", dingtalkInner.userIds)
        assertEquals("sampleMarkdown", dingtalkInner.msgKey)
        assertEquals("Relay", dingtalkInner.titleTemplate)
        assertEquals(Proxy.Type.DIRECT, dingtalkInner.proxyType)

        val bark = SenderSettingJson.decode<BarkSetting>(
            SenderSettingDrafts.empty(SenderType.BARK)
                .withString("server", "https://api.day.app/key")
                .withString("title", "Relay")
                .withString("transformation", "AES/GCM/NoPadding")
                .withString("key", "secret-key")
                .withString("iv", "secret-iv")
                .toJson(),
        )
        assertEquals("https://api.day.app/key", bark.server)
        assertEquals("Relay", bark.title)
        assertEquals("AES/GCM/NoPadding", bark.transformation)
        assertEquals("secret-key", bark.key)
        assertEquals("secret-iv", bark.iv)

        val email = SenderSettingJson.decode<EmailSetting>(
            SenderSettingDrafts.empty(SenderType.EMAIL)
                .withString("mailType", "smtp")
                .withString("authEmail", "auth@example.com")
                .withString("fromEmail", "from@example.com")
                .withString("fromEmailAlias", "Relay")
                .withString("pwd", "password")
                .withString("host", "smtp.example.com")
                .withString("port", "465")
                .withBoolean("ssl", true)
                .withBoolean("startTls", false)
                .withString("toEmail", "to@example.com")
                .withString("title", "Relay")
                .toJson(),
        )
        assertEquals("smtp", email.mailType)
        assertEquals("auth@example.com", email.authEmail)
        assertEquals("from@example.com", email.fromEmail)
        assertEquals("Relay", email.fromEmailAlias)
        assertEquals("password", email.pwd)
        assertEquals("smtp.example.com", email.host)
        assertEquals("465", email.port)
        assertEquals(true, email.ssl)
        assertEquals(false, email.startTls)
        assertEquals("to@example.com", email.toEmail)
        assertEquals("Relay", email.title)

        val webhook = SenderSettingJson.decode<WebhookSetting>(
            SenderSettingDrafts.empty(SenderType.WEBHOOK)
                .withString("method", "POST")
                .withString("webServer", "https://example.com/hook")
                .withString("secret", "secret")
                .withString("webParams", "{\"msg\":\"[msg]\"}")
                .withStringMap("headers", mapOf("Authorization" to "Bearer token"))
                .withString("proxyType", "DIRECT")
                .toJson(),
        )
        assertEquals("POST", webhook.method)
        assertEquals("https://example.com/hook", webhook.webServer)
        assertEquals("secret", webhook.secret)
        assertEquals("{\"msg\":\"[msg]\"}", webhook.webParams)
        assertEquals(mapOf("Authorization" to "Bearer token"), webhook.headers)
        assertEquals(Proxy.Type.DIRECT, webhook.proxyType)

        val socket = SenderSettingJson.decode<SocketSetting>(
            SenderSettingDrafts.empty(SenderType.SOCKET)
                .withString("method", "MQTT")
                .withString("address", "127.0.0.1")
                .withInt("port", 1883)
                .withString("msgTemplate", "{\"msg\":\"[msg]\"}")
                .withString("outMessageTopic", "relay/default")
                .toJson(),
        )
        assertEquals("MQTT", socket.method)
        assertEquals("127.0.0.1", socket.address)
        assertEquals(1883, socket.port)
        assertEquals("{\"msg\":\"[msg]\"}", socket.msgTemplate)
        assertEquals("relay/default", socket.outMessageTopic)

        val weworkAgent = SenderSettingJson.decode<WeworkAgentSetting>(
            SenderSettingDrafts.empty(SenderType.WEWORK_AGENT)
                .withString("corpID", "corp")
                .withString("agentID", "100001")
                .withString("secret", "secret")
                .withString("toUser", "@all")
                .withString("customizeAPI", "https://qyapi.weixin.qq.com")
                .withString("proxyType", "DIRECT")
                .toJson(),
        )
        assertEquals("corp", weworkAgent.corpID)
        assertEquals("100001", weworkAgent.agentID)
        assertEquals("secret", weworkAgent.secret)
        assertEquals("@all", weworkAgent.toUser)
        assertEquals("https://qyapi.weixin.qq.com", weworkAgent.customizeAPI)
        assertEquals(Proxy.Type.DIRECT, weworkAgent.proxyType)

        val weworkRobot = SenderSettingJson.decode<WeworkRobotSetting>(
            SenderSettingDrafts.empty(SenderType.WEWORK_ROBOT)
                .withString("webHook", "https://qyapi.weixin.qq.com/cgi-bin/webhook/send")
                .withString("msgType", "markdown")
                .withBoolean("atAll", true)
                .withString("atUserIds", "user1,user2")
                .withString("atMobiles", "13800000000")
                .toJson(),
        )
        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send", weworkRobot.webHook)
        assertEquals("markdown", weworkRobot.msgType)
        assertEquals(true, weworkRobot.atAll)
        assertEquals("user1,user2", weworkRobot.atUserIds)
        assertEquals("13800000000", weworkRobot.atMobiles)
    }

    @Test
    fun withField_rejectsUnknownFieldForKnownSenderType() {
        val draft = SenderSettingDrafts.empty(SenderType.TELEGRAM)

        assertThrows(IllegalArgumentException::class.java) {
            draft.withString("webServer", "https://example.com")
        }
    }
}
