package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.remote.BindCodeResponse
import io.github.magisk317.relay.contract.remote.DeviceItem
import io.github.magisk317.relay.contract.remote.PatchDeviceRequest
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.local.DatabaseTransferController
import io.github.magisk317.relay.desktop.local.DesktopDiagnosticsController
import io.github.magisk317.relay.desktop.local.DiagnosticsOutcome
import io.github.magisk317.relay.desktop.local.TransferOutcome
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.ConfirmDialog
import io.github.magisk317.relay.desktop.ui.ConsoleInk
import io.github.magisk317.relay.desktop.ui.ConsoleMuted
import io.github.magisk317.relay.desktop.ui.ErrorBanner
import io.github.magisk317.relay.desktop.ui.LiveBadge
import io.github.magisk317.relay.desktop.ui.LoadingCard
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.QrCodeImage
import io.github.magisk317.relay.desktop.ui.RelayBadge
import io.github.magisk317.relay.desktop.ui.RelayTone
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import io.github.magisk317.relay.desktop.ui.buildBindQrValue
import io.github.magisk317.relay.desktop.ui.formatTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the export/import success hint stays before it self-clears. */
private const val TRANSFER_HINT_MS = 6_000L

/** Realtime event types that re-fetch the device list, mirroring the webUI list. */
private val ADVANCED_DEVICE_EVENTS = setOf(
    "device.registered",
    "device.updated",
    "device.heartbeat",
    "device.revoked",
)

/**
 * Desktop port of the webUI advanced page: the scheduled-tasks shortcut, the
 * one-shot bind code with its QR payload and the remote agent cards. The
 * browser prompt/confirm calls become dialogs and the scheduled-tasks link
 * navigates through [onOpenScheduledTasks] instead of window.location.
 */
@Composable
fun AdvancedPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    transfer: DatabaseTransferController,
    diagnostics: DesktopDiagnosticsController,
    locale: DesktopLocale,
    onOpenScheduledTasks: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var bindCode by remember { mutableStateOf<BindCodeResponse?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var renaming by remember { mutableStateOf<DeviceItem?>(null) }
    var revoking by remember { mutableStateOf<DeviceItem?>(null) }
    var confirmTransfer by remember { mutableStateOf(false) }
    var transferBusy by remember { mutableStateOf(false) }
    var transferHint by remember { mutableStateOf<String?>(null) }
    var diagnosticsBusy by remember { mutableStateOf(false) }

    // Success hints self-clear like the header link hint; failures go to the
    // error banner so they stay until the next action.
    LaunchedEffect(transferHint) {
        if (transferHint != null) {
            delay(TRANSFER_HINT_MS)
            transferHint = null
        }
    }

    fun exportTransfer() {
        transferBusy = true
        scope.launch {
            when (val outcome = transfer.export()) {
                is TransferOutcome.Exported -> transferHint = DesktopMessages.t(
                    locale,
                    "advanced.databaseExportDone",
                    mapOf("path" to outcome.file.absolutePath),
                )
                is TransferOutcome.Imported -> Unit
                is TransferOutcome.Cancelled -> Unit
                is TransferOutcome.Failed -> error =
                    DesktopMessages.t(locale, "advanced.databaseFailed") + outcome.message
            }
            transferBusy = false
        }
    }

    fun exportDiagnostics() {
        diagnosticsBusy = true
        scope.launch {
            when (val outcome = diagnostics.export()) {
                is DiagnosticsOutcome.Exported -> transferHint = DesktopMessages.t(
                    locale,
                    "advanced.databaseExportDone",
                    mapOf("path" to outcome.file.absolutePath),
                )
                is DiagnosticsOutcome.Cancelled -> Unit
                is DiagnosticsOutcome.Failed -> error = DesktopMessages.t(locale, "advanced.databaseFailed") + outcome.message
            }
            diagnosticsBusy = false
        }
    }

    fun importTransfer() {
        confirmTransfer = false
        transferBusy = true
        scope.launch {
            when (val outcome = transfer.import()) {
                is TransferOutcome.Imported -> transferHint = DesktopMessages.t(
                    locale,
                    "advanced.databaseImportDone",
                    mapOf(
                        "devices" to outcome.snapshot.devices.size,
                        "records" to outcome.snapshot.records.size,
                    ),
                )
                is TransferOutcome.Exported -> Unit
                is TransferOutcome.Cancelled -> Unit
                is TransferOutcome.Failed -> error =
                    DesktopMessages.t(locale, "advanced.databaseFailed") + outcome.message
            }
            transferBusy = false
        }
    }

    suspend fun load() {
        try {
            loading = true
            error = ""
            console.refreshDevices()
        } catch (failure: Exception) {
            error = failure.message ?: DesktopMessages.t(locale, "common.loadFailed")
        } finally {
            loading = false
        }
    }

    fun reload() {
        scope.launch { load() }
    }

    fun patch(device: DeviceItem, request: PatchDeviceRequest) {
        val client = session.currentClient() ?: return
        scope.launch {
            runCatching { client.patchDevice(device.id, request) }
                .onSuccess { reload() }
                .onFailure { failure ->
                    error = failure.message ?: DesktopMessages.t(locale, "common.saveFailed")
                }
        }
    }

    fun revoke(device: DeviceItem) {
        val client = session.currentClient() ?: return
        scope.launch {
            runCatching { client.revokeDevice(device.id) }
                .onSuccess { reload() }
                .onFailure { failure ->
                    error = failure.message ?: DesktopMessages.t(locale, "common.saveFailed")
                }
        }
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(feed.lastEvent) {
        if (feed.lastEvent?.type in ADVANCED_DEVICE_EVENTS) reload()
    }

    val actions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        ActionButton(
            text = DesktopMessages.t(locale, "advanced.refresh"),
            onClick = { reload() },
            tone = ActionTone.NEUTRAL,
        )
    }

    if (loading && console.devices.isEmpty() && error.isBlank() && bindCode == null) {
        PageShell(
            title = DesktopMessages.t(locale, "advanced.title"),
            description = DesktopMessages.t(locale, "advanced.description"),
            badge = DesktopMessages.t(locale, "advanced.title"),
            locale = locale,
            actions = actions,
        ) {
            LoadingCard(
                title = DesktopMessages.t(locale, "advanced.loadingTitle"),
                message = DesktopMessages.t(locale, "advanced.loadingMessage"),
                locale = locale,
            )
        }
        return
    }

    PageShell(
        title = DesktopMessages.t(locale, "advanced.title"),
        description = DesktopMessages.t(locale, "advanced.remoteDescription"),
        badge = DesktopMessages.t(locale, "advanced.title"),
        locale = locale,
        actions = actions,
    ) {
        ErrorBanner(message = error, locale = locale)

        SurfaceCard(
            title = DesktopMessages.t(locale, "advanced.scheduledTasksTitle"),
            subtitle = DesktopMessages.t(locale, "advanced.scheduledTasksSubtitle"),
        ) {
            ActionButton(
                text = DesktopMessages.t(locale, "advanced.scheduledTasksOpen"),
                onClick = onOpenScheduledTasks,
                tone = ActionTone.PRIMARY,
            )
        }

        // Local database (parity §5): the webUI's export/import buttons. The
        // card is inert with the mirror closed (Remote run mode), because the
        // snapshot rides the mirror's own connection.
        SurfaceCard(
            title = DesktopMessages.t(locale, "advanced.databaseTitle"),
            subtitle = DesktopMessages.t(locale, "advanced.databaseSubtitle"),
        ) {
            if (!transfer.available) {
                Text(
                    text = DesktopMessages.t(locale, "advanced.databaseUnavailable"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConsoleMuted,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton(
                            text = DesktopMessages.t(locale, "advanced.databaseExport"),
                            onClick = { exportTransfer() },
                            tone = ActionTone.PRIMARY,
                            enabled = !transferBusy,
                        )
                        ActionButton(
                            text = DesktopMessages.t(locale, "advanced.databaseImport"),
                            onClick = { confirmTransfer = true },
                            tone = ActionTone.WARNING,
                            enabled = !transferBusy,
                        )
                    }
                    transferHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConsoleMuted,
                        )
                    }
                }
            }
        }

        // Diagnostics (parity §5): token-free support bundle. Always
        // available - a broken mirror is when it is needed most, so unlike
        // the database card it does not gate on the mirror being open.
        SurfaceCard(
            title = DesktopMessages.t(locale, "advanced.diagnosticsTitle"),
            subtitle = DesktopMessages.t(locale, "advanced.diagnosticsSubtitle"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = DesktopMessages.t(locale, "advanced.diagnosticsExport"),
                    onClick = { exportDiagnostics() },
                    tone = ActionTone.PRIMARY,
                    enabled = !diagnosticsBusy,
                )
            }
        }

        SurfaceCard(
            title = DesktopMessages.t(locale, "advanced.bindTitle"),
            subtitle = DesktopMessages.t(locale, "advanced.bindSubtitle"),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionButton(
                        text = DesktopMessages.t(locale, "advanced.bindGenerate"),
                        onClick = {
                            val client = session.currentClient()
                            if (client == null) {
                                error = DesktopMessages.t(locale, "common.loadFailed")
                                return@ActionButton
                            }
                            scope.launch {
                                runCatching { client.createBindCode() }
                                    .onSuccess { generated -> bindCode = generated }
                                    .onFailure { failure ->
                                        error = failure.message
                                            ?: DesktopMessages.t(locale, "common.loadFailed")
                                    }
                            }
                        },
                        tone = ActionTone.PRIMARY,
                    )
                    val current = bindCode
                    if (current != null) {
                        RelayBadge(text = current.code, tone = RelayTone.ACCENT)
                        RelayBadge(text = formatTimestamp(current.expiresAt))
                    }
                }
                if (bindCode != null) {
                    val bindQrValue = buildBindQrValue(
                        code = bindCode?.code.orEmpty(),
                        baseUrl = session.activeProfile?.baseUrl.orEmpty(),
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD9E6B1)),
                    ) {
                        QrCodeImage(
                            value = bindQrValue,
                            modifier = Modifier.padding(16.dp),
                            qrSize = 180.dp,
                        )
                    }
                    Text(
                        text = DesktopMessages.t(locale, "advanced.bindQrHint"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ConsoleMuted,
                    )
                }
            }
        }

        val devices = console.devices
        if (devices.isEmpty()) {
            SurfaceCard(
                title = DesktopMessages.t(locale, "advanced.noAgentsTitle"),
                subtitle = DesktopMessages.t(locale, "advanced.noAgentsSubtitle"),
            ) {
                Text(
                    text = DesktopMessages.t(locale, "advanced.noAgentsBody"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConsoleMuted,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        locale = locale,
                        onRename = {
                            renaming = device
                        },
                        onToggle = {
                            patch(device, PatchDeviceRequest(enabled = !device.enabled))
                        },
                        onRevoke = { revoking = device },
                    )
                }
            }
        }
    }

    val renameTarget = renaming
    if (renameTarget != null) {
        RenameDeviceDialog(
            initialName = renameTarget.displayName.ifBlank { renameTarget.deviceName },
            locale = locale,
            onDismiss = { renaming = null },
            onConfirm = { nextName ->
                renaming = null
                patch(renameTarget, PatchDeviceRequest(displayName = nextName))
            },
        )
    }

    if (confirmTransfer) {
        ConfirmDialog(
            message = DesktopMessages.t(locale, "advanced.databaseImportConfirm"),
            confirmLabel = DesktopMessages.t(locale, "advanced.databaseImport"),
            dismissLabel = DesktopMessages.t(locale, "common.cancel"),
            onConfirm = { importTransfer() },
            onDismiss = { confirmTransfer = false },
        )
    }

    val revokeTarget = revoking
    if (revokeTarget != null) {
        ConfirmDialog(
            message = DesktopMessages.t(locale, "advanced.deviceRevokeConfirm"),
            confirmLabel = DesktopMessages.t(locale, "advanced.deviceRevoke"),
            dismissLabel = DesktopMessages.t(locale, "common.cancel"),
            onConfirm = {
                revoke(revokeTarget)
                revoking = null
            },
            onDismiss = { revoking = null },
        )
    }
}

/** One remote agent in the list; the actions mirror the webUI's per-device row. */
@Composable
private fun DeviceCard(
    device: DeviceItem,
    locale: DesktopLocale,
    onRename: () -> Unit,
    onToggle: () -> Unit,
    onRevoke: () -> Unit,
) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = device.displayName.ifBlank { device.deviceName },
                    style = MaterialTheme.typography.titleLarge,
                    color = ConsoleInk,
                )
                Text(
                    text = "${device.platform} / " +
                        device.deviceModel.ifBlank { DesktopMessages.t(locale, "common.unknownModel") } +
                        " / " +
                        device.appVersion.ifBlank { DesktopMessages.t(locale, "common.unknownVersion") },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConsoleMuted,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RelayBadge(
                        text = DesktopMessages.t(
                            locale,
                            if (device.enabled) "common.enabled" else "common.disabled",
                        ),
                        tone = if (device.enabled) RelayTone.SUCCESS else RelayTone.WARNING,
                    )
                    RelayBadge(
                        text = DesktopMessages.t(
                            locale,
                            if (device.lastSeenAt != null) "common.lastSeen" else "common.neverSeen",
                            if (device.lastSeenAt != null) {
                                mapOf("time" to formatTimestamp(device.lastSeenAt))
                            } else {
                                emptyMap()
                            },
                        ),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = DesktopMessages.t(locale, "advanced.deviceRename"),
                    onClick = onRename,
                )
                ActionButton(
                    text = DesktopMessages.t(
                        locale,
                        if (device.enabled) "advanced.deviceDisable" else "advanced.deviceEnable",
                    ),
                    onClick = onToggle,
                    tone = if (device.enabled) ActionTone.WARNING else ActionTone.PRIMARY,
                )
                ActionButton(
                    text = DesktopMessages.t(locale, "advanced.deviceRevoke"),
                    onClick = onRevoke,
                    tone = ActionTone.DANGER,
                )
            }
        }
    }
}

/** Replacement for window.prompt: a dialog with the current display name prefilled. */
@Composable
private fun RenameDeviceDialog(
    initialName: String,
    locale: DesktopLocale,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = DesktopMessages.t(locale, "advanced.deviceRenamePrompt")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(text = DesktopMessages.t(locale, "advanced.deviceRename")) },
                singleLine = true,
            )
        },
        confirmButton = {
            ActionButton(
                text = DesktopMessages.t(locale, "advanced.deviceRename"),
                onClick = { onConfirm(name) },
                tone = ActionTone.PRIMARY,
            )
        },
        dismissButton = {
            ActionButton(
                text = DesktopMessages.t(locale, "common.cancel"),
                onClick = onDismiss,
                tone = ActionTone.NEUTRAL,
            )
        },
    )
}
