package io.github.magisk317.relay.desktop.config

import io.github.magisk317.relay.contract.model.SenderActiveSchedule
import io.github.magisk317.relay.contract.model.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.contract.model.SnapshotSender
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.sender.SenderSettingFieldMetadata
import io.github.magisk317.relay.sender.SenderSettingFieldType
import io.github.magisk317.relay.sender.SenderSettingSchemas
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
/**
 * Desktop port of frontend/shared/senderDefaults.ts: the structured sender
 * field schemas plus the form/JSON conversions the console uses to edit a
 * sender without touching raw JSON by hand.
 *
 * Field defaults and option lists come from the shared Kotlin contract
 * (SenderSettingSchemas, the source senderSchemas.json is generated from), so
 * the desktop, the webUI and the device all agree on the same payload shape.
 */

/** Editing kinds the webUI sender field editor renders. */
enum class SenderFieldKind { TEXT, TEXTAREA, NUMBER, BOOLEAN, SELECT, JSON }

/** Locale triple mirroring the webUI's LocalizedText. */
data class SenderLocalizedText(val en: String, val zhCn: String, val zhTw: String = zhCn)

/** One structured field of a sender, with its label and option list. */
data class SenderFieldSchema(
    val key: String,
    val kind: SenderFieldKind,
    val label: SenderLocalizedText,
    val rows: Int? = null,
    val fullWidth: Boolean = false,
    val options: List<SenderSelectOption> = emptyList(),
)

/** Select choice with a localized label, like the webUI's field options. */
data class SenderSelectOption(val value: String, val label: SenderLocalizedText)

/** Resolves a localized string the same way the webUI's resolveSenderText does. */
fun resolveSenderText(locale: DesktopLocale, text: SenderLocalizedText): String = when (locale) {
    DesktopLocale.EN -> text.en
    DesktopLocale.ZH_CN -> text.zhCn
    DesktopLocale.ZH_TW -> text.zhTw
}

private fun field(
    key: String,
    kind: SenderFieldKind,
    zhCn: String,
    en: String,
    rows: Int? = null,
    fullWidth: Boolean = false,
    options: List<SenderSelectOption> = emptyList(),
): SenderFieldSchema = SenderFieldSchema(
    key = key,
    kind = kind,
    label = SenderLocalizedText(en = en, zhCn = zhCn, zhTw = zhCn),
    rows = rows,
    fullWidth = fullWidth,
    options = options,
)

private fun option(
    value: String,
    en: String,
    zhCn: String,
    zhTw: String = zhCn,
): SenderSelectOption = SenderSelectOption(value, SenderLocalizedText(en = en, zhCn = zhCn, zhTw = zhTw))

private val PROXY_OPTIONS = listOf(
    option("DIRECT", "Direct", "直连", "直連"),
    option("HTTP", "HTTP proxy", "HTTP 代理", "HTTP 代理"),
    option("SOCKS", "SOCKS proxy", "SOCKS 代理", "SOCKS 代理"),
)

/**
 * Structured fields per sender type, transcribed from the webUI table. Labels
 * are hand-written here because only the console renders typed forms; the
 * contract supplies defaults and select options.
 */
private val SENDER_FIELD_SCHEMAS: Map<Int, List<SenderFieldSchema>> = mapOf(
    0 to listOf(
        field("token", SenderFieldKind.TEXT, "Token", "Token"),
        field("secret", SenderFieldKind.TEXT, "签名 Secret", "Signing secret"),
        field("msgtype", SenderFieldKind.TEXT, "消息类型", "Message type"),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
        field("atAll", SenderFieldKind.BOOLEAN, "艾特所有人", "Mention all"),
        field("atMobiles", SenderFieldKind.TEXTAREA, "艾特手机号", "Mention mobiles", rows = 3),
        field("atDingtalkIds", SenderFieldKind.TEXTAREA, "艾特 DingTalk ID", "Mention DingTalk IDs", rows = 3),
    ),
    1 to listOf(
        field("mailType", SenderFieldKind.TEXT, "邮件类型", "Mail type"),
        field("authEmail", SenderFieldKind.TEXT, "登录邮箱", "Authentication email"),
        field("fromEmail", SenderFieldKind.TEXT, "显示发件邮箱", "Visible from email"),
        field("pwd", SenderFieldKind.TEXT, "邮箱密码", "Password"),
        field("host", SenderFieldKind.TEXT, "SMTP 主机", "SMTP host"),
        field("port", SenderFieldKind.TEXT, "SMTP 端口", "SMTP port"),
        field("ssl", SenderFieldKind.BOOLEAN, "启用 SSL", "Enable SSL"),
        field("startTls", SenderFieldKind.BOOLEAN, "启用 STARTTLS", "Enable STARTTLS"),
        field("title", SenderFieldKind.TEXT, "邮件标题", "Email title"),
        field("toEmail", SenderFieldKind.TEXT, "收件邮箱", "Recipient email"),
        field("fromEmailAlias", SenderFieldKind.TEXT, "显示发件人名称", "Visible sender name"),
        field("encryptionProtocol", SenderFieldKind.TEXT, "加密协议", "Encryption protocol"),
        field("keystore", SenderFieldKind.TEXTAREA, "证书内容", "Keystore / certificate", rows = 3),
        field("password", SenderFieldKind.TEXT, "证书密码", "Certificate password"),
        field(
            "recipients",
            SenderFieldKind.JSON,
            "收件人映射 JSON",
            "Recipients JSON",
            rows = 5,
            fullWidth = true,
        ),
        field(
            "authMethod",
            SenderFieldKind.SELECT,
            "验证方式",
            "Auth method",
            options = listOf(
                option("password", "password", "密码", "密碼"),
                option("oauth2", "oauth2", "OAuth2", "OAuth2"),
            ),
        ),
        field("oauth2ClientId", SenderFieldKind.TEXT, "OAuth2 客户端 ID", "OAuth2 client ID"),
        field("oauth2TenantId", SenderFieldKind.TEXT, "OAuth2 租户 ID", "OAuth2 tenant ID"),
        field("oauth2CredentialId", SenderFieldKind.TEXT, "OAuth2 凭据 ID", "OAuth2 credential ID"),
    ),
    2 to listOf(
        field("server", SenderFieldKind.TEXT, "Bark 地址", "Bark server"),
        field("group", SenderFieldKind.TEXT, "分组", "Group"),
        field("icon", SenderFieldKind.TEXT, "图标 URL", "Icon URL"),
        field("sound", SenderFieldKind.TEXT, "铃声", "Sound"),
        field("badge", SenderFieldKind.TEXT, "角标", "Badge"),
        field("url", SenderFieldKind.TEXT, "跳转链接", "Open URL"),
        field("level", SenderFieldKind.TEXT, "通知级别", "Level"),
        field("title", SenderFieldKind.TEXT, "标题模板", "Title template"),
        field("transformation", SenderFieldKind.TEXT, "加密方式", "Transformation"),
        field("key", SenderFieldKind.TEXT, "加密密钥", "Encryption key"),
        field("iv", SenderFieldKind.TEXT, "IV", "IV"),
        field("call", SenderFieldKind.TEXT, "持续提醒", "Call"),
        field("autoCopy", SenderFieldKind.TEXT, "自动复制", "Auto copy"),
    ),
    3 to listOf(
        field("method", SenderFieldKind.TEXT, "请求方法", "HTTP method"),
        field("webServer", SenderFieldKind.TEXT, "Webhook 地址", "Webhook URL"),
        field("secret", SenderFieldKind.TEXT, "签名密钥", "Secret"),
        field("response", SenderFieldKind.TEXT, "成功响应关键字", "Success response keyword"),
        field("proxyType", SenderFieldKind.SELECT, "代理类型", "Proxy type", options = PROXY_OPTIONS),
        field("proxyHost", SenderFieldKind.TEXT, "代理主机", "Proxy host"),
        field("proxyPort", SenderFieldKind.TEXT, "代理端口", "Proxy port"),
        field("proxyAuthenticator", SenderFieldKind.BOOLEAN, "代理鉴权", "Proxy auth"),
        field("proxyUsername", SenderFieldKind.TEXT, "代理用户名", "Proxy username"),
        field("proxyPassword", SenderFieldKind.TEXT, "代理密码", "Proxy password"),
        field("webParams", SenderFieldKind.TEXTAREA, "请求参数", "Request params", rows = 4, fullWidth = true),
        field("headers", SenderFieldKind.JSON, "请求头 JSON", "Headers JSON", rows = 4, fullWidth = true),
    ),
    4 to listOf(
        field("webHook", SenderFieldKind.TEXT, "机器人 WebHook", "Robot WebHook"),
        field("msgType", SenderFieldKind.TEXT, "消息类型", "Message type"),
        field("atAll", SenderFieldKind.BOOLEAN, "艾特所有人", "Mention all"),
        field("atUserIds", SenderFieldKind.TEXTAREA, "艾特成员 ID", "Mention user IDs", rows = 3),
        field("atMobiles", SenderFieldKind.TEXTAREA, "艾特手机号", "Mention mobiles", rows = 3),
    ),
    5 to listOf(
        field("corpID", SenderFieldKind.TEXT, "企业 ID", "Corp ID"),
        field("agentID", SenderFieldKind.TEXT, "应用 Agent ID", "Agent ID"),
        field("secret", SenderFieldKind.TEXT, "应用 Secret", "App secret"),
        field("toUser", SenderFieldKind.TEXT, "接收用户", "To user"),
        field("toParty", SenderFieldKind.TEXTAREA, "接收部门", "To party", rows = 2),
        field("toTag", SenderFieldKind.TEXTAREA, "接收标签", "To tag", rows = 2),
        field("customizeAPI", SenderFieldKind.TEXT, "API 地址", "Custom API"),
        field("atAll", SenderFieldKind.BOOLEAN, "艾特所有人", "Mention all"),
        field("proxyType", SenderFieldKind.SELECT, "代理类型", "Proxy type", options = PROXY_OPTIONS),
        field("proxyHost", SenderFieldKind.TEXT, "代理主机", "Proxy host"),
        field("proxyPort", SenderFieldKind.TEXT, "代理端口", "Proxy port"),
        field("proxyAuthenticator", SenderFieldKind.BOOLEAN, "代理鉴权", "Proxy auth"),
        field("proxyUsername", SenderFieldKind.TEXT, "代理用户名", "Proxy username"),
        field("proxyPassword", SenderFieldKind.TEXT, "代理密码", "Proxy password"),
    ),
    6 to listOf(
        field("sendKey", SenderFieldKind.TEXT, "SendKey", "SendKey"),
        field("channel", SenderFieldKind.TEXT, "Channel", "Channel"),
        field("openid", SenderFieldKind.TEXT, "OpenID", "OpenID"),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
    ),
    7 to listOf(
        field("method", SenderFieldKind.TEXT, "请求方法", "HTTP method"),
        field("apiBase", SenderFieldKind.TEXT, "API 地址", "API base"),
        field("apiToken", SenderFieldKind.TEXT, "Bot Token", "Bot token"),
        field("chatId", SenderFieldKind.TEXT, "Chat ID", "Chat ID"),
        field("messageThreadId", SenderFieldKind.TEXT, "话题 ID", "Message thread ID"),
        field("parseMode", SenderFieldKind.TEXT, "解析模式", "Parse mode"),
        field("proxyType", SenderFieldKind.SELECT, "代理类型", "Proxy type", options = PROXY_OPTIONS),
        field("proxyHost", SenderFieldKind.TEXT, "代理主机", "Proxy host"),
        field("proxyPort", SenderFieldKind.TEXT, "代理端口", "Proxy port"),
        field("proxyAuthenticator", SenderFieldKind.BOOLEAN, "代理鉴权", "Proxy auth"),
        field("proxyUsername", SenderFieldKind.TEXT, "代理用户名", "Proxy username"),
        field("proxyPassword", SenderFieldKind.TEXT, "代理密码", "Proxy password"),
    ),
    8 to listOf(
        field("simSlot", SenderFieldKind.NUMBER, "SIM 卡槽", "SIM slot"),
        field("mobiles", SenderFieldKind.TEXTAREA, "目标号码", "Target numbers", rows = 3),
        field("onlyNoNetwork", SenderFieldKind.BOOLEAN, "仅无网络时发送", "Only when no network"),
    ),
    9 to listOf(
        field("webhook", SenderFieldKind.TEXT, "Webhook 地址", "Webhook URL"),
        field("secret", SenderFieldKind.TEXT, "签名密钥", "Secret"),
        field("msgType", SenderFieldKind.TEXT, "消息类型", "Message type"),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
        field("messageCard", SenderFieldKind.TEXTAREA, "消息卡片 JSON", "Message card JSON", rows = 5, fullWidth = true),
    ),
    10 to listOf(
        field("website", SenderFieldKind.TEXT, "PushPlus 域名", "PushPlus host"),
        field("token", SenderFieldKind.TEXT, "Token", "Token"),
        field("topic", SenderFieldKind.TEXT, "Topic", "Topic"),
        field("template", SenderFieldKind.TEXT, "模板", "Template"),
        field("channel", SenderFieldKind.TEXT, "Channel", "Channel"),
        field("webhook", SenderFieldKind.TEXT, "Webhook", "Webhook"),
        field("callbackUrl", SenderFieldKind.TEXT, "回调地址", "Callback URL"),
        field("validTime", SenderFieldKind.TEXT, "有效期", "Valid time"),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
    ),
    11 to listOf(
        field("webServer", SenderFieldKind.TEXT, "Gotify 地址", "Gotify URL"),
        field("title", SenderFieldKind.TEXT, "标题", "Title"),
        field("priority", SenderFieldKind.TEXT, "优先级", "Priority"),
    ),
    12 to listOf(
        field("agentID", SenderFieldKind.TEXT, "Agent ID", "Agent ID"),
        field("appKey", SenderFieldKind.TEXT, "App Key", "App key"),
        field("appSecret", SenderFieldKind.TEXT, "App Secret", "App secret"),
        field("userIds", SenderFieldKind.TEXTAREA, "接收用户 ID", "User IDs", rows = 3),
        field("msgKey", SenderFieldKind.TEXT, "消息 Key", "Message key"),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
        field("proxyType", SenderFieldKind.SELECT, "代理类型", "Proxy type", options = PROXY_OPTIONS),
        field("proxyHost", SenderFieldKind.TEXT, "代理主机", "Proxy host"),
        field("proxyPort", SenderFieldKind.TEXT, "代理端口", "Proxy port"),
        field("proxyAuthenticator", SenderFieldKind.BOOLEAN, "代理鉴权", "Proxy auth"),
        field("proxyUsername", SenderFieldKind.TEXT, "代理用户名", "Proxy username"),
        field("proxyPassword", SenderFieldKind.TEXT, "代理密码", "Proxy password"),
    ),
    13 to listOf(
        field("appId", SenderFieldKind.TEXT, "App ID", "App ID"),
        field("appSecret", SenderFieldKind.TEXT, "App Secret", "App secret"),
        field("receiveId", SenderFieldKind.TEXT, "接收 ID", "Receive ID"),
        field(
            "receiveIdType",
            SenderFieldKind.SELECT,
            "接收 ID 类型",
            "Receive ID type",
            options = listOf(
                option("user_id", "User ID", "用户 ID"),
                option("open_id", "Open ID", "开放 ID"),
                option("union_id", "Union ID", "统一 ID"),
                option("email", "Email", "邮箱"),
                option("chat_id", "Chat ID", "会话 ID"),
            ),
        ),
        field(
            "msgType",
            SenderFieldKind.SELECT,
            "消息类型",
            "Message type",
            options = listOf(
                option("interactive", "Interactive", "交互卡片"),
                option("text", "Text", "文本"),
            ),
        ),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
        field("messageCard", SenderFieldKind.TEXTAREA, "消息卡片 JSON", "Message card JSON", rows = 5, fullWidth = true),
    ),
    14 to listOf(
        field("urlScheme", SenderFieldKind.TEXTAREA, "URL Scheme", "URL scheme", rows = 4, fullWidth = true),
    ),
    15 to listOf(
        field("method", SenderFieldKind.TEXT, "协议类型", "Method"),
        field("address", SenderFieldKind.TEXT, "地址", "Address"),
        field("port", SenderFieldKind.NUMBER, "端口", "Port"),
        field("msgTemplate", SenderFieldKind.TEXTAREA, "消息模板", "Message template", rows = 3, fullWidth = true),
        field("secret", SenderFieldKind.TEXT, "签名密钥", "Secret"),
        field("response", SenderFieldKind.TEXT, "成功响应关键字", "Success response"),
        field("username", SenderFieldKind.TEXT, "用户名", "Username"),
        field("password", SenderFieldKind.TEXT, "密码", "Password"),
        field("inCharset", SenderFieldKind.TEXT, "输入编码", "Input charset"),
        field("outCharset", SenderFieldKind.TEXT, "输出编码", "Output charset"),
        field("inMessageTopic", SenderFieldKind.TEXT, "订阅主题", "Inbound topic"),
        field("outMessageTopic", SenderFieldKind.TEXT, "发布主题", "Outbound topic"),
        field("uriType", SenderFieldKind.TEXT, "URI 类型", "URI type"),
        field("path", SenderFieldKind.TEXT, "路径", "Path"),
        field("clientId", SenderFieldKind.TEXT, "客户端 ID", "Client ID"),
        field("qos", SenderFieldKind.NUMBER, "QoS", "QoS"),
        field("retained", SenderFieldKind.BOOLEAN, "保留消息", "Retained"),
    ),
    16 to listOf(
        field("server", SenderFieldKind.TEXT, "Ntfy 地址", "Ntfy server"),
        field("topic", SenderFieldKind.TEXT, "Topic", "Topic"),
        field("token", SenderFieldKind.TEXT, "Token", "Token"),
        field("title", SenderFieldKind.TEXT, "标题", "Title"),
        field("priority", SenderFieldKind.TEXT, "优先级", "Priority"),
        field("tags", SenderFieldKind.TEXT, "标签", "Tags"),
    ),
    17 to listOf(
        field("token", SenderFieldKind.TEXT, "Token", "Token"),
        field("recvId", SenderFieldKind.TEXT, "接收者 ID", "Recipient ID"),
        field(
            "recvType",
            SenderFieldKind.SELECT,
            "接收者类型",
            "Recipient type",
            options = listOf(
                option("user", "User", "用户", "用戶"),
                option("group", "Group", "群组", "群組"),
            ),
        ),
        field(
            "contentType",
            SenderFieldKind.SELECT,
            "消息类型",
            "Message type",
            options = listOf(
                option("text", "Text", "文本", "文字"),
                option("markdown", "Markdown", "Markdown", "Markdown"),
            ),
        ),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
    ),
    18 to listOf(
        field("server", SenderFieldKind.TEXT, "Server", "Server"),
        field("pushkey", SenderFieldKind.TEXT, "PushKey", "PushKey"),
        field(
            "type",
            SenderFieldKind.SELECT,
            "消息类型",
            "Message type",
            options = listOf(
                option("markdown", "Markdown", "Markdown", "Markdown"),
                option("text", "Text", "文本", "文字"),
            ),
        ),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
    ),
    19 to listOf(
        field("homeserver", SenderFieldKind.TEXT, "Homeserver", "Homeserver"),
        field("username", SenderFieldKind.TEXT, "用户名", "Username"),
        field("password", SenderFieldKind.TEXT, "密码", "Password"),
        field("accessToken", SenderFieldKind.TEXT, "Access Token", "Access token"),
        field("roomId", SenderFieldKind.TEXT, "Room ID", "Room ID"),
        field(
            "messageType",
            SenderFieldKind.SELECT,
            "消息类型",
            "Message type",
            options = listOf(
                option("text", "Text", "文本", "文字"),
                option("markdown", "Markdown", "Markdown", "Markdown"),
            ),
        ),
        field("titleTemplate", SenderFieldKind.TEXT, "标题模板", "Title template"),
        field("proxyType", SenderFieldKind.SELECT, "代理类型", "Proxy type", options = PROXY_OPTIONS),
        field("proxyHost", SenderFieldKind.TEXT, "代理主机", "Proxy host"),
        field("proxyPort", SenderFieldKind.TEXT, "代理端口", "Proxy port"),
        field("proxyAuthenticator", SenderFieldKind.BOOLEAN, "代理鉴权", "Proxy auth"),
        field("proxyUsername", SenderFieldKind.TEXT, "代理用户名", "Proxy username"),
        field("proxyPassword", SenderFieldKind.TEXT, "代理密码", "Proxy password"),
    ),
)

private val compactJson = Json { encodeDefaults = true }

private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** Defaults applied on top of the contract defaults, mirroring the webUI overrides. */
private val DEFAULT_SENDER_SETTING_OVERRIDES: Map<Int, Map<String, JsonElement>> = mapOf(
    1 to mapOf("encryptionProtocol" to JsonPrimitive("Plain")),
    2 to mapOf("level" to JsonPrimitive("active")),
    15 to mapOf("uriType" to JsonPrimitive("tcp")),
)

/** Per-sender-type default jsonSetting payloads, in contract field order. */
private val DEFAULT_SENDER_SETTINGS: Map<Int, Map<String, JsonElement>> =
    SenderSettingSchemas.all.associate { schema ->
        schema.senderType to (
            schema.fields.associate { field -> field.name to defaultValueForContractField(field) } +
                DEFAULT_SENDER_SETTING_OVERRIDES[schema.senderType].orEmpty()
            )
    }

private fun defaultValueForContractField(field: SenderSettingFieldMetadata): JsonElement =
    when (field.type) {
        SenderSettingFieldType.BOOLEAN -> JsonPrimitive(field.defaultValue == "true")
        SenderSettingFieldType.INTEGER -> JsonPrimitive(field.defaultValue?.toIntOrNull() ?: 0)
        SenderSettingFieldType.STRING_MAP, SenderSettingFieldType.EMAIL_RECIPIENTS -> JsonObject(emptyMap())
        else -> JsonPrimitive(field.defaultValue ?: "")
    }

/**
 * Structured fields for [type]; select options come from the contract so a
 * newly added device-side option shows up here without a console release.
 */
fun getSenderFieldSchemas(type: Int): List<SenderFieldSchema> {
    val contractFields = SenderSettingSchemas.fieldsFor(type).associateBy { it.name }
    return SENDER_FIELD_SCHEMAS[type].orEmpty().map { schema ->
        val contractField = contractFields[schema.key]
        if (contractField == null || contractField.options.isEmpty()) {
            schema
        } else {
            val existingLabels = schema.options.associateBy { it.value }
            schema.copy(
                kind = SenderFieldKind.SELECT,
                options = contractField.options.map { contractOption ->
                    SenderSelectOption(
                        value = contractOption.value,
                        label = existingLabels[contractOption.value]?.label
                            ?: SenderLocalizedText(contractOption.value, contractOption.value),
                    )
                },
            )
        }
    }
}

/** Pretty-printed starter jsonSetting for [type], like buildSenderDraftJson. */
fun buildSenderDraftJson(type: Int): String {
    val normalized = normalizeSenderJson(type, "")
    if (normalized.isEmpty()) return ""
    return prettyJson.encodeToString(parseJsonObject(normalized) ?: JsonObject(emptyMap()))
}

/** Next free sender id, like nextSenderId. */
fun nextSenderId(senders: List<SnapshotSender>): Long =
    senders.maxOfOrNull { it.id }?.coerceAtLeast(0L)?.plus(1) ?: 1L

/**
 * Keeps the typed jsonSetting when switching type only while it still equals
 * the untouched default draft; otherwise the user already customized it.
 */
fun resolveSenderJsonForTypeChange(currentType: Int, nextType: Int, currentJson: String): String {
    if (currentType == nextType) return currentJson
    if (currentJson.trim().isEmpty()) return buildSenderDraftJson(nextType)
    val currentDefault = canonicalizeJsonObject(buildSenderDraftJson(currentType))
    val normalizedCurrent = canonicalizeJsonObject(currentJson)
    return if (normalizedCurrent != null && normalizedCurrent == currentDefault) {
        buildSenderDraftJson(nextType)
    } else {
        currentJson
    }
}

/** Normalizes a sender before it is queued, like normalizeSnapshotSender. */
fun normalizeSnapshotSender(sender: SnapshotSender): SnapshotSender = sender.copy(
    id = if (sender.id > 0) sender.id else 0L,
    name = sender.name.trim(),
    jsonSetting = normalizePersistedSenderJson(sender.type, sender.jsonSetting),
    activeSchedule = SenderActiveScheduleEvaluator.sanitize(sender.activeSchedule),
    status = sender.status.asFlag(),
    receiveCode = sender.receiveCode.asFlag(),
    receiveNonCode = sender.receiveNonCode.asFlag(),
    receiveAppNotify = sender.receiveAppNotify.asFlag(),
    receiveCallNotify = sender.receiveCallNotify.asFlag(),
)

/** Only the Feishu app payload is re-encoded on persist; other types keep their text. */
private fun normalizePersistedSenderJson(type: Int, rawJson: String): String =
    if (type == 13) normalizeSenderJson(type, rawJson) else rawJson.trim()

private fun Int.asFlag(): Int = if (this == 1) 1 else 0

/** Seeds the editor form from a persisted payload, like parseSenderFormState. */
fun parseSenderFormState(type: Int, rawJson: String): Map<String, JsonElement> {
    val parsed = parseJsonObject(normalizeSenderJson(type, rawJson)) ?: JsonObject(emptyMap())
    val formState = LinkedHashMap<String, JsonElement>()
    for (schema in getSenderFieldSchemas(type)) {
        when (schema.kind) {
            SenderFieldKind.JSON -> formState[schema.key] = JsonPrimitive(
                prettyJson.encodeToString((parsed[schema.key] as? JsonObject) ?: JsonObject(emptyMap())),
            )

            else -> parsed[schema.key]?.let { formState[schema.key] = it }
        }
    }
    if (type == 1) {
        val authEmail = formState["authEmail"].stringOrNull().orEmpty()
        val fromEmail = formState["fromEmail"].stringOrNull().orEmpty()
        val alias = formState["fromEmailAlias"].stringOrNull().orEmpty()
        val nickname = parsed["nickname"].stringOrNull().orEmpty()
        formState["authEmail"] = JsonPrimitive(authEmail.ifEmpty { fromEmail })
        formState["fromEmailAlias"] = JsonPrimitive(alias.ifEmpty { nickname })
    }
    return formState
}

/** Serializes the editor form back into a sender payload, like buildSenderJsonFromFormState. */
fun buildSenderJsonFromFormState(type: Int, formState: Map<String, JsonElement>): String {
    val defaults = DEFAULT_SENDER_SETTINGS[type] ?: return ""
    val fields = getSenderFieldSchemas(type)
    val raw = LinkedHashMap<String, JsonElement>()
    for ((key, defaultValue) in defaults) {
        val schema = fields.firstOrNull { it.key == key }
        val candidate = formState[key]
        raw[key] = if (schema == null) defaultValue else normalizeFormValue(schema, defaultValue, candidate)
    }
    if (type == 1) {
        val authEmail = raw["authEmail"].stringOrNull().orEmpty()
        val fromEmail = raw["fromEmail"].stringOrNull().orEmpty()
        val alias = raw["fromEmailAlias"].stringOrNull().orEmpty()
        raw["authEmail"] = JsonPrimitive(authEmail.ifEmpty { fromEmail })
        raw["nickname"] = JsonPrimitive(alias)
    }
    return compactJson.encodeToString(sanitizeBySchema(type, defaults, raw))
}

/** Fills missing keys with contract defaults and drops unknown ones. */
fun normalizeSenderJson(type: Int, rawJson: String): String {
    val defaults = DEFAULT_SENDER_SETTINGS[type] ?: return rawJson.trim()
    return compactJson.encodeToString(sanitizeBySchema(type, defaults, parseJsonObject(rawJson)))
}

private fun normalizeFormValue(
    schema: SenderFieldSchema,
    defaultValue: JsonElement,
    candidate: JsonElement?,
): JsonElement = when (schema.kind) {
    SenderFieldKind.BOOLEAN -> JsonPrimitive(candidate == JsonPrimitive(true))
    SenderFieldKind.NUMBER -> when (candidate) {
        is JsonPrimitive if !candidate.isString ->
            if (candidate.content.toDoubleOrNull()?.isFinite() == true) candidate else defaultValue
        is JsonPrimitive -> numberPrimitive(candidate.content.trim()) ?: defaultValue
        else -> defaultValue
    }

    SenderFieldKind.JSON -> when (candidate) {
        is JsonObject -> candidate
        is JsonPrimitive if candidate.isString -> {
            val text = candidate.content.trim()
            if (text.isEmpty()) {
                defaultValue as? JsonObject ?: JsonObject(emptyMap())
            } else {
                parseJsonObject(text) ?: defaultValue
            }
        }

        else -> defaultValue as? JsonObject ?: JsonObject(emptyMap())
    }

    SenderFieldKind.SELECT -> when {
        candidate !is JsonPrimitive || !candidate.isString -> defaultValue
        schema.options.isEmpty() || schema.options.any { it.value == candidate.content } -> candidate
        else -> defaultValue
    }

    SenderFieldKind.TEXT, SenderFieldKind.TEXTAREA ->
        JsonPrimitive(candidate.stringOrNull() ?: defaultValue.stringOrNull().orEmpty())
}

private fun numberPrimitive(text: String): JsonElement? {
    if (text.isEmpty()) return null
    val value = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    return if (value == value.toLong().toDouble()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
}

private fun sanitizeBySchema(
    type: Int,
    schema: Map<String, JsonElement>,
    raw: Map<String, JsonElement>?,
): JsonObject {
    val normalized = LinkedHashMap<String, JsonElement>()
    for ((key, defaultValue) in schema) {
        normalized[key] = sanitizeValue(defaultValue, raw?.get(key))
    }
    if (type == 1) {
        val fromEmail = normalized["fromEmail"].stringOrNull().orEmpty()
        val authEmail = normalized["authEmail"].stringOrNull().orEmpty()
        val rawNickname = raw?.get("nickname").stringOrNull().orEmpty()
        val alias = normalized["fromEmailAlias"].stringOrNull().orEmpty()
        val resolvedAlias = alias.ifEmpty { rawNickname }
        normalized["authEmail"] = JsonPrimitive(authEmail.ifEmpty { fromEmail })
        normalized["fromEmailAlias"] = JsonPrimitive(resolvedAlias)
        normalized["nickname"] = JsonPrimitive(resolvedAlias)
    }
    return JsonObject(normalized)
}

private fun sanitizeValue(defaultValue: JsonElement, candidate: JsonElement?): JsonElement = when (defaultValue) {
    is JsonArray -> candidate as? JsonArray ?: defaultValue
    is JsonObject -> candidate as? JsonObject ?: defaultValue
    is JsonPrimitive -> when {
        defaultValue is JsonNull -> defaultValue
        defaultValue.isString ->
            if (defaultValue.content == "DIRECT") {
                JsonPrimitive(sanitizeProxyType(candidate.stringOrNull()))
            } else {
                JsonPrimitive(candidate.stringOrNull() ?: defaultValue.content)
            }

        defaultValue.content == "true" || defaultValue.content == "false" ->
            if (candidate is JsonPrimitive && candidate !is JsonNull && !candidate.isString &&
                (candidate.content == "true" || candidate.content == "false")
            ) {
                candidate
            } else {
                defaultValue
            }

        else -> if (candidate is JsonPrimitive && candidate !is JsonNull && !candidate.isString) candidate else defaultValue
    }
}

private fun sanitizeProxyType(candidate: String?): String =
    if (candidate == "HTTP" || candidate == "SOCKS") candidate else "DIRECT"

private fun parseJsonObject(rawJson: String): JsonObject? {
    val trimmed = rawJson.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { compactJson.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject
}

private fun canonicalizeJsonObject(rawJson: String): String? {
    val parsed = parseJsonObject(rawJson) ?: return null
    return compactJson.encodeToString(parsed)
}

/** Content of a non-null JSON primitive; objects and arrays have no text form. */
private fun JsonElement?.stringOrNull(): String? =
    if (this is JsonPrimitive && this !is JsonNull) content else null

/** Default active schedule for a new sender, mirroring buildDefaultSenderActiveSchedule. */
fun buildDefaultSenderActiveSchedule(): SenderActiveSchedule = SenderActiveSchedule()
