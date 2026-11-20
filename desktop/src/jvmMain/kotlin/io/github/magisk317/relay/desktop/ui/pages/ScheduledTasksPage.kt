package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.model.SnapshotScheduledTask
import io.github.magisk317.relay.desktop.config.buildReplaceScheduledTasksMutation
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopRunMode
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.ConfirmDialog
import io.github.magisk317.relay.desktop.ui.EmptyCard
import io.github.magisk317.relay.desktop.ui.ErrorBanner
import io.github.magisk317.relay.desktop.ui.LiveBadge
import io.github.magisk317.relay.desktop.ui.LoadingCard
import io.github.magisk317.relay.desktop.ui.NoticeCard
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.RelayBadge
import io.github.magisk317.relay.desktop.ui.RelayOption
import io.github.magisk317.relay.desktop.ui.RelaySelect
import io.github.magisk317.relay.desktop.ui.RelaySwitch
import io.github.magisk317.relay.desktop.ui.RelayTone
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private data class ScheduledTaskDraft(
    val name: String = "",
    val cronExpression: String = "0 9 * * *",
    val simSlot: Int = 0,
    val mobiles: String = "",
    val content: String = "",
    val enabled: Boolean = true,
)

@Composable
fun ScheduledTasksPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editingSyncId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(ScheduledTaskDraft()) }
    var deleteTarget by remember { mutableStateOf<SnapshotScheduledTask?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }

    val deviceId = console.selectedDeviceId
    val device = console.devices.firstOrNull { it.id == deviceId }
    val tasks = console.root.scheduledTasks
    val conflicts = tasks.filter { !it.conflictId.isNullOrBlank() }.groupBy { it.conflictId!! }
    val activeTasks = tasks.filter { it.conflictId.isNullOrBlank() && !it.syncDeleted }
    val deviceTimeZone = device?.timeZone?.ifBlank { "UTC" } ?: "UTC"
    val heartbeatEligible = isScheduledTaskHeartbeatEligible(device?.lastSeenAt, now)
    val supportsScheduledTasks = device?.capabilities?.get("scheduledTasks")?.jsonPrimitive?.contentOrNull == "true"
    val canEdit = session.runMode.allowsRemoteDeviceActions && session.authenticated &&
        device != null && supportsScheduledTasks && heartbeatEligible && console.config != null && !console.saving
    val pendingTaskCommand = console.config?.pendingCommands
        ?.filter { hasScheduledTaskMutation(it.mutation) }
        ?.maxByOrNull { it.targetRevision }

    LaunchedEffect(session.authenticated, session.runMode, deviceId) {
        try {
            console.refreshDevices()
        } catch (failure: Exception) {
            console.showError(failure.message ?: DesktopMessages.t(locale, "common.loadFailed"))
        }
    }
    LaunchedEffect(feed.lastEvent, deviceId) {
        if (feed.lastEvent?.type == "device.config.updated" || feed.lastEvent?.type == "device.config.command.updated") {
            try {
                console.refreshConfig()
            } catch (failure: Exception) {
                console.showError(failure.message ?: DesktopMessages.t(locale, "common.loadFailed"))
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = Instant.now()
        }
    }

    fun queueTasks(nextTasks: List<SnapshotScheduledTask>, summary: String, onSaved: () -> Unit = {}) {
        if (!canEdit) {
            console.showError(DesktopMessages.t(locale, "scheduledTasks.writeUnavailable"))
            return
        }
        scope.launch {
            runCatching { console.queueMutation(buildReplaceScheduledTasksMutation(nextTasks), summary) }
                .onSuccess {
                    console.showError("")
                    onSaved()
                }
                .onFailure { failure ->
                    console.showError(failure.message ?: DesktopMessages.t(locale, "common.saveFailed"))
                }
        }
    }

    fun resetEditor() {
        editorOpen = false
        editingSyncId = null
        draft = ScheduledTaskDraft()
    }

    fun saveTask() {
        if (draft.name.isBlank() || draft.cronExpression.isBlank() || draft.mobiles.isBlank() || draft.content.isBlank()) {
            console.showError(DesktopMessages.t(locale, "scheduledTasks.requiredFields"))
            return
        }
        val existing = editingSyncId?.let { id -> tasks.firstOrNull { it.syncId == id } }
        val saved = SnapshotScheduledTask(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            name = draft.name.trim(),
            taskType = "sms",
            cronExpression = draft.cronExpression.trim(),
            simSlot = draft.simSlot,
            mobiles = draft.mobiles.trim(),
            content = draft.content,
            status = if (draft.enabled) 1 else 0,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        val next = if (existing == null) tasks + saved else tasks.map { if (it.syncId == existing.syncId) saved else it }
        queueTasks(next, DesktopMessages.t(locale, if (existing == null) "scheduledTasks.createSummary" else "scheduledTasks.updateSummary")) {
            resetEditor()
        }
    }

    fun updateTask(task: SnapshotScheduledTask, nextStatus: Int) {
        queueTasks(
            tasks.map { if (it.syncId == task.syncId) it.copy(status = nextStatus) else it },
            DesktopMessages.t(locale, "scheduledTasks.updateSummary"),
        )
    }

    fun resolveConflict(conflictId: String, choice: ScheduledTaskConflictChoice) {
        runCatching { resolveScheduledTaskConflict(tasks, conflictId, choice) }
            .onSuccess { next -> queueTasks(next, DesktopMessages.t(locale, "scheduledTasks.resolveSummary")) }
            .onFailure { failure -> console.showError(failure.message ?: DesktopMessages.t(locale, "common.saveFailed")) }
    }

    val deviceOptions = console.devices.map { item ->
        RelayOption(
            value = item.id.toString(),
            label = item.displayName.ifBlank { item.deviceName },
            description = "${item.platform} / ${item.timeZone.ifBlank { "UTC" }}",
        )
    }
    val headerActions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        if (deviceOptions.isNotEmpty()) {
            RelaySelect(
                value = deviceId?.toString().orEmpty(),
                options = deviceOptions,
                onValueChange = { value -> value.toLongOrNull()?.let(console::selectDevice) },
            )
        }
        ActionButton(
            text = DesktopMessages.t(locale, "common.refresh"),
            onClick = {
                scope.launch {
                    try {
                        console.refreshConfig()
                    } catch (failure: Exception) {
                        console.showError(failure.message ?: DesktopMessages.t(locale, "common.loadFailed"))
                    }
                }
            },
            tone = ActionTone.NEUTRAL,
        )
    }

    PageShell(
        title = DesktopMessages.t(locale, "scheduledTasks.title"),
        description = DesktopMessages.t(locale, "scheduledTasks.description"),
        badge = DesktopMessages.t(locale, "layout.nav.group.automation"),
        locale = locale,
        actions = headerActions,
    ) {
        ErrorBanner(message = console.error, locale = locale)
        if (device == null) {
            EmptyCard(
                title = DesktopMessages.t(locale, "scheduledTasks.noDevice"),
                message = DesktopMessages.t(locale, "scheduledTasks.noDeviceHint"),
            )
            return@PageShell
        }

        if (!session.runMode.allowsRemoteDeviceActions) {
            NoticeCard(DesktopMessages.t(locale, "scheduledTasks.remoteOnly"))
        } else if (!session.authenticated) {
            NoticeCard(DesktopMessages.t(locale, "scheduledTasks.signInHint"))
        } else if (!supportsScheduledTasks) {
            NoticeCard(DesktopMessages.t(locale, "scheduledTasks.capabilityUnavailable"))
        } else if (!heartbeatEligible) {
            NoticeCard(DesktopMessages.t(locale, "scheduledTasks.heartbeatStale"))
        }

        SurfaceCard(
            title = DesktopMessages.t(locale, "scheduledTasks.deviceTitle"),
            subtitle = DesktopMessages.t(locale, "scheduledTasks.deviceSubtitle", mapOf("timeZone" to deviceTimeZone)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelayBadge(text = DesktopMessages.t(locale, "records.deviceBadge", mapOf("deviceId" to device.id)))
                RelayBadge(
                    text = DesktopMessages.t(locale, if (heartbeatEligible) "scheduledTasks.heartbeatFresh" else "scheduledTasks.heartbeatStale"),
                    tone = if (heartbeatEligible) RelayTone.SUCCESS else RelayTone.WARNING,
                )
                pendingTaskCommand?.let { command ->
                    RelayBadge(
                        text = DesktopMessages.t(locale, "scheduledTasks.commandState", mapOf("revision" to command.targetRevision, "status" to command.status)),
                        tone = if (command.status == "pending") RelayTone.WARNING else if (command.status == "failed" || command.status == "stale") RelayTone.DANGER else RelayTone.SUCCESS,
                    )
                }
            }
        }

        SurfaceCard(
            title = DesktopMessages.t(locale, "scheduledTasks.listTitle"),
            subtitle = DesktopMessages.t(locale, "scheduledTasks.listSubtitle"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (activeTasks.isEmpty() && conflicts.isEmpty()) {
                    Text(
                        text = DesktopMessages.t(locale, "scheduledTasks.emptySubtitle"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                activeTasks.forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(task.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "${task.cronExpression} · " + DesktopMessages.t(locale, "scheduledTasks.simSlot", mapOf("simSlot" to task.simSlot)) + " · ${task.mobiles} · ${task.content}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RelayBadge(
                            text = DesktopMessages.t(locale, if (task.status == 1) "common.enabled" else "common.disabled"),
                            tone = if (task.status == 1) RelayTone.SUCCESS else RelayTone.MUTED,
                        )
                        RelaySwitch(
                            checked = task.status == 1,
                            onCheckedChange = { enabled -> updateTask(task, if (enabled) 1 else 0) },
                            enabled = canEdit,
                        )
                        ActionButton(
                            text = DesktopMessages.t(locale, "scheduledTasks.editAction"),
                            onClick = {
                                editingSyncId = task.syncId
                                draft = ScheduledTaskDraft(task.name, task.cronExpression, task.simSlot, task.mobiles, task.content, task.status == 1)
                                editorOpen = true
                            },
                            enabled = canEdit,
                        )
                        ActionButton(
                            text = DesktopMessages.t(locale, "common.delete"),
                            onClick = { deleteTarget = task },
                            tone = ActionTone.DANGER,
                            enabled = canEdit,
                        )
                    }
                }
                conflicts.forEach { (conflictId, variants) ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = DesktopMessages.t(locale, "scheduledTasks.conflictsTitle"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        variants.forEach { task ->
                            Text(
                                text = "${DesktopMessages.t(locale, if (task.conflictSide == "local") "scheduledTasks.localVersion" else "scheduledTasks.remoteVersion")}: " +
                                    if (task.syncDeleted) DesktopMessages.t(locale, "scheduledTasks.deletedTask", mapOf("name" to task.name)) else "${task.name} · ${task.cronExpression} · ${task.mobiles}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton(text = DesktopMessages.t(locale, "scheduledTasks.useLocal"), onClick = { resolveConflict(conflictId, ScheduledTaskConflictChoice.LOCAL) }, enabled = canEdit)
                            ActionButton(text = DesktopMessages.t(locale, "scheduledTasks.useRemote"), onClick = { resolveConflict(conflictId, ScheduledTaskConflictChoice.REMOTE) }, enabled = canEdit)
                            ActionButton(
                                text = DesktopMessages.t(locale, "scheduledTasks.keepBoth"),
                                onClick = { resolveConflict(conflictId, ScheduledTaskConflictChoice.KEEP_BOTH) },
                                enabled = canEdit && variants.any { !it.syncDeleted },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                ActionButton(
                    text = DesktopMessages.t(locale, "scheduledTasks.addAction"),
                    onClick = {
                        editingSyncId = null
                        draft = ScheduledTaskDraft()
                        editorOpen = true
                    },
                    tone = ActionTone.PRIMARY,
                    enabled = canEdit,
                )
            }
        }

        if (editorOpen) {
            SurfaceCard(
                title = DesktopMessages.t(locale, if (editingSyncId == null) "scheduledTasks.createTitle" else "scheduledTasks.editTitle"),
                subtitle = DesktopMessages.t(locale, "scheduledTasks.formSubtitle"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text(DesktopMessages.t(locale, "scheduledTasks.field.name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.cronExpression, { draft = draft.copy(cronExpression = it) }, label = { Text(DesktopMessages.t(locale, "scheduledTasks.field.cron")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    RelaySelect(
                        value = draft.simSlot.toString(),
                        options = listOf(
                            RelayOption("0", DesktopMessages.t(locale, "scheduledTasks.sim.auto")),
                            RelayOption("1", DesktopMessages.t(locale, "scheduledTasks.sim.one")),
                            RelayOption("2", DesktopMessages.t(locale, "scheduledTasks.sim.two")),
                        ),
                        onValueChange = { draft = draft.copy(simSlot = it.toIntOrNull() ?: 0) },
                    )
                    OutlinedTextField(draft.mobiles, { draft = draft.copy(mobiles = it) }, label = { Text(DesktopMessages.t(locale, "scheduledTasks.field.mobiles")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.content, { draft = draft.copy(content = it) }, label = { Text(DesktopMessages.t(locale, "scheduledTasks.field.content")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RelaySwitch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) }, enabled = !console.saving)
                        Text(DesktopMessages.t(locale, if (draft.enabled) "common.enabled" else "common.disabled"), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(text = DesktopMessages.t(locale, "common.save"), onClick = { saveTask() }, tone = ActionTone.PRIMARY, enabled = canEdit)
                        ActionButton(text = DesktopMessages.t(locale, "common.cancel"), onClick = { resetEditor() })
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            message = DesktopMessages.t(locale, "scheduledTasks.deleteConfirm", mapOf("name" to target.name)),
            confirmLabel = DesktopMessages.t(locale, "common.delete"),
            dismissLabel = DesktopMessages.t(locale, "common.cancel"),
            onConfirm = {
                queueTasks(tasks.filterNot { it.syncId == target.syncId }, DesktopMessages.t(locale, "scheduledTasks.deleteSummary"))
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun hasScheduledTaskMutation(mutation: JsonObject): Boolean {
    val operations = mutation["operations"] as? JsonArray ?: return false
    return operations.any { operation ->
        (operation as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "replace_scheduled_tasks"
    }
}
