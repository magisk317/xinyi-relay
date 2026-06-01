package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.Sender
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
import io.github.magisk317.relay.sender.config.YunhuSetting
import io.github.magisk317.relay.sender.config.FeishuBotTokenSetting
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.engine.sender.SenderType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.Proxy
import java.util.Locale

object SenderSettingSanitizer {
    private const val TELEGRAM_BOT_ID_MIN_LENGTH = 5
    private const val TELEGRAM_BOT_SECRET_MIN_LENGTH = 20

    fun sanitizeSenderLenient(sender: Sender): Sender {
        val safeJson = sanitizeJsonLenient(sender.type, sender.jsonSetting)
        val safeSchedule = SenderActiveScheduleEvaluator.sanitize(sender.activeSchedule)
        return if (safeJson == sender.jsonSetting && safeSchedule == sender.activeSchedule) {
            sender
        } else {
            sender.copy(jsonSetting = safeJson, activeSchedule = safeSchedule)
        }
    }

    fun sanitizeJsonLenient(type: Int, json: String): String {
        val rawJson = parseSettingJson(json)
        val canonicalJson = canonicalizeLegacyKeys(type, rawJson)
        val parseJson = canonicalJson?.toString() ?: json
        return when (type) {
            SenderType.DINGTALK_GROUP_ROBOT -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                DingtalkGroupRobotSetting.serializer(),
                ::sanitizeDingtalkGroupRobotSetting,
            )
            SenderType.EMAIL -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                EmailSetting.serializer(),
                ::sanitizeEmailSetting,
            )
            SenderType.BARK -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                BarkSetting.serializer(),
                ::sanitizeBarkSetting,
            )
            SenderType.WEBHOOK -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                WebhookSetting.serializer(),
                ::sanitizeWebhookSetting,
            )
            SenderType.WEWORK_ROBOT -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                WeworkRobotSetting.serializer(),
                ::sanitizeWeworkRobotSetting,
            )
            SenderType.WEWORK_AGENT -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                WeworkAgentSetting.serializer(),
                ::sanitizeWeworkAgentSetting,
            )
            SenderType.SERVERCHAN -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                ServerchanSetting.serializer(),
                ::sanitizeServerchanSetting,
            )
            SenderType.PUSHPLUS -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                PushplusSetting.serializer(),
                ::sanitizePushplusSetting,
            )
            SenderType.TELEGRAM -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                TelegramSetting.serializer(),
                ::sanitizeTelegramSetting,
            )
            SenderType.SMS -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                SmsSetting.serializer(),
                ::sanitizeSmsSetting,
            )
            SenderType.FEISHU -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                FeishuSetting.serializer(),
                ::sanitizeFeishuSetting,
            )
            SenderType.GOTIFY -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                GotifySetting.serializer(),
                ::sanitizeGotifySetting,
            )
            SenderType.DINGTALK_INNER_ROBOT -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                DingtalkInnerRobotSetting.serializer(),
                ::sanitizeDingtalkInnerRobotSetting,
            )
            SenderType.FEISHU_APP -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                FeishuAppSetting.serializer(),
                ::sanitizeFeishuAppSetting,
            )
            SenderType.URL_SCHEME -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                UrlSchemeSetting.serializer(),
                ::sanitizeUrlSchemeSetting,
            )
            SenderType.SOCKET -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                SocketSetting.serializer(),
                ::sanitizeSocketSetting,
            )
            SenderType.NTFY -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                NtfySetting.serializer(),
                ::sanitizeNtfySetting,
            )
            SenderType.YUNHU -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                YunhuSetting.serializer(),
                ::sanitizeYunhuSetting,
            )
            SenderType.FEISHU_BOT_TOKEN -> sanitizeSettingJson(
                parseJson,
                canonicalJson,
                FeishuBotTokenSetting.serializer(),
                ::sanitizeFeishuBotTokenSetting,
            )
            else -> if (json.isBlank()) "" else json
        }
    }

    private fun <T> sanitizeSettingJson(
        json: String,
        rawJson: JsonObject?,
        serializer: KSerializer<T>,
        sanitizer: (T?, JsonObject?) -> T,
    ): String {
        return SenderSettingJson.encode(serializer, sanitizer(parseSetting(json, serializer), rawJson))
    }

    fun sanitizeDingtalkGroupRobotSetting(
        raw: DingtalkGroupRobotSetting?,
        rawJson: JsonObject? = null,
    ): DingtalkGroupRobotSetting {
        val defaults = DingtalkGroupRobotSetting()
        val setting = DingtalkGroupRobotSetting(
            token = safeString(resolveValue(raw?.token, rawJson, "token")),
            secret = safeString(resolveValue(raw?.secret, rawJson, "secret")),
            atAll = safeBoolean(resolveValue(raw?.atAll, rawJson, "atAll"), defaults.atAll),
            atMobiles = safeString(resolveValue(raw?.atMobiles, rawJson, "atMobiles")),
            atDingtalkIds = safeString(resolveValue(raw?.atDingtalkIds, rawJson, "atDingtalkIds")),
            msgtype = safeString(resolveValue(raw?.msgtype, rawJson, "msgtype")).ifBlank { defaults.msgtype },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
        )
        val repaired = repairFields(
            "token" to setting.token,
            "secret" to setting.secret,
            "msgtype" to setting.msgtype,
        )
        return setting.copy(
            token = repaired.string("token"),
            secret = repaired.string("secret"),
            msgtype = repaired.enumString("msgtype", defaults.msgtype),
        )
    }

    fun sanitizeEmailSetting(raw: EmailSetting?, rawJson: JsonObject? = null): EmailSetting {
        val defaults = EmailSetting()
        val fromEmail = safeString(resolveValue(raw?.fromEmail, rawJson, "fromEmail"))
        val authEmail = safeString(resolveValue(raw?.authEmail, rawJson, "authEmail")).ifBlank { fromEmail }
        val alias = safeString(resolveValue(raw?.fromEmailAlias, rawJson, "fromEmailAlias"))
            .ifBlank { safeString(resolveValue(raw?.nickname, rawJson, "nickname")) }
        val setting = EmailSetting(
            mailType = safeString(resolveValue(raw?.mailType, rawJson, "mailType")),
            authEmail = authEmail,
            fromEmail = fromEmail,
            pwd = safeString(resolveValue(raw?.pwd, rawJson, "pwd")),
            nickname = safeString(resolveValue(raw?.nickname, rawJson, "nickname")).ifBlank { alias },
            host = safeString(resolveValue(raw?.host, rawJson, "host")),
            port = safeString(resolveValue(raw?.port, rawJson, "port")),
            ssl = safeBoolean(resolveValue(raw?.ssl, rawJson, "ssl"), defaults.ssl),
            startTls = safeBoolean(resolveValue(raw?.startTls, rawJson, "startTls"), defaults.startTls),
            title = safeString(resolveValue(raw?.title, rawJson, "title")),
            recipients = safeEmailRecipients(resolveValue(raw?.recipients, rawJson, "recipients")),
            toEmail = safeString(resolveValue(raw?.toEmail, rawJson, "toEmail")),
            keystore = safeString(resolveValue(raw?.keystore, rawJson, "keystore")),
            password = safeString(resolveValue(raw?.password, rawJson, "password")),
            encryptionProtocol = safeString(resolveValue(raw?.encryptionProtocol, rawJson, "encryptionProtocol"))
                .ifBlank { defaults.encryptionProtocol },
            fromEmailAlias = alias,
        )
        val repaired = repairFields(
            "authEmail" to setting.authEmail,
            "fromEmail" to setting.fromEmail,
            "host" to setting.host,
            "port" to setting.port,
            "ssl" to setting.ssl,
            "startTls" to setting.startTls,
            "toEmail" to setting.toEmail,
            "encryptionProtocol" to setting.encryptionProtocol,
        )
        return setting.copy(
            authEmail = repaired.string("authEmail"),
            fromEmail = repaired.string("fromEmail"),
            host = repaired.string("host"),
            port = repaired.string("port"),
            ssl = repaired.boolean("ssl", defaults.ssl),
            startTls = repaired.boolean("startTls", defaults.startTls),
            toEmail = repaired.string("toEmail"),
            encryptionProtocol = repaired.enumString("encryptionProtocol", defaults.encryptionProtocol),
        )
    }

    fun sanitizeBarkSetting(raw: BarkSetting?, rawJson: JsonObject? = null): BarkSetting {
        val defaults = BarkSetting()
        val setting = BarkSetting(
            server = safeString(resolveValue(raw?.server, rawJson, "server")),
            group = safeString(resolveValue(raw?.group, rawJson, "group")),
            icon = safeString(resolveValue(raw?.icon, rawJson, "icon")),
            sound = safeString(resolveValue(raw?.sound, rawJson, "sound")),
            badge = safeString(resolveValue(raw?.badge, rawJson, "badge")),
            url = safeString(resolveValue(raw?.url, rawJson, "url")),
            level = safeString(resolveValue(raw?.level, rawJson, "level")).ifBlank { defaults.level },
            title = safeString(resolveValue(raw?.title, rawJson, "title")),
            transformation = safeString(resolveValue(raw?.transformation, rawJson, "transformation"))
                .ifBlank { defaults.transformation },
            key = safeString(resolveValue(raw?.key, rawJson, "key")),
            iv = safeString(resolveValue(raw?.iv, rawJson, "iv")),
            call = safeString(resolveValue(raw?.call, rawJson, "call")),
            autoCopy = safeString(resolveValue(raw?.autoCopy, rawJson, "autoCopy")),
        )
        val repaired = repairFields(
            "server" to setting.server,
            "url" to setting.url,
            "level" to setting.level,
            "transformation" to setting.transformation,
        )
        return setting.copy(
            server = repaired.string("server"),
            url = repaired.string("url"),
            level = repaired.enumString("level", defaults.level),
            transformation = repaired.enumString("transformation", defaults.transformation),
        )
    }

    fun sanitizeWebhookSetting(raw: WebhookSetting?, rawJson: JsonObject? = null): WebhookSetting {
        val defaults = WebhookSetting()
        val setting = WebhookSetting(
            method = safeString(resolveValue(raw?.method, rawJson, "method")).ifBlank { defaults.method },
            webServer = safeString(resolveValue(raw?.webServer, rawJson, "webServer")),
            secret = safeString(resolveValue(raw?.secret, rawJson, "secret")),
            response = safeString(resolveValue(raw?.response, rawJson, "response")),
            webParams = safeString(resolveValue(raw?.webParams, rawJson, "webParams")),
            headers = safeMapStringString(resolveValue(raw?.headers, rawJson, "headers")),
            proxyType = safeProxyType(resolveValue(raw?.proxyType, rawJson, "proxyType")),
            proxyHost = safeString(resolveValue(raw?.proxyHost, rawJson, "proxyHost")),
            proxyPort = safeString(resolveValue(raw?.proxyPort, rawJson, "proxyPort")),
            proxyAuthenticator = safeBoolean(
                resolveValue(raw?.proxyAuthenticator, rawJson, "proxyAuthenticator"),
                defaults.proxyAuthenticator,
            ),
            proxyUsername = safeString(resolveValue(raw?.proxyUsername, rawJson, "proxyUsername")),
            proxyPassword = safeString(resolveValue(raw?.proxyPassword, rawJson, "proxyPassword")),
        )
        return repairWebhookSetting(setting)
    }

    fun sanitizeWeworkRobotSetting(raw: WeworkRobotSetting?, rawJson: JsonObject? = null): WeworkRobotSetting {
        val defaults = WeworkRobotSetting()
        val setting = WeworkRobotSetting(
            webHook = safeString(resolveValue(raw?.webHook, rawJson, "webHook")),
            msgType = safeString(resolveValue(raw?.msgType, rawJson, "msgType")).ifBlank { defaults.msgType },
            atAll = safeBoolean(resolveValue(raw?.atAll, rawJson, "atAll"), defaults.atAll),
            atUserIds = safeString(resolveValue(raw?.atUserIds, rawJson, "atUserIds")),
            atMobiles = safeString(resolveValue(raw?.atMobiles, rawJson, "atMobiles")),
        )
        val repaired = repairFields(
            "webHook" to setting.webHook,
            "msgType" to setting.msgType,
            "atAll" to setting.atAll,
        )
        return setting.copy(
            webHook = repaired.string("webHook"),
            msgType = repaired.enumString("msgType", defaults.msgType),
            atAll = repaired.boolean("atAll", defaults.atAll),
        )
    }

    fun sanitizeWeworkAgentSetting(raw: WeworkAgentSetting?, rawJson: JsonObject? = null): WeworkAgentSetting {
        val defaults = WeworkAgentSetting()
        val setting = WeworkAgentSetting(
            corpID = safeString(resolveValue(raw?.corpID, rawJson, "corpID")),
            agentID = safeString(resolveValue(raw?.agentID, rawJson, "agentID")),
            secret = safeString(resolveValue(raw?.secret, rawJson, "secret")),
            atAll = safeBoolean(resolveValue(raw?.atAll, rawJson, "atAll"), defaults.atAll),
            toUser = safeString(resolveValue(raw?.toUser, rawJson, "toUser")).ifBlank { defaults.toUser },
            toParty = safeString(resolveValue(raw?.toParty, rawJson, "toParty")),
            toTag = safeString(resolveValue(raw?.toTag, rawJson, "toTag")),
            proxyType = safeProxyType(resolveValue(raw?.proxyType, rawJson, "proxyType")),
            proxyHost = safeString(resolveValue(raw?.proxyHost, rawJson, "proxyHost")),
            proxyPort = safeString(resolveValue(raw?.proxyPort, rawJson, "proxyPort")),
            proxyAuthenticator = safeBoolean(
                resolveValue(raw?.proxyAuthenticator, rawJson, "proxyAuthenticator"),
                defaults.proxyAuthenticator,
            ),
            proxyUsername = safeString(resolveValue(raw?.proxyUsername, rawJson, "proxyUsername")),
            proxyPassword = safeString(resolveValue(raw?.proxyPassword, rawJson, "proxyPassword")),
            customizeAPI = safeString(resolveValue(raw?.customizeAPI, rawJson, "customizeAPI"))
                .ifBlank { defaults.customizeAPI },
        )
        val repaired = repairFields(
            "corpID" to setting.corpID,
            "agentID" to setting.agentID,
            "secret" to setting.secret,
            "atAll" to setting.atAll,
            "proxyType" to setting.proxyType,
            "proxyHost" to setting.proxyHost,
            "proxyPort" to setting.proxyPort,
            "proxyAuthenticator" to setting.proxyAuthenticator,
            "customizeAPI" to setting.customizeAPI,
        )
        return setting.copy(
            corpID = repaired.string("corpID"),
            agentID = repaired.string("agentID"),
            secret = repaired.string("secret"),
            atAll = repaired.boolean("atAll", defaults.atAll),
            proxyType = repaired.proxy("proxyType"),
            proxyHost = repaired.string("proxyHost"),
            proxyPort = repaired.string("proxyPort"),
            proxyAuthenticator = repaired.boolean("proxyAuthenticator", defaults.proxyAuthenticator),
            customizeAPI = repaired.string("customizeAPI").ifBlank { defaults.customizeAPI },
        )
    }

    fun sanitizeServerchanSetting(raw: ServerchanSetting?, rawJson: JsonObject? = null): ServerchanSetting {
        val setting = ServerchanSetting(
            sendKey = safeString(resolveValue(raw?.sendKey, rawJson, "sendKey")),
            channel = safeString(resolveValue(raw?.channel, rawJson, "channel")),
            openid = safeString(resolveValue(raw?.openid, rawJson, "openid")),
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
        )
        val repaired = repairFields(
            "sendKey" to setting.sendKey,
            "channel" to setting.channel,
            "openid" to setting.openid,
        )
        return setting.copy(
            sendKey = repaired.string("sendKey"),
            channel = repaired.string("channel"),
            openid = repaired.string("openid"),
        )
    }

    fun sanitizePushplusSetting(raw: PushplusSetting?, rawJson: JsonObject? = null): PushplusSetting {
        val defaults = PushplusSetting()
        val setting = PushplusSetting(
            website = safeString(resolveValue(raw?.website, rawJson, "website")).ifBlank { defaults.website },
            token = safeString(resolveValue(raw?.token, rawJson, "token")),
            topic = safeString(resolveValue(raw?.topic, rawJson, "topic")),
            template = safeString(resolveValue(raw?.template, rawJson, "template")),
            channel = safeString(resolveValue(raw?.channel, rawJson, "channel")),
            webhook = safeString(resolveValue(raw?.webhook, rawJson, "webhook")),
            callbackUrl = safeString(resolveValue(raw?.callbackUrl, rawJson, "callbackUrl")),
            validTime = safeString(resolveValue(raw?.validTime, rawJson, "validTime")),
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
        )
        val repaired = repairFields(
            "website" to setting.website,
            "token" to setting.token,
            "webhook" to setting.webhook,
            "callbackUrl" to setting.callbackUrl,
            "validTime" to setting.validTime,
        )
        return setting.copy(
            website = repaired.string("website").ifBlank { defaults.website },
            token = repaired.string("token"),
            webhook = repaired.string("webhook"),
            callbackUrl = repaired.string("callbackUrl"),
            validTime = repaired.string("validTime"),
        )
    }

    fun sanitizeYunhuSetting(raw: YunhuSetting?, rawJson: JsonObject? = null): YunhuSetting {
        val defaults = YunhuSetting()
        val setting = YunhuSetting(
            token = safeString(resolveValue(raw?.token, rawJson, "token")),
            recvId = safeString(resolveValue(raw?.recvId, rawJson, "recvId")),
            recvType = safeString(resolveValue(raw?.recvType, rawJson, "recvType")).ifBlank { defaults.recvType },
            contentType = safeString(resolveValue(raw?.contentType, rawJson, "contentType")).ifBlank { defaults.contentType },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
        )
        val repaired = repairFields(
            "token" to setting.token,
            "recvId" to setting.recvId,
            "recvType" to setting.recvType,
            "contentType" to setting.contentType,
        )
        return setting.copy(
            token = repaired.string("token"),
            recvId = repaired.string("recvId"),
            recvType = repaired.enumString("recvType", defaults.recvType),
            contentType = repaired.enumString("contentType", defaults.contentType),
        )
    }

    fun sanitizeTelegramSetting(raw: TelegramSetting?, rawJson: JsonObject? = null): TelegramSetting {
        val defaults = TelegramSetting()
        val setting = TelegramSetting(
            apiBase = safeString(resolveValue(raw?.apiBase, rawJson, "apiBase")).ifBlank { defaults.apiBase },
            method = safeString(resolveValue(raw?.method, rawJson, "method")).ifBlank { defaults.method },
            apiToken = safeString(resolveValue(raw?.apiToken, rawJson, "apiToken")),
            chatId = safeString(resolveValue(raw?.chatId, rawJson, "chatId")),
            messageThreadId = safeString(
                resolveValue(
                    raw?.messageThreadId,
                    rawJson,
                    "messageThreadId",
                    "topicId",
                    "topic_id",
                    "message_thread_id",
                ),
            ),
            proxyType = safeProxyType(resolveValue(raw?.proxyType, rawJson, "proxyType")),
            proxyHost = safeString(resolveValue(raw?.proxyHost, rawJson, "proxyHost")),
            proxyPort = safeString(resolveValue(raw?.proxyPort, rawJson, "proxyPort")),
            proxyAuthenticator = safeBoolean(
                resolveValue(raw?.proxyAuthenticator, rawJson, "proxyAuthenticator"),
                defaults.proxyAuthenticator,
            ),
            proxyUsername = safeString(resolveValue(raw?.proxyUsername, rawJson, "proxyUsername")),
            proxyPassword = safeString(resolveValue(raw?.proxyPassword, rawJson, "proxyPassword")),
            parseMode = safeString(resolveValue(raw?.parseMode, rawJson, "parseMode")).ifBlank { defaults.parseMode },
        )
        return repairTelegramSetting(setting)
    }

    fun sanitizeSmsSetting(raw: SmsSetting?, rawJson: JsonObject? = null): SmsSetting {
        val defaults = SmsSetting()
        val setting = SmsSetting(
            simSlot = safeInt(resolveValue(raw?.simSlot, rawJson, "simSlot"), defaults.simSlot),
            mobiles = safeString(resolveValue(raw?.mobiles, rawJson, "mobiles")),
            onlyNoNetwork = safeBoolean(
                resolveValue(raw?.onlyNoNetwork, rawJson, "onlyNoNetwork"),
                defaults.onlyNoNetwork,
            ),
        )
        val repaired = repairFields(
            "simSlot" to setting.simSlot,
            "mobiles" to setting.mobiles,
            "onlyNoNetwork" to setting.onlyNoNetwork,
        )
        return setting.copy(
            simSlot = repaired.int("simSlot", defaults.simSlot),
            mobiles = repaired.string("mobiles"),
            onlyNoNetwork = repaired.boolean("onlyNoNetwork", defaults.onlyNoNetwork),
        )
    }

    fun sanitizeFeishuSetting(raw: FeishuSetting?, rawJson: JsonObject? = null): FeishuSetting {
        val defaults = FeishuSetting()
        val setting = FeishuSetting(
            webhook = safeString(resolveValue(raw?.webhook, rawJson, "webhook")),
            secret = safeString(resolveValue(raw?.secret, rawJson, "secret")),
            msgType = safeString(resolveValue(raw?.msgType, rawJson, "msgType")).ifBlank { defaults.msgType },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
            messageCard = safeString(resolveValue(raw?.messageCard, rawJson, "messageCard")),
        )
        return repairFeishuSetting(setting)
    }

    fun sanitizeGotifySetting(raw: GotifySetting?, rawJson: JsonObject? = null): GotifySetting {
        val setting = GotifySetting(
            webServer = safeString(resolveValue(raw?.webServer, rawJson, "webServer")),
            title = safeString(resolveValue(raw?.title, rawJson, "title")),
            priority = safeString(resolveValue(raw?.priority, rawJson, "priority")),
        )
        val repaired = repairFields(
            "webServer" to setting.webServer,
            "priority" to setting.priority,
        )
        return setting.copy(
            webServer = repaired.string("webServer"),
            priority = repaired.string("priority"),
        )
    }

    fun sanitizeNtfySetting(raw: NtfySetting?, rawJson: JsonObject? = null): NtfySetting {
        val defaults = NtfySetting()
        val setting = NtfySetting(
            server = safeString(resolveValue(raw?.server, rawJson, "server")),
            topic = safeString(resolveValue(raw?.topic, rawJson, "topic")),
            token = safeString(resolveValue(raw?.token, rawJson, "token")),
            title = safeString(resolveValue(raw?.title, rawJson, "title")),
            priority = safeString(resolveValue(raw?.priority, rawJson, "priority")).ifBlank { defaults.priority },
            tags = safeString(resolveValue(raw?.tags, rawJson, "tags")),
        )
        val repaired = repairFields(
            "server" to setting.server,
            "topic" to setting.topic,
            "token" to setting.token,
            "priority" to setting.priority,
        )
        return setting.copy(
            server = repaired.string("server"),
            topic = repaired.string("topic"),
            token = repaired.string("token"),
            priority = repaired.enumString("priority", defaults.priority),
        )
    }

    fun sanitizeDingtalkInnerRobotSetting(
        raw: DingtalkInnerRobotSetting?,
        rawJson: JsonObject? = null,
    ): DingtalkInnerRobotSetting {
        val defaults = DingtalkInnerRobotSetting()
        val setting = DingtalkInnerRobotSetting(
            agentID = safeString(resolveValue(raw?.agentID, rawJson, "agentID")),
            appKey = safeString(resolveValue(raw?.appKey, rawJson, "appKey")),
            appSecret = safeString(resolveValue(raw?.appSecret, rawJson, "appSecret")),
            userIds = safeString(resolveValue(raw?.userIds, rawJson, "userIds")),
            msgKey = safeString(resolveValue(raw?.msgKey, rawJson, "msgKey")).ifBlank { defaults.msgKey },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
            proxyType = safeProxyType(resolveValue(raw?.proxyType, rawJson, "proxyType")),
            proxyHost = safeString(resolveValue(raw?.proxyHost, rawJson, "proxyHost")),
            proxyPort = safeString(resolveValue(raw?.proxyPort, rawJson, "proxyPort")),
            proxyAuthenticator = safeBoolean(
                resolveValue(raw?.proxyAuthenticator, rawJson, "proxyAuthenticator"),
                defaults.proxyAuthenticator,
            ),
            proxyUsername = safeString(resolveValue(raw?.proxyUsername, rawJson, "proxyUsername")),
            proxyPassword = safeString(resolveValue(raw?.proxyPassword, rawJson, "proxyPassword")),
        )
        val repaired = repairFields(
            "agentID" to setting.agentID,
            "appKey" to setting.appKey,
            "appSecret" to setting.appSecret,
            "userIds" to setting.userIds,
            "msgKey" to setting.msgKey,
            "proxyType" to setting.proxyType,
            "proxyHost" to setting.proxyHost,
            "proxyPort" to setting.proxyPort,
            "proxyAuthenticator" to setting.proxyAuthenticator,
        )
        return setting.copy(
            agentID = repaired.string("agentID"),
            appKey = repaired.string("appKey"),
            appSecret = repaired.string("appSecret"),
            userIds = repaired.string("userIds"),
            msgKey = repaired.enumString("msgKey", defaults.msgKey),
            proxyType = repaired.proxy("proxyType"),
            proxyHost = repaired.string("proxyHost"),
            proxyPort = repaired.string("proxyPort"),
            proxyAuthenticator = repaired.boolean("proxyAuthenticator", defaults.proxyAuthenticator),
        )
    }

    fun sanitizeFeishuAppSetting(raw: FeishuAppSetting?, rawJson: JsonObject? = null): FeishuAppSetting {
        val defaults = FeishuAppSetting()
        val setting = FeishuAppSetting(
            appId = safeString(resolveValue(raw?.appId, rawJson, "appId")),
            appSecret = safeString(resolveValue(raw?.appSecret, rawJson, "appSecret")),
            receiveId = safeString(resolveValue(raw?.receiveId, rawJson, "receiveId")),
            msgType = safeString(resolveValue(raw?.msgType, rawJson, "msgType")).ifBlank { defaults.msgType },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
            receiveIdType = safeString(resolveValue(raw?.receiveIdType, rawJson, "receiveIdType"))
                .ifBlank { defaults.receiveIdType },
            messageCard = safeString(resolveValue(raw?.messageCard, rawJson, "messageCard")),
        )
        val repaired = repairFields(
            "appId" to setting.appId,
            "appSecret" to setting.appSecret,
            "receiveId" to setting.receiveId,
            "msgType" to setting.msgType,
            "receiveIdType" to setting.receiveIdType,
        )
        return setting.copy(
            appId = repaired.string("appId"),
            appSecret = repaired.string("appSecret"),
            receiveId = repaired.string("receiveId"),
            msgType = repaired.enumString("msgType", defaults.msgType),
            receiveIdType = repaired.enumString("receiveIdType", defaults.receiveIdType),
        )
    }

    fun sanitizeFeishuBotTokenSetting(raw: FeishuBotTokenSetting?, rawJson: JsonObject? = null): FeishuBotTokenSetting {
        val defaults = FeishuBotTokenSetting()
        val setting = FeishuBotTokenSetting(
            token = safeString(resolveValue(raw?.token, rawJson, "token")),
            receiveId = safeString(resolveValue(raw?.receiveId, rawJson, "receiveId")),
            msgType = safeString(resolveValue(raw?.msgType, rawJson, "msgType")).ifBlank { defaults.msgType },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
            receiveIdType = safeString(resolveValue(raw?.receiveIdType, rawJson, "receiveIdType"))
                .ifBlank { defaults.receiveIdType },
            messageCard = safeString(resolveValue(raw?.messageCard, rawJson, "messageCard")),
        )
        val repaired = repairFields(
            "token" to setting.token,
            "receiveId" to setting.receiveId,
            "msgType" to setting.msgType,
            "receiveIdType" to setting.receiveIdType,
        )
        return setting.copy(
            token = repaired.string("token"),
            receiveId = repaired.string("receiveId"),
            msgType = repaired.enumString("msgType", defaults.msgType),
            receiveIdType = repaired.enumString("receiveIdType", defaults.receiveIdType),
        )
    }

    fun sanitizeUrlSchemeSetting(raw: UrlSchemeSetting?, rawJson: JsonObject? = null): UrlSchemeSetting {
        return UrlSchemeSetting(
            urlScheme = safeString(resolveValue(raw?.urlScheme, rawJson, "urlScheme")),
        )
    }

    fun sanitizeSocketSetting(raw: SocketSetting?, rawJson: JsonObject? = null): SocketSetting {
        val defaults = SocketSetting()
        val setting = SocketSetting(
            method = safeString(resolveValue(raw?.method, rawJson, "method")).ifBlank { defaults.method },
            address = safeString(resolveValue(raw?.address, rawJson, "address")),
            port = safeInt(resolveValue(raw?.port, rawJson, "port"), defaults.port),
            msgTemplate = safeString(resolveValue(raw?.msgTemplate, rawJson, "msgTemplate")),
            secret = safeString(resolveValue(raw?.secret, rawJson, "secret")),
            response = safeString(resolveValue(raw?.response, rawJson, "response")),
            username = safeString(resolveValue(raw?.username, rawJson, "username")),
            password = safeString(resolveValue(raw?.password, rawJson, "password")),
            inCharset = safeString(resolveValue(raw?.inCharset, rawJson, "inCharset")),
            outCharset = safeString(resolveValue(raw?.outCharset, rawJson, "outCharset")),
            inMessageTopic = safeString(resolveValue(raw?.inMessageTopic, rawJson, "inMessageTopic")),
            outMessageTopic = safeString(resolveValue(raw?.outMessageTopic, rawJson, "outMessageTopic")),
            uriType = safeString(resolveValue(raw?.uriType, rawJson, "uriType")).ifBlank { defaults.uriType },
            path = safeString(resolveValue(raw?.path, rawJson, "path")),
            clientId = safeString(resolveValue(raw?.clientId, rawJson, "clientId")),
            qos = safeInt(resolveValue(raw?.qos, rawJson, "qos"), defaults.qos),
            retained = safeBoolean(resolveValue(raw?.retained, rawJson, "retained"), defaults.retained),
        )
        val repaired = repairFields(
            "method" to setting.method,
            "address" to setting.address,
            "port" to setting.port,
            "uriType" to setting.uriType,
            "qos" to setting.qos,
            "retained" to setting.retained,
        )
        return setting.copy(
            method = repaired.enumString("method", defaults.method),
            address = repaired.string("address"),
            port = repaired.int("port", defaults.port),
            uriType = repaired.enumString("uriType", defaults.uriType),
            qos = repaired.int("qos", defaults.qos),
            retained = repaired.boolean("retained", defaults.retained),
        )
    }

    fun safeString(any: Any?): String {
        return when (any) {
            null -> ""
            is String -> any
            is Map<*, *>, is Iterable<*>, is Array<*> -> ""
            else -> any.toString()
        }
    }

    fun safeMapStringString(any: Any?): Map<String, String> {
        if (any !is Map<*, *>) return emptyMap()
        val result = LinkedHashMap<String, String>()
        any.forEach { (key, value) ->
            val safeKey = safeString(key).trim()
            if (safeKey.isEmpty()) return@forEach
            result[safeKey] = safeString(value)
        }
        return result
    }

    fun safeEmailRecipients(any: Any?): MutableMap<String, Pair<String, String>> {
        if (any !is Map<*, *>) return mutableMapOf()
        val result = LinkedHashMap<String, Pair<String, String>>()
        any.forEach { (key, value) ->
            val email = safeString(key).trim()
            if (email.isEmpty()) return@forEach
            result[email] = safePairStringString(value)
        }
        return result.toMutableMap()
    }

    fun safeProxyType(any: Any?): Proxy.Type {
        return when (any) {
            is Proxy.Type -> any
            is String -> {
                runCatching { Proxy.Type.valueOf(any.trim().uppercase()) }
                    .getOrDefault(Proxy.Type.DIRECT)
            }
            else -> Proxy.Type.DIRECT
        }
    }

    private fun safePairStringString(any: Any?): Pair<String, String> {
        return when (any) {
            is Pair<*, *> -> safeString(any.first) to safeString(any.second)
            is List<*> -> safeString(any.getOrNull(0)) to safeString(any.getOrNull(1))
            is Array<*> -> safeString(any.getOrNull(0)) to safeString(any.getOrNull(1))
            is Map<*, *> -> safeString(any["first"]) to safeString(any["second"])
            is String -> any to ""
            else -> "" to ""
        }
    }

    private fun safeBoolean(any: Any?, defaultValue: Boolean): Boolean {
        return when (any) {
            null -> defaultValue
            is Boolean -> any
            is Number -> any.toInt() != 0
            is String -> when (any.trim().lowercase()) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                else -> defaultValue
            }
            else -> defaultValue
        }
    }

    private fun safeInt(any: Any?, defaultValue: Int): Int {
        return when (any) {
            null -> defaultValue
            is Number -> any.toInt()
            is Boolean -> if (any) 1 else 0
            is String -> any.trim().toIntOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun canonicalizeLegacyKeys(type: Int, rawJson: JsonObject?): JsonObject? {
        if (rawJson == null) return null
        val specs = SenderSettingSchemas.fieldsFor(type)
        if (specs.isEmpty()) return rawJson
        return buildJsonObject {
            specs.forEach { spec ->
                firstFieldElement(rawJson, spec.name, *spec.aliases.toTypedArray())?.let { element ->
                    put(spec.name, element)
                }
            }
        }
    }

    private fun repairTelegramSetting(setting: TelegramSetting): TelegramSetting {
        val defaults = TelegramSetting()
        var method = setting.method
        var apiToken = setting.apiToken
        var chatId = setting.chatId
        var messageThreadId = setting.messageThreadId
        var movedToken = false

        if (!isHttpMethod(method) && isHttpMethod(apiToken)) {
            method = apiToken
            apiToken = ""
        }
        if (!isTelegramBotToken(apiToken) && isTelegramBotToken(chatId)) {
            apiToken = chatId
            chatId = ""
            movedToken = true
        }
        if (movedToken && !isTelegramChatId(chatId) && isTelegramChatId(messageThreadId)) {
            chatId = messageThreadId
            messageThreadId = ""
        }

        val repaired = repairFields(
            "apiBase" to setting.apiBase,
            "method" to method,
            "apiToken" to apiToken,
            "chatId" to chatId,
            "messageThreadId" to messageThreadId,
            "proxyType" to setting.proxyType,
            "proxyHost" to setting.proxyHost,
            "proxyPort" to setting.proxyPort,
            "proxyAuthenticator" to setting.proxyAuthenticator,
            "parseMode" to setting.parseMode,
        )
        return setting.copy(
            apiBase = repaired.string("apiBase").ifBlank { defaults.apiBase },
            method = repaired.enumString("method", defaults.method),
            apiToken = repaired.string("apiToken"),
            chatId = repaired.string("chatId"),
            messageThreadId = repaired.string("messageThreadId"),
            proxyType = repaired.proxy("proxyType"),
            proxyHost = repaired.string("proxyHost"),
            proxyPort = repaired.string("proxyPort"),
            proxyAuthenticator = repaired.boolean("proxyAuthenticator", defaults.proxyAuthenticator),
            parseMode = repaired.enumString("parseMode", defaults.parseMode),
        )
    }

    private fun repairFeishuSetting(setting: FeishuSetting): FeishuSetting {
        val defaults = FeishuSetting()
        var webhook = setting.webhook
        var secret = setting.secret
        var msgType = setting.msgType

        if (!isUrlLike(webhook) && isUrlLike(secret)) {
            webhook = secret
            secret = ""
            if (isLikelySecret(msgType)) {
                secret = msgType
            }
            msgType = defaults.msgType
        }

        val repaired = repairFields(
            "webhook" to webhook,
            "secret" to secret,
            "msgType" to msgType,
        )
        return setting.copy(
            webhook = repaired.string("webhook"),
            secret = repaired.string("secret"),
            msgType = repaired.enumString("msgType", defaults.msgType),
        )
    }

    private fun repairWebhookSetting(setting: WebhookSetting): WebhookSetting {
        val defaults = WebhookSetting()
        var method = setting.method
        var webServer = setting.webServer
        var secret = setting.secret
        var response = setting.response

        if (!isHttpMethod(method) && isHttpMethod(webServer)) {
            method = webServer
            webServer = ""
        }
        if (!isUrlLike(webServer) && isUrlLike(secret)) {
            webServer = secret
            secret = ""
            if (isLikelySecret(response)) {
                secret = response
                response = ""
            }
        }

        val repaired = repairFields(
            "method" to method,
            "webServer" to webServer,
            "secret" to secret,
            "response" to response,
            "proxyType" to setting.proxyType,
            "proxyHost" to setting.proxyHost,
            "proxyPort" to setting.proxyPort,
            "proxyAuthenticator" to setting.proxyAuthenticator,
        )
        return setting.copy(
            method = repaired.enumString("method", defaults.method),
            webServer = repaired.string("webServer"),
            secret = repaired.string("secret"),
            response = repaired.string("response"),
            proxyType = repaired.proxy("proxyType"),
            proxyHost = repaired.string("proxyHost"),
            proxyPort = repaired.string("proxyPort"),
            proxyAuthenticator = repaired.boolean("proxyAuthenticator", defaults.proxyAuthenticator),
        )
    }

    private fun repairFields(vararg values: Pair<String, Any?>): RepairedFields {
        val names = values.map { it.first }
        val repaired = LinkedHashMap<String, Any?>()
        values.forEach { (name, value) -> repaired[name] = value }

        names.forEachIndexed { targetIndex, targetName ->
            val targetValue = repaired[targetName]
            if (!needsRepair(targetName, targetValue)) return@forEachIndexed
            for (sourceIndex in targetIndex + 1 until names.size) {
                val sourceName = names[sourceIndex]
                val sourceValue = repaired[sourceName]
                if (isBlankValue(sourceValue)) continue
                if (matchesField(targetName, sourceValue) && !matchesField(sourceName, sourceValue)) {
                    repaired[targetName] = sourceValue
                    repaired[sourceName] = blankReplacement(sourceValue)
                    break
                }
            }
        }
        return RepairedFields(repaired)
    }

    private fun needsRepair(fieldName: String, value: Any?): Boolean {
        return hasStrongValidator(fieldName) && !matchesField(fieldName, value)
    }

    private fun hasStrongValidator(fieldName: String): Boolean {
        return when (fieldName) {
            "method", "msgtype", "msgType", "msgKey", "contentType", "recvType", "parseMode", "proxyType", "receiveIdType",
            "encryptionProtocol", "transformation", "level", "uriType", "priority",
            "server", "webServer", "webhook", "webHook", "customizeAPI", "apiBase", "callbackUrl", "url", "website",
            "authEmail", "fromEmail", "toEmail", "host", "port", "proxyPort", "simSlot", "qos",
            "ssl", "startTls", "atAll", "proxyAuthenticator", "retained", "onlyNoNetwork",
            "apiToken", "chatId", "messageThreadId", "token", "secret", "sendKey",
            "appSecret", "appKey", "appId", "corpID", "agentID", "receiveId" -> true
            else -> false
        }
    }

    private fun matchesField(fieldName: String, value: Any?): Boolean {
        return when (fieldName) {
            "method" -> isHttpMethod(value) || isSocketMethod(value)
            "msgtype", "msgType", "msgKey" -> isMessageType(value)
            "contentType" -> safeString(value).trim() in setOf("text", "markdown")
            "recvType" -> normalized(value) in setOf("user", "group")
            "parseMode" -> normalized(value) in setOf("html", "markdownv2")
            "proxyType" -> safeString(value).trim().uppercase(Locale.ROOT) in setOf("DIRECT", "HTTP", "SOCKS") ||
                value is Proxy.Type
            "receiveIdType" -> normalized(value) in setOf("user_id", "open_id", "union_id", "email", "chat_id")
            "encryptionProtocol" -> safeString(value) in setOf("Plain", "S/MIME", "OpenPGP")
            "transformation" -> safeString(value) in setOf("none", "AES/GCM/NoPadding", "AES/CBC/PKCS5Padding")
            "level" -> normalized(value) in setOf("active", "time-sensitive", "timesensitive", "passive", "critical")
            "uriType" -> normalized(value) in setOf("tcp", "ssl", "ws", "wss")
            "priority" -> safeString(value).trim().toIntOrNull() in 1..5
            "server", "webServer", "webhook", "webHook", "customizeAPI", "apiBase", "callbackUrl", "url" -> isUrlLike(value)
            "website" -> isUrlLike(value) || isHostLike(value)
            "authEmail", "fromEmail", "toEmail" -> isEmailLike(value)
            "host" -> isHostLike(value)
            "port", "proxyPort" -> isPortLike(value)
            "simSlot", "qos" -> safeString(value).trim().toIntOrNull() != null || value is Number
            "ssl", "startTls", "atAll", "proxyAuthenticator", "retained", "onlyNoNetwork" -> isBooleanLike(value)
            "apiToken" -> isTelegramBotToken(value)
            "chatId" -> isTelegramChatId(value)
            "messageThreadId" -> isTelegramThreadId(value)
            "token", "sendKey", "appSecret", "appKey", "appId", "receiveId" -> isLikelyToken(value)
            "secret" -> isLikelySecret(value)
            "corpID" -> safeString(value).trim().startsWith("ww", ignoreCase = true)
            "agentID" -> safeString(value).trim().toLongOrNull() != null
            else -> false
        }
    }

    private fun blankReplacement(value: Any?): Any? {
        return when (value) {
            is Boolean -> false
            is Number -> 0
            is Proxy.Type -> Proxy.Type.DIRECT
            else -> ""
        }
    }

    private fun isBlankValue(value: Any?): Boolean {
        return when (value) {
            null -> true
            is String -> value.isBlank()
            is Map<*, *> -> value.isEmpty()
            is Iterable<*> -> !value.iterator().hasNext()
            is Array<*> -> value.isEmpty()
            else -> false
        }
    }

    private fun isHttpMethod(value: Any?): Boolean {
        return safeString(value).trim().uppercase(Locale.ROOT) in setOf("GET", "POST", "PUT", "PATCH")
    }

    private fun isSocketMethod(value: Any?): Boolean {
        return safeString(value).trim().uppercase(Locale.ROOT) in setOf("TCP", "UDP", "MQTT")
    }

    private fun isMessageType(value: Any?): Boolean {
        return safeString(value).trim() in setOf("text", "markdown", "interactive", "sampleText", "sampleMarkdown")
    }

    private fun isUrlLike(value: Any?): Boolean {
        val text = safeString(value).trim()
        return text.startsWith("https://", ignoreCase = true) ||
            text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("bark://", ignoreCase = true)
    }

    private fun isHostLike(value: Any?): Boolean {
        val text = safeString(value).trim()
        if (text.isBlank() || text.any { it.isWhitespace() }) return false
        if (isUrlLike(text)) return true
        return '.' in text && !text.startsWith(".") && !text.endsWith(".")
    }

    private fun isEmailLike(value: Any?): Boolean {
        val text = safeString(value).trim()
        val atIndex = text.indexOf('@')
        return atIndex > 0 && atIndex < text.lastIndex && '.' in text.substring(atIndex + 1)
    }

    private fun isPortLike(value: Any?): Boolean {
        val port = safeString(value).trim().toIntOrNull() ?: return value is Number
        return port in 1..65535
    }

    private fun isBooleanLike(value: Any?): Boolean {
        return when (value) {
            is Boolean -> true
            is Number -> value.toInt() == 0 || value.toInt() == 1
            is String -> normalized(value) in setOf("1", "0", "true", "false", "yes", "no", "y", "n", "on", "off")
            else -> false
        }
    }

    private fun isTelegramBotToken(value: Any?): Boolean {
        val text = safeString(value).trim()
        val split = text.split(':', limit = 2)
        if (split.size != 2) return false
        return split[0].all { it.isDigit() } &&
            split[0].length >= TELEGRAM_BOT_ID_MIN_LENGTH &&
            split[1].length >= TELEGRAM_BOT_SECRET_MIN_LENGTH
    }

    private fun isTelegramChatId(value: Any?): Boolean {
        val text = safeString(value).trim()
        return text.startsWith("@") && text.length > 1 ||
            text.toLongOrNull() != null
    }

    private fun isTelegramThreadId(value: Any?): Boolean {
        val text = safeString(value).trim()
        return text.isBlank() || text.toLongOrNull() != null
    }

    private fun isLikelyToken(value: Any?): Boolean {
        val text = safeString(value).trim()
        if (text.length < 6 || text.any { it.isWhitespace() }) return false
        return !isUrlLike(text) &&
            !isHttpMethod(text) &&
            !isSocketMethod(text) &&
            !isMessageType(text) &&
            !text.startsWith("{") &&
            !text.startsWith("[")
    }

    private fun isLikelySecret(value: Any?): Boolean {
        return isLikelyToken(value) && !isTelegramChatId(value)
    }

    private fun normalized(value: Any?): String = safeString(value).trim().lowercase(Locale.ROOT)

    private class RepairedFields(private val values: Map<String, Any?>) {
        fun string(name: String): String = safeString(values[name])

        fun enumString(name: String, defaultValue: String): String {
            val value = values[name]
            return if (matchesField(name, value)) safeString(value).ifBlank { defaultValue } else defaultValue
        }

        fun boolean(name: String, defaultValue: Boolean): Boolean = safeBoolean(values[name], defaultValue)

        fun int(name: String, defaultValue: Int): Int = safeInt(values[name], defaultValue)

        fun proxy(name: String): Proxy.Type = safeProxyType(values[name])
    }

    private fun <T> parseSetting(json: String, serializer: KSerializer<T>): T? {
        return SenderSettingJson.decodeOrNull(serializer, json)
    }

    private fun parseSettingJson(json: String): JsonObject? {
        return SenderSettingJson.parseObject(json)
    }

    private fun resolveValue(primary: Any?, rawJson: JsonObject?, vararg names: String): Any? {
        return primary ?: fieldValue(rawJson, *names)
    }

    private fun fieldValue(rawJson: JsonObject?, vararg names: String): Any? {
        val element = firstFieldElement(rawJson, *names) ?: return null
        return jsonElementToAny(element)
    }

    private fun firstFieldElement(rawJson: JsonObject?, vararg names: String): JsonElement? {
        if (rawJson == null) return null
        names.forEach { name ->
            val element = rawJson[name] ?: return@forEach
            if (element is JsonNull) return@forEach
            return element
        }
        return null
    }

    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            JsonNull -> null
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.booleanOrNull
                element.intOrNull != null -> element.intOrNull
                element.longOrNull != null -> element.longOrNull
                element.doubleOrNull != null -> element.doubleOrNull
                else -> element.content
            }
            is JsonArray -> element.map { child -> jsonElementToAny(child) }
            is JsonObject -> buildMap {
                element.forEach { (key, value) ->
                    put(key, jsonElementToAny(value))
                }
            }
        }
    }
}
