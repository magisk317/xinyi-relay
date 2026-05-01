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
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.engine.sender.SenderType
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.Proxy

object SenderSettingSanitizer {
    private val gson = Gson()

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
        return when (type) {
            SenderType.DINGTALK_GROUP_ROBOT -> gson.toJson(
                sanitizeDingtalkGroupRobotSetting(parseSetting(json, DingtalkGroupRobotSetting::class.java), rawJson),
            )
            SenderType.EMAIL -> gson.toJson(
                sanitizeEmailSetting(parseSetting(json, EmailSetting::class.java), rawJson),
            )
            SenderType.BARK -> gson.toJson(
                sanitizeBarkSetting(parseSetting(json, BarkSetting::class.java), rawJson),
            )
            SenderType.WEBHOOK -> gson.toJson(
                sanitizeWebhookSetting(parseSetting(json, WebhookSetting::class.java), rawJson),
            )
            SenderType.WEWORK_ROBOT -> gson.toJson(
                sanitizeWeworkRobotSetting(parseSetting(json, WeworkRobotSetting::class.java), rawJson),
            )
            SenderType.WEWORK_AGENT -> gson.toJson(
                sanitizeWeworkAgentSetting(parseSetting(json, WeworkAgentSetting::class.java), rawJson),
            )
            SenderType.SERVERCHAN -> gson.toJson(
                sanitizeServerchanSetting(parseSetting(json, ServerchanSetting::class.java), rawJson),
            )
            SenderType.PUSHPLUS -> gson.toJson(
                sanitizePushplusSetting(parseSetting(json, PushplusSetting::class.java), rawJson),
            )
            SenderType.TELEGRAM -> gson.toJson(
                sanitizeTelegramSetting(parseSetting(json, TelegramSetting::class.java), rawJson),
            )
            SenderType.SMS -> gson.toJson(
                sanitizeSmsSetting(parseSetting(json, SmsSetting::class.java), rawJson),
            )
            SenderType.FEISHU -> gson.toJson(
                sanitizeFeishuSetting(parseSetting(json, FeishuSetting::class.java), rawJson),
            )
            SenderType.GOTIFY -> gson.toJson(
                sanitizeGotifySetting(parseSetting(json, GotifySetting::class.java), rawJson),
            )
            SenderType.DINGTALK_INNER_ROBOT -> gson.toJson(
                sanitizeDingtalkInnerRobotSetting(parseSetting(json, DingtalkInnerRobotSetting::class.java), rawJson),
            )
            SenderType.FEISHU_APP -> gson.toJson(
                sanitizeFeishuAppSetting(parseSetting(json, FeishuAppSetting::class.java), rawJson),
            )
            SenderType.URL_SCHEME -> gson.toJson(
                sanitizeUrlSchemeSetting(parseSetting(json, UrlSchemeSetting::class.java), rawJson),
            )
            SenderType.SOCKET -> gson.toJson(
                sanitizeSocketSetting(parseSetting(json, SocketSetting::class.java), rawJson),
            )
            SenderType.NTFY -> gson.toJson(
                sanitizeNtfySetting(parseSetting(json, NtfySetting::class.java), rawJson),
            )
            else -> if (json.isBlank()) "" else json
        }
    }

    fun sanitizeDingtalkGroupRobotSetting(
        raw: DingtalkGroupRobotSetting?,
        rawJson: JsonObject? = null,
    ): DingtalkGroupRobotSetting {
        val defaults = DingtalkGroupRobotSetting()
        return DingtalkGroupRobotSetting(
            token = safeString(resolveValue(raw?.token, rawJson, "token")),
            secret = safeString(resolveValue(raw?.secret, rawJson, "secret")),
            atAll = safeBoolean(resolveValue(raw?.atAll, rawJson, "atAll"), defaults.atAll),
            atMobiles = safeString(resolveValue(raw?.atMobiles, rawJson, "atMobiles")),
            atDingtalkIds = safeString(resolveValue(raw?.atDingtalkIds, rawJson, "atDingtalkIds")),
            msgtype = safeString(resolveValue(raw?.msgtype, rawJson, "msgtype")).ifBlank { defaults.msgtype },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
        )
    }

    fun sanitizeEmailSetting(raw: EmailSetting?, rawJson: JsonObject? = null): EmailSetting {
        val defaults = EmailSetting()
        val fromEmail = safeString(resolveValue(raw?.fromEmail, rawJson, "fromEmail"))
        val authEmail = safeString(resolveValue(raw?.authEmail, rawJson, "authEmail")).ifBlank { fromEmail }
        val alias = safeString(resolveValue(raw?.fromEmailAlias, rawJson, "fromEmailAlias"))
            .ifBlank { safeString(resolveValue(raw?.nickname, rawJson, "nickname")) }
        return EmailSetting(
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
    }

    fun sanitizeBarkSetting(raw: BarkSetting?, rawJson: JsonObject? = null): BarkSetting {
        val defaults = BarkSetting()
        return BarkSetting(
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
    }

    fun sanitizeWebhookSetting(raw: WebhookSetting?, rawJson: JsonObject? = null): WebhookSetting {
        val defaults = WebhookSetting()
        return WebhookSetting(
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
    }

    fun sanitizeWeworkRobotSetting(raw: WeworkRobotSetting?, rawJson: JsonObject? = null): WeworkRobotSetting {
        val defaults = WeworkRobotSetting()
        return WeworkRobotSetting(
            webHook = safeString(resolveValue(raw?.webHook, rawJson, "webHook")),
            msgType = safeString(resolveValue(raw?.msgType, rawJson, "msgType")).ifBlank { defaults.msgType },
            atAll = safeBoolean(resolveValue(raw?.atAll, rawJson, "atAll"), defaults.atAll),
            atUserIds = safeString(resolveValue(raw?.atUserIds, rawJson, "atUserIds")),
            atMobiles = safeString(resolveValue(raw?.atMobiles, rawJson, "atMobiles")),
        )
    }

    fun sanitizeWeworkAgentSetting(raw: WeworkAgentSetting?, rawJson: JsonObject? = null): WeworkAgentSetting {
        val defaults = WeworkAgentSetting()
        return WeworkAgentSetting(
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
    }

    fun sanitizeServerchanSetting(raw: ServerchanSetting?, rawJson: JsonObject? = null): ServerchanSetting {
        return ServerchanSetting(
            sendKey = safeString(resolveValue(raw?.sendKey, rawJson, "sendKey")),
            channel = safeString(resolveValue(raw?.channel, rawJson, "channel")),
            openid = safeString(resolveValue(raw?.openid, rawJson, "openid")),
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
        )
    }

    fun sanitizePushplusSetting(raw: PushplusSetting?, rawJson: JsonObject? = null): PushplusSetting {
        val defaults = PushplusSetting()
        return PushplusSetting(
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
    }

    fun sanitizeTelegramSetting(raw: TelegramSetting?, rawJson: JsonObject? = null): TelegramSetting {
        val defaults = TelegramSetting()
        return TelegramSetting(
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
    }

    fun sanitizeSmsSetting(raw: SmsSetting?, rawJson: JsonObject? = null): SmsSetting {
        val defaults = SmsSetting()
        return SmsSetting(
            simSlot = safeInt(resolveValue(raw?.simSlot, rawJson, "simSlot"), defaults.simSlot),
            mobiles = safeString(resolveValue(raw?.mobiles, rawJson, "mobiles")),
            onlyNoNetwork = safeBoolean(
                resolveValue(raw?.onlyNoNetwork, rawJson, "onlyNoNetwork"),
                defaults.onlyNoNetwork,
            ),
        )
    }

    fun sanitizeFeishuSetting(raw: FeishuSetting?, rawJson: JsonObject? = null): FeishuSetting {
        val defaults = FeishuSetting()
        return FeishuSetting(
            webhook = safeString(resolveValue(raw?.webhook, rawJson, "webhook")),
            secret = safeString(resolveValue(raw?.secret, rawJson, "secret")),
            msgType = safeString(resolveValue(raw?.msgType, rawJson, "msgType")).ifBlank { defaults.msgType },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
            messageCard = safeString(resolveValue(raw?.messageCard, rawJson, "messageCard")),
        )
    }

    fun sanitizeGotifySetting(raw: GotifySetting?, rawJson: JsonObject? = null): GotifySetting {
        return GotifySetting(
            webServer = safeString(resolveValue(raw?.webServer, rawJson, "webServer")),
            title = safeString(resolveValue(raw?.title, rawJson, "title")),
            priority = safeString(resolveValue(raw?.priority, rawJson, "priority")),
        )
    }

    fun sanitizeNtfySetting(raw: NtfySetting?, rawJson: JsonObject? = null): NtfySetting {
        val defaults = NtfySetting()
        return NtfySetting(
            server = safeString(resolveValue(raw?.server, rawJson, "server")),
            topic = safeString(resolveValue(raw?.topic, rawJson, "topic")),
            token = safeString(resolveValue(raw?.token, rawJson, "token")),
            title = safeString(resolveValue(raw?.title, rawJson, "title")),
            priority = safeString(resolveValue(raw?.priority, rawJson, "priority")).ifBlank { defaults.priority },
            tags = safeString(resolveValue(raw?.tags, rawJson, "tags")),
        )
    }

    fun sanitizeDingtalkInnerRobotSetting(
        raw: DingtalkInnerRobotSetting?,
        rawJson: JsonObject? = null,
    ): DingtalkInnerRobotSetting {
        val defaults = DingtalkInnerRobotSetting()
        return DingtalkInnerRobotSetting(
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
    }

    fun sanitizeFeishuAppSetting(raw: FeishuAppSetting?, rawJson: JsonObject? = null): FeishuAppSetting {
        val defaults = FeishuAppSetting()
        return FeishuAppSetting(
            appId = safeString(resolveValue(raw?.appId, rawJson, "appId")),
            appSecret = safeString(resolveValue(raw?.appSecret, rawJson, "appSecret")),
            receiveId = safeString(resolveValue(raw?.receiveId, rawJson, "receiveId")),
            msgType = safeString(resolveValue(raw?.msgType, rawJson, "msgType")).ifBlank { defaults.msgType },
            titleTemplate = safeString(resolveValue(raw?.titleTemplate, rawJson, "titleTemplate")),
            receiveIdType = safeString(resolveValue(raw?.receiveIdType, rawJson, "receiveIdType"))
                .ifBlank { defaults.receiveIdType },
            messageCard = safeString(resolveValue(raw?.messageCard, rawJson, "messageCard")),
        )
    }

    fun sanitizeUrlSchemeSetting(raw: UrlSchemeSetting?, rawJson: JsonObject? = null): UrlSchemeSetting {
        return UrlSchemeSetting(
            urlScheme = safeString(resolveValue(raw?.urlScheme, rawJson, "urlScheme")),
        )
    }

    fun sanitizeSocketSetting(raw: SocketSetting?, rawJson: JsonObject? = null): SocketSetting {
        val defaults = SocketSetting()
        return SocketSetting(
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

    private fun <T> parseSetting(json: String, clazz: Class<T>): T? {
        if (json.isBlank()) return null
        return runCatching { gson.fromJson(json, clazz) }.getOrNull()
    }

    private fun parseSettingJson(json: String): JsonObject? {
        if (json.isBlank()) return null
        return runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()
    }

    private fun resolveValue(primary: Any?, rawJson: JsonObject?, vararg names: String): Any? {
        return primary ?: fieldValue(rawJson, *names)
    }

    private fun fieldValue(rawJson: JsonObject?, vararg names: String): Any? {
        if (rawJson == null) return null
        names.forEach { name ->
            val element = rawJson.get(name) ?: return@forEach
            if (element.isJsonNull) return null
            return jsonElementToAny(element)
        }
        return null
    }

    private fun jsonElementToAny(element: JsonElement): Any? {
        return when {
            element.isJsonNull -> null
            element.isJsonPrimitive -> {
                val primitive = element.asJsonPrimitive
                when {
                    primitive.isBoolean -> primitive.asBoolean
                    primitive.isNumber -> runCatching { primitive.asInt }
                        .recoverCatching { primitive.asLong }
                        .recoverCatching { primitive.asDouble }
                        .getOrNull()
                    primitive.isString -> primitive.asString
                    else -> primitive.toString()
                }
            }
            element.isJsonArray -> element.asJsonArray.map { child -> jsonElementToAny(child) }
            element.isJsonObject -> buildMap {
                element.asJsonObject.entrySet().forEach { (key, value) ->
                    put(key, jsonElementToAny(value))
                }
            }
            else -> null
        }
    }
}
