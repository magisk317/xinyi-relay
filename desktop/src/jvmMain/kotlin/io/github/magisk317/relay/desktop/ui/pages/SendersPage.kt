package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import io.github.magisk317.relay.contract.model.SenderActiveSchedule
import io.github.magisk317.relay.contract.model.SenderActiveScheduleConst
import io.github.magisk317.relay.contract.model.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.contract.model.SenderActiveScheduleRange
import io.github.magisk317.relay.contract.model.SenderActiveScheduleRule
import io.github.magisk317.relay.contract.model.SnapshotSender
import io.github.magisk317.relay.desktop.config.SenderFieldKind
import io.github.magisk317.relay.desktop.config.SenderFieldSchema
import io.github.magisk317.relay.desktop.config.SenderLocalizedText
import io.github.magisk317.relay.desktop.config.buildReplaceSendersMutation
import io.github.magisk317.relay.desktop.config.buildSenderDraftJson
import io.github.magisk317.relay.desktop.config.buildSenderJsonFromFormState
import io.github.magisk317.relay.desktop.config.getSenderFieldSchemas
import io.github.magisk317.relay.desktop.config.nextSenderId
import io.github.magisk317.relay.desktop.config.normalizeSnapshotSender
import io.github.magisk317.relay.desktop.config.parseSenderFormState
import io.github.magisk317.relay.desktop.config.resolveSenderJsonForTypeChange
import io.github.magisk317.relay.desktop.config.resolveSenderText
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.ConfirmDialog
import io.github.magisk317.relay.desktop.ui.ConsoleInk
import io.github.magisk317.relay.desktop.ui.EmptyCard
import io.github.magisk317.relay.desktop.ui.ErrorBanner
import io.github.magisk317.relay.desktop.ui.LiveBadge
import io.github.magisk317.relay.desktop.ui.LoadingCard
import io.github.magisk317.relay.desktop.ui.MetricCard
import io.github.magisk317.relay.desktop.ui.MetricRow
import io.github.magisk317.relay.desktop.ui.MetricTone
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.RelayBadge
import io.github.magisk317.relay.desktop.ui.RelayOption
import io.github.magisk317.relay.desktop.ui.RelaySelect
import io.github.magisk317.relay.desktop.ui.RelaySwitch
import io.github.magisk317.relay.desktop.ui.RelayTone
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import io.github.magisk317.relay.desktop.ui.ToggleRow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Sender types the webUI offers in the new-sender picker, in webUI order. */
private val SENDER_TYPE_OPTIONS = listOf(3, 4, 5, 9, 13, 7, 16, 18, 19, 11, 10, 0, 12, 1, 2, 6, 8, 14, 15)

private val SENDER_TYPE_TRANSLATION_KEYS = mapOf(
    0 to "senders.type.dingtalkGroup",
    1 to "senders.type.email",
    2 to "senders.type.bark",
    3 to "senders.type.webhook",
    4 to "senders.type.weworkRobot",
    5 to "senders.type.weworkAgent",
    6 to "senders.type.serverchan",
    7 to "senders.type.telegram",
    8 to "senders.type.sms",
    9 to "senders.type.feishu",
    10 to "senders.type.pushplus",
    11 to "senders.type.gotify",
    12 to "senders.type.dingtalkInner",
    13 to "senders.type.feishuApp",
    14 to "senders.type.urlScheme",
    15 to "senders.type.socket",
    16 to "senders.type.ntfy",
    17 to "senders.type.yunhu",
    18 to "senders.type.pushdeer",
    19 to "senders.type.matrix",
)

/** Same translation table the webUI's translateSenderType uses. */
private fun translateSenderType(type: Int, locale: DesktopLocale): String {
    val key = SENDER_TYPE_TRANSLATION_KEYS[type] ?: return "Type $type"
    return DesktopMessages.t(locale, key)
}

/** Text the webUI keeps inline in SenderFieldEditor instead of the i18n tables. */
private object SenderEditorText {
    val structured = SenderLocalizedText("Structured config", "结构化配置", "結構化配置")
    val unsupported = SenderLocalizedText(
        en = "This sender type is not exposed as typed fields in the console yet. Edit it on the Android device instead of using raw JSON here.",
        zhCn = "该发送通道暂未在控制台暴露结构化字段，请改在 Android 设备端编辑，而不是在这里回退到原始 JSON。",
        zhTw = "該傳送通道暫未在控制台暴露結構化欄位，請改在 Android 裝置端編輯，而不是在這裡回退到原始 JSON。",
    )
    val syncHint = SenderLocalizedText(
        en = "These fields mirror the sender defaults used on Android and are now the primary editing surface.",
        zhCn = "这些字段会直接套用 Android 端同类通道的默认模板，并作为当前唯一的主要编辑入口。",
        zhTw = "這些欄位會直接套用 Android 端同類通道的預設模板，並作為目前唯一的主要編輯入口。",
    )
}

/** Text the webUI keeps inline in SenderActiveScheduleEditor. */
private object ScheduleText {
    val title = SenderLocalizedText("Active time", "生效时间", "生效時間")
    val noRestrictions = SenderLocalizedText("No schedule restrictions", "未限制时间段", "未限制時間段")
    val sms = SenderLocalizedText("SMS", "短信", "簡訊")
    val appNotify = SenderLocalizedText("App Notify", "应用通知", "應用通知")
    val callNotify = SenderLocalizedText("Call Notify", "通话通知", "通話通知")
    val blacklist = SenderLocalizedText("Blacklist", "黑名单", "黑名單")
    val whitelist = SenderLocalizedText("Whitelist", "白名单", "白名單")
    val weekdays = SenderLocalizedText("Weekdays", "星期", "星期")
    val ranges = SenderLocalizedText("Time ranges", "时间段", "時間段")
    val addRange = SenderLocalizedText("Add range", "添加时间段", "新增時間段")
    val remove = SenderLocalizedText("Remove", "删除", "刪除")
}

/** Weekday pills carry a value and two labels, like the webUI DAYS table. */
private val SCHEDULE_DAYS = listOf(
    1 to SenderLocalizedText("Mon", "周一"),
    2 to SenderLocalizedText("Tue", "周二"),
    3 to SenderLocalizedText("Wed", "周三"),
    4 to SenderLocalizedText("Thu", "周四"),
    5 to SenderLocalizedText("Fri", "周五"),
    6 to SenderLocalizedText("Sat", "周六"),
    7 to SenderLocalizedText("Sun", "周日"),
)

private enum class ScheduleBucket { SMS, APP_NOTIFY, CALL_NOTIFY }

private data class SenderDraft(
    val name: String,
    val type: Int,
    val jsonSetting: String,
    val activeSchedule: SenderActiveSchedule,
)

private fun senderDraft(type: Int): SenderDraft = SenderDraft(
    name = "",
    type = type,
    jsonSetting = buildSenderDraftJson(type),
    activeSchedule = SenderActiveSchedule(),
)

/**
 * Desktop port of the webUI senders page: the sender definitions of the
 * selected device with structured field editing and per-channel active time
 * rules. Every edit is queued as a replace_senders command, like the React page.
 */
@Composable
fun SendersPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(senderDraft(4)) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(console.selectedDeviceId) {
        runCatching { console.refreshConfig() }
    }

    val senders = console.root.senders
    val rules = console.root.rules
    val enabledCount = senders.count { it.status == 1 }
    val appNotifyCount = senders.count { it.receiveAppNotify == 1 }

    val typeOptions = remember(locale) {
        SENDER_TYPE_OPTIONS.map { type ->
            RelayOption(value = type.toString(), label = translateSenderType(type, locale))
        }
    }

    fun persistSenders(nextSenders: List<SnapshotSender>, removedSenderId: Long? = null) {
        if (console.selectedDeviceId == null || console.config == null) return
        scope.launch {
            runCatching {
                console.queueMutation(
                    buildReplaceSendersMutation(
                        nextSenders.map(::normalizeSnapshotSender),
                        if (removedSenderId != null) listOf(removedSenderId) else emptyList(),
                    ),
                    "senders:update",
                )
            }
        }
    }

    val actions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        ActionButton(
            text = DesktopMessages.t(locale, "senders.refresh"),
            onClick = { scope.launch { runCatching { console.refreshConfig() } } },
            tone = ActionTone.NEUTRAL,
        )
    }

    if (console.loading && console.config == null && console.error.isBlank()) {
        PageShell(
            title = DesktopMessages.t(locale, "senders.title"),
            description = DesktopMessages.t(locale, "senders.description"),
            badge = DesktopMessages.t(locale, "senders.title"),
            locale = locale,
            actions = actions,
        ) {
            LoadingCard(
                title = DesktopMessages.t(locale, "senders.loadingTitle"),
                message = DesktopMessages.t(locale, "senders.loadingMessage"),
                locale = locale,
            )
        }
        return
    }

    PageShell(
        title = DesktopMessages.t(locale, "senders.title"),
        description = DesktopMessages.t(locale, "senders.remoteDescription"),
        badge = DesktopMessages.t(locale, "senders.title"),
        locale = locale,
        actions = actions,
    ) {
        ErrorBanner(message = console.error, locale = locale)

        MetricRow(
            cards = listOf(
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "senders.metric.total"),
                        value = senders.size.toString(),
                        helper = DesktopMessages.t(locale, "senders.metric.totalHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "senders.metric.enabled"),
                        value = enabledCount.toString(),
                        tone = MetricTone.SUCCESS,
                        helper = DesktopMessages.t(locale, "senders.metric.enabledHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "senders.metric.appNotify"),
                        value = appNotifyCount.toString(),
                        tone = MetricTone.INFO,
                        helper = DesktopMessages.t(locale, "senders.metric.appNotifyHelper"),
                    )
                },
            ),
        )

        SurfaceCard(
            title = DesktopMessages.t(locale, "senders.newTitle"),
            subtitle = DesktopMessages.t(locale, "senders.newSubtitle"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { value -> draft = draft.copy(name = value) },
                        label = { Text(DesktopMessages.t(locale, "senders.namePlaceholder")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    RelaySelect(
                        value = draft.type.toString(),
                        options = typeOptions,
                        onValueChange = { value ->
                            val nextType = value.toIntOrNull() ?: return@RelaySelect
                            draft = draft.copy(
                                type = nextType,
                                jsonSetting = resolveSenderJsonForTypeChange(draft.type, nextType, draft.jsonSetting),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                SenderFieldEditor(
                    type = draft.type,
                    jsonSetting = draft.jsonSetting,
                    locale = locale,
                    onLiveChange = { value -> draft = draft.copy(jsonSetting = value) },
                )
                SenderActiveScheduleEditor(
                    schedule = draft.activeSchedule,
                    locale = locale,
                    onChange = { schedule -> draft = draft.copy(activeSchedule = schedule) },
                )
                ActionButton(
                    text = if (console.saving) {
                        DesktopMessages.t(locale, "common.saving")
                    } else {
                        DesktopMessages.t(locale, "senders.create")
                    },
                    onClick = {
                        if (draft.name.isBlank()) {
                            console.showError(DesktopMessages.t(locale, "common.nameRequired"))
                            return@ActionButton
                        }
                        val nextSender = SnapshotSender(
                            id = nextSenderId(senders),
                            type = draft.type,
                            name = draft.name.trim(),
                            jsonSetting = draft.jsonSetting,
                            activeSchedule = draft.activeSchedule,
                            status = 1,
                            receiveCode = 1,
                            receiveNonCode = 1,
                            receiveAppNotify = 1,
                            receiveCallNotify = 0,
                        )
                        persistSenders(listOf(nextSender) + senders)
                        draft = senderDraft(draft.type)
                    },
                    tone = ActionTone.PRIMARY,
                    enabled = !console.saving,
                )
            }
        }

        if (senders.isEmpty()) {
            EmptyCard(
                title = DesktopMessages.t(locale, "senders.emptyTitle"),
                message = DesktopMessages.t(locale, "senders.emptyMessage"),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                senders.forEach { sender ->
                    SenderCard(
                        sender = sender,
                        locale = locale,
                        saving = console.saving,
                        typeOptions = typeOptions,
                        linkedRuleCount = rules.count { it.senderId == sender.id },
                        onPersist = { updated ->
                            persistSenders(senders.map { if (it.id == sender.id) updated else it })
                        },
                        onDeleteRequest = { pendingDeleteId = sender.id },
                    )
                }
            }
        }
    }

    val deletingSenderId = pendingDeleteId
    if (deletingSenderId != null) {
        ConfirmDialog(
            message = DesktopMessages.t(locale, "common.confirmDeleteSender"),
            confirmLabel = DesktopMessages.t(locale, "common.delete"),
            dismissLabel = DesktopMessages.t(locale, "common.cancel"),
            onConfirm = {
                persistSenders(senders.filter { it.id != deletingSenderId }, deletingSenderId)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun SenderCard(
    sender: SnapshotSender,
    locale: DesktopLocale,
    saving: Boolean,
    typeOptions: List<RelayOption>,
    linkedRuleCount: Int,
    onPersist: (SnapshotSender) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var name by remember(sender.id, sender.name) { mutableStateOf(sender.name) }

    fun commitName() {
        val value = name.trim()
        if (value.isEmpty() || value == sender.name) return
        onPersist(sender.copy(name = value))
    }

    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sender.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = ConsoleInk,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RelayBadge(
                        text = translateSenderType(sender.type, locale),
                        tone = RelayTone.ACCENT,
                    )
                    RelayBadge(
                        text = DesktopMessages.t(
                            locale,
                            if (sender.status == 1) "common.enabled" else "common.disabled",
                        ),
                        tone = if (sender.status == 1) RelayTone.SUCCESS else RelayTone.WARNING,
                    )
                    RelayBadge(
                        text = DesktopMessages.t(locale, "senders.linkedRules", mapOf("count" to linkedRuleCount)),
                    )
                }
            }
            ActionButton(
                text = DesktopMessages.t(locale, "common.delete"),
                onClick = onDeleteRequest,
                tone = ActionTone.DANGER,
                enabled = !saving,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(DesktopMessages.t(locale, "senders.namePlaceholder")) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focus -> if (!focus.isFocused) commitName() },
                )
                RelaySelect(
                    value = sender.type.toString(),
                    options = typeOptions,
                    onValueChange = { value ->
                        val nextType = value.toIntOrNull() ?: return@RelaySelect
                        onPersist(
                            sender.copy(
                                type = nextType,
                                jsonSetting = resolveSenderJsonForTypeChange(
                                    sender.type,
                                    nextType,
                                    sender.jsonSetting,
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            listOf(
                Triple(
                    DesktopMessages.t(locale, "common.enabled"),
                    sender.status == 1,
                ) { value: Boolean -> onPersist(sender.copy(status = if (value) 1 else 0)) },
                Triple(
                    DesktopMessages.t(locale, "senders.receiveCode"),
                    sender.receiveCode == 1,
                ) { value: Boolean -> onPersist(sender.copy(receiveCode = if (value) 1 else 0)) },
                Triple(
                    DesktopMessages.t(locale, "senders.receivePlain"),
                    sender.receiveNonCode == 1,
                ) { value: Boolean -> onPersist(sender.copy(receiveNonCode = if (value) 1 else 0)) },
                Triple(
                    DesktopMessages.t(locale, "senders.receiveAppNotify"),
                    sender.receiveAppNotify == 1,
                ) { value: Boolean -> onPersist(sender.copy(receiveAppNotify = if (value) 1 else 0)) },
            ).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, checked, onCheckedChange) ->
                        Box(modifier = Modifier.weight(1f)) {
                            ToggleRow(
                                label = label,
                                checked = checked,
                                onCheckedChange = onCheckedChange,
                                enabled = !saving,
                            )
                        }
                    }
                }
            }

            SenderActiveScheduleEditor(
                schedule = sender.activeSchedule,
                locale = locale,
                onChange = { schedule -> onPersist(sender.copy(activeSchedule = schedule)) },
            )

            SenderFieldEditor(
                type = sender.type,
                jsonSetting = sender.jsonSetting,
                locale = locale,
                onCommit = { value ->
                    if (value != sender.jsonSetting) onPersist(sender.copy(jsonSetting = value))
                },
            )
        }
    }
}

/**
 * Structured editor for one sender payload, ported from SenderFieldEditor.tsx.
 * Live edits propagate to the caller; text and textarea inputs commit when
 * they lose focus, matching the React onBlur handlers.
 */
@Composable
private fun SenderFieldEditor(
    type: Int,
    jsonSetting: String,
    locale: DesktopLocale,
    onLiveChange: (String) -> Unit = {},
    onCommit: (String) -> Unit = {},
) {
    val fields = remember(type) { getSenderFieldSchemas(type) }

    if (fields.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFFFFFAF0),
            border = BorderStroke(1.dp, Color(0xFFE4D8AE)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = resolveSenderText(locale, SenderEditorText.unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7A6540),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        return
    }

    var formState by remember(type) { mutableStateOf(parseSenderFormState(type, jsonSetting)) }

    // The payload can also change from the outside (device refresh, type switch);
    // re-seed the form then, but never for the edits this editor just emitted.
    LaunchedEffect(type, jsonSetting) {
        if (jsonSetting != buildSenderJsonFromFormState(type, formState)) {
            formState = parseSenderFormState(type, jsonSetting)
        }
    }

    fun updateField(schema: SenderFieldSchema, value: JsonElement, commit: Boolean) {
        val next = formState + (schema.key to value)
        formState = next
        val nextJson = buildSenderJsonFromFormState(type, next)
        onLiveChange(nextJson)
        if (commit) onCommit(nextJson)
    }

    fun commitCurrentForm() {
        val nextJson = buildSenderJsonFromFormState(type, formState)
        onLiveChange(nextJson)
        onCommit(nextJson)
    }

    val rows = remember(fields) { chunkSenderFields(fields) }

    SurfaceCard(
        title = resolveSenderText(locale, SenderEditorText.structured),
        subtitle = resolveSenderText(locale, SenderEditorText.syncHint),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { schema ->
                        Box(modifier = Modifier.weight(1f)) {
                            SenderFieldInput(
                                schema = schema,
                                value = formState[schema.key],
                                locale = locale,
                                onChange = { value, commit -> updateField(schema, value, commit) },
                                onCommit = { commitCurrentForm() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pairs neighbouring fields so wide fields span both grid columns. */
private fun chunkSenderFields(fields: List<SenderFieldSchema>): List<List<SenderFieldSchema>> {
    val rows = mutableListOf<List<SenderFieldSchema>>()
    val buffer = mutableListOf<SenderFieldSchema>()
    for (schema in fields) {
        if (schema.fullWidth) {
            if (buffer.isNotEmpty()) {
                rows.add(buffer.toList())
                buffer.clear()
            }
            rows.add(listOf(schema))
            continue
        }
        buffer.add(schema)
        if (buffer.size == 2) {
            rows.add(buffer.toList())
            buffer.clear()
        }
    }
    if (buffer.isNotEmpty()) rows.add(buffer.toList())
    return rows
}

@Composable
private fun SenderFieldInput(
    schema: SenderFieldSchema,
    value: JsonElement?,
    locale: DesktopLocale,
    onChange: (JsonElement, Boolean) -> Unit,
    onCommit: () -> Unit,
) {
    val primitive = value as? JsonPrimitive
    val text = if (primitive != null && primitive !is JsonNull) primitive.content else ""
    val label = resolveSenderText(locale, schema.label)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ConsoleInk,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when (schema.kind) {
            SenderFieldKind.BOOLEAN -> RelaySwitch(
                checked = text == "true",
                onCheckedChange = { checked -> onChange(JsonPrimitive(checked), true) },
            )

            SenderFieldKind.SELECT -> RelaySelect(
                value = text,
                options = schema.options.map { option ->
                    RelayOption(value = option.value, label = resolveSenderText(locale, option.label))
                },
                onValueChange = { next -> onChange(JsonPrimitive(next), true) },
                modifier = Modifier.fillMaxWidth(),
            )

            SenderFieldKind.TEXTAREA, SenderFieldKind.JSON -> OutlinedTextField(
                value = text,
                onValueChange = { next -> onChange(JsonPrimitive(next), false) },
                label = { Text(label) },
                minLines = schema.rows ?: 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus -> if (!focus.isFocused) onCommit() },
            )

            SenderFieldKind.NUMBER, SenderFieldKind.TEXT -> OutlinedTextField(
                value = text,
                onValueChange = { next -> onChange(JsonPrimitive(next), false) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (schema.kind == SenderFieldKind.NUMBER) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus -> if (!focus.isFocused) onCommit() },
            )
        }
    }
}

/**
 * Active time editor ported from SenderActiveScheduleEditor.tsx: per message
 * class rules with a blacklist/whitelist mode, weekday pills and time ranges.
 */
@Composable
private fun SenderActiveScheduleEditor(
    schedule: SenderActiveSchedule,
    locale: DesktopLocale,
    onChange: (SenderActiveSchedule) -> Unit,
) {
    val normalized = remember(schedule) { SenderActiveScheduleEvaluator.sanitize(schedule) }
    val counts = remember(normalized) { SenderActiveScheduleEvaluator.summarize(normalized) }
    val summary = if (counts.smsRanges == 0 && counts.appNotifyRanges == 0 && counts.callNotifyRanges == 0) {
        resolveSenderText(locale, ScheduleText.noRestrictions)
    } else {
        "${resolveSenderText(locale, ScheduleText.sms)} ${counts.smsRanges} · " +
            "${resolveSenderText(locale, ScheduleText.appNotify)} ${counts.appNotifyRanges} · " +
            "${resolveSenderText(locale, ScheduleText.callNotify)} ${counts.callNotifyRanges}"
    }

    fun updateRule(bucket: ScheduleBucket, nextRule: SenderActiveScheduleRule) {
        val next = when (bucket) {
            ScheduleBucket.SMS -> normalized.copy(sms = nextRule)
            ScheduleBucket.APP_NOTIFY -> normalized.copy(appNotify = nextRule)
            ScheduleBucket.CALL_NOTIFY -> normalized.copy(callNotify = nextRule)
        }
        onChange(SenderActiveScheduleEvaluator.sanitize(next))
    }

    SurfaceCard(
        title = resolveSenderText(locale, ScheduleText.title),
        subtitle = summary,
    ) {
        MetricRow(
            cards = listOf(
                {
                    ScheduleRuleCard(
                        title = resolveSenderText(locale, ScheduleText.sms),
                        rule = normalized.sms,
                        locale = locale,
                        onChange = { nextRule -> updateRule(ScheduleBucket.SMS, nextRule) },
                    )
                },
                {
                    ScheduleRuleCard(
                        title = resolveSenderText(locale, ScheduleText.appNotify),
                        rule = normalized.appNotify,
                        locale = locale,
                        onChange = { nextRule -> updateRule(ScheduleBucket.APP_NOTIFY, nextRule) },
                    )
                },
                {
                    ScheduleRuleCard(
                        title = resolveSenderText(locale, ScheduleText.callNotify),
                        rule = normalized.callNotify,
                        locale = locale,
                        onChange = { nextRule -> updateRule(ScheduleBucket.CALL_NOTIFY, nextRule) },
                    )
                },
            ),
        )
    }
}

@Composable
private fun ScheduleRuleCard(
    title: String,
    rule: SenderActiveScheduleRule,
    locale: DesktopLocale,
    onChange: (SenderActiveScheduleRule) -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ConsoleInk,
                )
                RelaySwitch(
                    checked = rule.enabled,
                    onCheckedChange = { enabled ->
                        onChange(
                            if (enabled && rule.ranges.isEmpty()) {
                                SenderActiveScheduleRule(
                                    enabled = true,
                                    mode = SenderActiveScheduleConst.MODE_BLACKLIST,
                                    weekdays = SenderActiveScheduleConst.ALL_WEEKDAYS,
                                    ranges = listOf(SenderActiveScheduleRange(start = "09:00", end = "18:00")),
                                )
                            } else {
                                rule.copy(enabled = enabled)
                            },
                        )
                    },
                )
            }

            RelaySelect(
                value = rule.mode,
                options = listOf(
                    RelayOption(
                        value = SenderActiveScheduleConst.MODE_BLACKLIST,
                        label = resolveSenderText(locale, ScheduleText.blacklist),
                    ),
                    RelayOption(
                        value = SenderActiveScheduleConst.MODE_WHITELIST,
                        label = resolveSenderText(locale, ScheduleText.whitelist),
                    ),
                ),
                onValueChange = { mode -> onChange(rule.copy(mode = mode)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = resolveSenderText(locale, ScheduleText.weekdays),
                style = MaterialTheme.typography.labelLarge,
                color = ConsoleInk,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SCHEDULE_DAYS.forEach { (value, label) ->
                    val selected = value in rule.weekdays
                    WeekdayPill(
                        text = resolveSenderText(locale, label),
                        selected = selected,
                        onClick = {
                            val next = if (selected) {
                                rule.weekdays.filter { it != value }
                            } else {
                                (rule.weekdays + value).sorted()
                            }
                            onChange(rule.copy(weekdays = if (next.isEmpty()) rule.weekdays else next))
                        },
                    )
                }
            }

            Text(
                text = resolveSenderText(locale, ScheduleText.ranges),
                style = MaterialTheme.typography.labelLarge,
                color = ConsoleInk,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rule.ranges.forEachIndexed { index, range ->
                    TimeRangeRow(
                        range = range,
                        onChangeStart = { start ->
                            onChange(
                                rule.copy(
                                    ranges = rule.ranges.mapIndexed { current, item ->
                                        if (current == index) item.copy(start = start) else item
                                    },
                                ),
                            )
                        },
                        onChangeEnd = { end ->
                            onChange(
                                rule.copy(
                                    ranges = rule.ranges.mapIndexed { current, item ->
                                        if (current == index) item.copy(end = end) else item
                                    },
                                ),
                            )
                        },
                        onRemove = {
                            onChange(rule.copy(ranges = rule.ranges.filterIndexed { current, _ -> current != index }))
                        },
                        removeLabel = resolveSenderText(locale, ScheduleText.remove),
                    )
                }
            }
            ActionButton(
                text = resolveSenderText(locale, ScheduleText.addRange),
                onClick = {
                    onChange(
                        rule.copy(
                            ranges = rule.ranges + SenderActiveScheduleRange(start = "09:00", end = "18:00"),
                        ),
                    )
                },
                tone = ActionTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun WeekdayPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFFEDF6CF) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFF7AA21D) else Color(0xFFD6DFB2)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color(0xFF31411C) else Color(0xFF667451),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun TimeRangeRow(
    range: SenderActiveScheduleRange,
    onChangeStart: (String) -> Unit,
    onChangeEnd: (String) -> Unit,
    onRemove: () -> Unit,
    removeLabel: String,
) {
    var startText by remember(range.start) { mutableStateOf(range.start) }
    var endText by remember(range.end) { mutableStateOf(range.end) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = startText,
            onValueChange = { next ->
                startText = next
                if (SenderActiveScheduleConst.parseMinutes(next) != null) onChangeStart(next)
            },
            placeholder = { Text("HH:MM") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = endText,
            onValueChange = { next ->
                endText = next
                if (SenderActiveScheduleConst.parseMinutes(next) != null) onChangeEnd(next)
            },
            placeholder = { Text("HH:MM") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        ActionButton(text = removeLabel, onClick = onRemove, tone = ActionTone.DANGER)
    }
}
