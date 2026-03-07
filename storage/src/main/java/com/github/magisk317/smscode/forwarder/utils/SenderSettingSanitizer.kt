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
import java.net.Proxy

object SenderSettingSanitizer {
    private val gson = Gson()

    fun sanitizeSenderLenient(sender: Sender): Sender {
        val safeJson = sanitizeJsonLenient(sender.type, sender.jsonSetting)
        return if (safeJson == sender.jsonSetting) sender else sender.copy(jsonSetting = safeJson)
    }

    fun sanitizeJsonLenient(type: Int, json: String): String {
        return when (type) {
            SenderType.DINGTALK_GROUP_ROBOT -> gson.toJson(
                sanitizeDingtalkGroupRobotSetting(parseSetting(json, DingtalkGroupRobotSetting::class.java)),
            )
            SenderType.EMAIL -> gson.toJson(
                sanitizeEmailSetting(parseSetting(json, EmailSetting::class.java)),
            )
            SenderType.BARK -> gson.toJson(
                sanitizeBarkSetting(parseSetting(json, BarkSetting::class.java)),
            )
            SenderType.WEBHOOK -> gson.toJson(
                sanitizeWebhookSetting(parseSetting(json, WebhookSetting::class.java)),
            )
            SenderType.WEWORK_ROBOT -> gson.toJson(
                sanitizeWeworkRobotSetting(parseSetting(json, WeworkRobotSetting::class.java)),
            )
            SenderType.WEWORK_AGENT -> gson.toJson(
                sanitizeWeworkAgentSetting(parseSetting(json, WeworkAgentSetting::class.java)),
            )
            SenderType.SERVERCHAN -> gson.toJson(
                sanitizeServerchanSetting(parseSetting(json, ServerchanSetting::class.java)),
            )
            SenderType.PUSHPLUS -> gson.toJson(
                sanitizePushplusSetting(parseSetting(json, PushplusSetting::class.java)),
            )
            SenderType.TELEGRAM -> gson.toJson(
                sanitizeTelegramSetting(parseSetting(json, TelegramSetting::class.java)),
            )
            SenderType.SMS -> gson.toJson(
                sanitizeSmsSetting(parseSetting(json, SmsSetting::class.java)),
            )
            SenderType.FEISHU -> gson.toJson(
                sanitizeFeishuSetting(parseSetting(json, FeishuSetting::class.java)),
            )
            SenderType.GOTIFY -> gson.toJson(
                sanitizeGotifySetting(parseSetting(json, GotifySetting::class.java)),
            )
            SenderType.DINGTALK_INNER_ROBOT -> gson.toJson(
                sanitizeDingtalkInnerRobotSetting(parseSetting(json, DingtalkInnerRobotSetting::class.java)),
            )
            SenderType.FEISHU_APP -> gson.toJson(
                sanitizeFeishuAppSetting(parseSetting(json, FeishuAppSetting::class.java)),
            )
            SenderType.URL_SCHEME -> gson.toJson(
                sanitizeUrlSchemeSetting(parseSetting(json, UrlSchemeSetting::class.java)),
            )
            SenderType.SOCKET -> gson.toJson(
                sanitizeSocketSetting(parseSetting(json, SocketSetting::class.java)),
            )
            else -> if (json.isBlank()) "" else json
        }
    }

    fun sanitizeDingtalkGroupRobotSetting(raw: DingtalkGroupRobotSetting?): DingtalkGroupRobotSetting {
        val defaults = DingtalkGroupRobotSetting()
        return DingtalkGroupRobotSetting(
            token = safeString(raw?.token),
            secret = safeString(raw?.secret),
            atAll = safeBoolean(raw?.atAll, defaults.atAll),
            atMobiles = safeString(raw?.atMobiles),
            atDingtalkIds = safeString(raw?.atDingtalkIds),
            msgtype = safeString(raw?.msgtype).ifBlank { defaults.msgtype },
            titleTemplate = safeString(raw?.titleTemplate),
        )
    }

    fun sanitizeEmailSetting(raw: EmailSetting?): EmailSetting {
        val defaults = EmailSetting()
        return EmailSetting(
            mailType = safeString(raw?.mailType),
            fromEmail = safeString(raw?.fromEmail),
            pwd = safeString(raw?.pwd),
            nickname = safeString(raw?.nickname),
            host = safeString(raw?.host),
            port = safeString(raw?.port),
            ssl = safeBoolean(raw?.ssl, defaults.ssl),
            startTls = safeBoolean(raw?.startTls, defaults.startTls),
            title = safeString(raw?.title),
            recipients = safeEmailRecipients(raw?.recipients),
            toEmail = safeString(raw?.toEmail),
            keystore = safeString(raw?.keystore),
            password = safeString(raw?.password),
            encryptionProtocol = safeString(raw?.encryptionProtocol).ifBlank { defaults.encryptionProtocol },
            fromEmailAlias = safeString(raw?.fromEmailAlias),
        )
    }

    fun sanitizeBarkSetting(raw: BarkSetting?): BarkSetting {
        val defaults = BarkSetting()
        return BarkSetting(
            server = safeString(raw?.server),
            group = safeString(raw?.group),
            icon = safeString(raw?.icon),
            sound = safeString(raw?.sound),
            badge = safeString(raw?.badge),
            url = safeString(raw?.url),
            level = safeString(raw?.level).ifBlank { defaults.level },
            title = safeString(raw?.title),
            transformation = safeString(raw?.transformation).ifBlank { defaults.transformation },
            key = safeString(raw?.key),
            iv = safeString(raw?.iv),
            call = safeString(raw?.call),
            autoCopy = safeString(raw?.autoCopy),
        )
    }

    fun sanitizeWebhookSetting(raw: WebhookSetting?): WebhookSetting {
        val defaults = WebhookSetting()
        return WebhookSetting(
            method = safeString(raw?.method).ifBlank { defaults.method },
            webServer = safeString(raw?.webServer),
            secret = safeString(raw?.secret),
            response = safeString(raw?.response),
            webParams = safeString(raw?.webParams),
            headers = safeMapStringString(raw?.headers),
            proxyType = safeProxyType(raw?.proxyType),
            proxyHost = safeString(raw?.proxyHost),
            proxyPort = safeString(raw?.proxyPort),
            proxyAuthenticator = safeBoolean(raw?.proxyAuthenticator, defaults.proxyAuthenticator),
            proxyUsername = safeString(raw?.proxyUsername),
            proxyPassword = safeString(raw?.proxyPassword),
        )
    }

    fun sanitizeWeworkRobotSetting(raw: WeworkRobotSetting?): WeworkRobotSetting {
        val defaults = WeworkRobotSetting()
        return WeworkRobotSetting(
            webHook = safeString(raw?.webHook),
            msgType = safeString(raw?.msgType).ifBlank { defaults.msgType },
            atAll = safeBoolean(raw?.atAll, defaults.atAll),
            atUserIds = safeString(raw?.atUserIds),
            atMobiles = safeString(raw?.atMobiles),
        )
    }

    fun sanitizeWeworkAgentSetting(raw: WeworkAgentSetting?): WeworkAgentSetting {
        val defaults = WeworkAgentSetting()
        return WeworkAgentSetting(
            corpID = safeString(raw?.corpID),
            agentID = safeString(raw?.agentID),
            secret = safeString(raw?.secret),
            atAll = safeBoolean(raw?.atAll, defaults.atAll),
            toUser = safeString(raw?.toUser).ifBlank { defaults.toUser },
            toParty = safeString(raw?.toParty),
            toTag = safeString(raw?.toTag),
            proxyType = safeProxyType(raw?.proxyType),
            proxyHost = safeString(raw?.proxyHost),
            proxyPort = safeString(raw?.proxyPort),
            proxyAuthenticator = safeBoolean(raw?.proxyAuthenticator, defaults.proxyAuthenticator),
            proxyUsername = safeString(raw?.proxyUsername),
            proxyPassword = safeString(raw?.proxyPassword),
            customizeAPI = safeString(raw?.customizeAPI).ifBlank { defaults.customizeAPI },
        )
    }

    fun sanitizeServerchanSetting(raw: ServerchanSetting?): ServerchanSetting {
        return ServerchanSetting(
            sendKey = safeString(raw?.sendKey),
            channel = safeString(raw?.channel),
            openid = safeString(raw?.openid),
            titleTemplate = safeString(raw?.titleTemplate),
        )
    }

    fun sanitizePushplusSetting(raw: PushplusSetting?): PushplusSetting {
        val defaults = PushplusSetting()
        return PushplusSetting(
            website = safeString(raw?.website).ifBlank { defaults.website },
            token = safeString(raw?.token),
            topic = safeString(raw?.topic),
            template = safeString(raw?.template),
            channel = safeString(raw?.channel),
            webhook = safeString(raw?.webhook),
            callbackUrl = safeString(raw?.callbackUrl),
            validTime = safeString(raw?.validTime),
            titleTemplate = safeString(raw?.titleTemplate),
        )
    }

    fun sanitizeTelegramSetting(raw: TelegramSetting?): TelegramSetting {
        val defaults = TelegramSetting()
        return TelegramSetting(
            method = safeString(raw?.method).ifBlank { defaults.method },
            apiToken = safeString(raw?.apiToken),
            chatId = safeString(raw?.chatId),
            messageThreadId = safeString(raw?.messageThreadId),
            proxyType = safeProxyType(raw?.proxyType),
            proxyHost = safeString(raw?.proxyHost),
            proxyPort = safeString(raw?.proxyPort),
            proxyAuthenticator = safeBoolean(raw?.proxyAuthenticator, defaults.proxyAuthenticator),
            proxyUsername = safeString(raw?.proxyUsername),
            proxyPassword = safeString(raw?.proxyPassword),
            parseMode = safeString(raw?.parseMode).ifBlank { defaults.parseMode },
        )
    }

    fun sanitizeSmsSetting(raw: SmsSetting?): SmsSetting {
        val defaults = SmsSetting()
        return SmsSetting(
            simSlot = safeInt(raw?.simSlot, defaults.simSlot),
            mobiles = safeString(raw?.mobiles),
            onlyNoNetwork = safeBoolean(raw?.onlyNoNetwork, defaults.onlyNoNetwork),
        )
    }

    fun sanitizeFeishuSetting(raw: FeishuSetting?): FeishuSetting {
        val defaults = FeishuSetting()
        return FeishuSetting(
            webhook = safeString(raw?.webhook),
            secret = safeString(raw?.secret),
            msgType = safeString(raw?.msgType).ifBlank { defaults.msgType },
            titleTemplate = safeString(raw?.titleTemplate),
            messageCard = safeString(raw?.messageCard),
        )
    }

    fun sanitizeGotifySetting(raw: GotifySetting?): GotifySetting {
        return GotifySetting(
            webServer = safeString(raw?.webServer),
            title = safeString(raw?.title),
            priority = safeString(raw?.priority),
        )
    }

    fun sanitizeDingtalkInnerRobotSetting(raw: DingtalkInnerRobotSetting?): DingtalkInnerRobotSetting {
        val defaults = DingtalkInnerRobotSetting()
        return DingtalkInnerRobotSetting(
            agentID = safeString(raw?.agentID),
            appKey = safeString(raw?.appKey),
            appSecret = safeString(raw?.appSecret),
            userIds = safeString(raw?.userIds),
            msgKey = safeString(raw?.msgKey).ifBlank { defaults.msgKey },
            titleTemplate = safeString(raw?.titleTemplate),
            proxyType = safeProxyType(raw?.proxyType),
            proxyHost = safeString(raw?.proxyHost),
            proxyPort = safeString(raw?.proxyPort),
            proxyAuthenticator = safeBoolean(raw?.proxyAuthenticator, defaults.proxyAuthenticator),
            proxyUsername = safeString(raw?.proxyUsername),
            proxyPassword = safeString(raw?.proxyPassword),
        )
    }

    fun sanitizeFeishuAppSetting(raw: FeishuAppSetting?): FeishuAppSetting {
        val defaults = FeishuAppSetting()
        return FeishuAppSetting(
            appId = safeString(raw?.appId),
            appSecret = safeString(raw?.appSecret),
            receiveId = safeString(raw?.receiveId),
            msgType = safeString(raw?.msgType).ifBlank { defaults.msgType },
            titleTemplate = safeString(raw?.titleTemplate),
            receiveIdType = safeString(raw?.receiveIdType).ifBlank { defaults.receiveIdType },
            messageCard = safeString(raw?.messageCard),
        )
    }

    fun sanitizeUrlSchemeSetting(raw: UrlSchemeSetting?): UrlSchemeSetting {
        return UrlSchemeSetting(
            urlScheme = safeString(raw?.urlScheme),
        )
    }

    fun sanitizeSocketSetting(raw: SocketSetting?): SocketSetting {
        val defaults = SocketSetting()
        return SocketSetting(
            method = safeString(raw?.method).ifBlank { defaults.method },
            address = safeString(raw?.address),
            port = safeInt(raw?.port, defaults.port),
            msgTemplate = safeString(raw?.msgTemplate),
            secret = safeString(raw?.secret),
            response = safeString(raw?.response),
            username = safeString(raw?.username),
            password = safeString(raw?.password),
            inCharset = safeString(raw?.inCharset),
            outCharset = safeString(raw?.outCharset),
            inMessageTopic = safeString(raw?.inMessageTopic),
            outMessageTopic = safeString(raw?.outMessageTopic),
            uriType = safeString(raw?.uriType).ifBlank { defaults.uriType },
            path = safeString(raw?.path),
            clientId = safeString(raw?.clientId),
            qos = safeInt(raw?.qos, defaults.qos),
            retained = safeBoolean(raw?.retained, defaults.retained),
        )
    }

    fun safeString(any: Any?): String {
        return when (any) {
            null -> ""
            is String -> any
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

    private fun <T> parseSetting(json: String, clazz: Class<T>): T? {
        if (json.isBlank()) return null
        return runCatching { gson.fromJson(json, clazz) }.getOrNull()
    }
}
