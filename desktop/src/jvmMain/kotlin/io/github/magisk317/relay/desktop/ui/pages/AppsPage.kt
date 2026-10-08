package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import io.github.magisk317.relay.contract.model.SnapshotAppInfo
import io.github.magisk317.relay.desktop.config.buildReplaceDeviceAppsMutation
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.ConsoleInk
import io.github.magisk317.relay.desktop.ui.ConsoleMuted
import io.github.magisk317.relay.desktop.ui.ConfirmDialog
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
import io.github.magisk317.relay.desktop.ui.RelayTone
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import io.github.magisk317.relay.desktop.ui.ToggleRow
import kotlinx.coroutines.launch

/**
 * Desktop port of the webUI apps page: app-level interception, forwarding
 * and template editing for the selected device. Text fields commit on focus
 * loss, mirroring the onBlur handlers of the React page.
 */
@Composable
fun AppsPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var draftPackageName by remember { mutableStateOf("") }
    var draftLabel by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val appInfos = remember(console.root, console.selectedDeviceId) {
        val deviceId = console.selectedDeviceId
        if (deviceId == null) emptyList() else console.root.deviceAppInfos[deviceId.toString()] ?: emptyList()
    }
    val notifyRoutes = console.root.notifyRoutes
    val smsCodeRules = console.root.smsCodeRules
    val forwardFilters = console.root.forwardFilters

    val filteredItems = remember(appInfos, search) {
        val keyword = search.trim().lowercase()
        if (keyword.isEmpty()) {
            appInfos
        } else {
            appInfos.filter { item ->
                item.packageName.lowercase().contains(keyword) ||
                    (item.label?.lowercase()?.contains(keyword) == true)
            }
        }
    }

    fun persistApps(nextApps: List<SnapshotAppInfo>) {
        val deviceId = console.selectedDeviceId ?: return
        scope.launch {
            runCatching { console.queueMutation(buildReplaceDeviceAppsMutation(deviceId, nextApps), "apps:update") }
        }
    }

    val actions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        ActionButton(
            text = DesktopMessages.t(locale, "apps.refresh"),
            onClick = { scope.launch { runCatching { console.refreshConfig() } } },
            tone = ActionTone.NEUTRAL,
        )
    }

    if (console.loading && console.config == null && console.error.isBlank()) {
        PageShell(
            title = DesktopMessages.t(locale, "apps.title"),
            description = DesktopMessages.t(locale, "apps.description"),
            badge = DesktopMessages.t(locale, "apps.title"),
            locale = locale,
            actions = actions,
        ) {
            LoadingCard(
                title = DesktopMessages.t(locale, "apps.loadingTitle"),
                message = DesktopMessages.t(locale, "apps.loadingMessage"),
                locale = locale,
            )
        }
        return
    }

    PageShell(
        title = DesktopMessages.t(locale, "apps.title"),
        description = DesktopMessages.t(locale, "apps.remoteDescription"),
        badge = DesktopMessages.t(locale, "apps.title"),
        locale = locale,
        actions = actions,
    ) {
        ErrorBanner(message = console.error, locale = locale)

        if (console.devices.isNotEmpty()) {
            RelaySelect(
                value = console.selectedDeviceId?.toString() ?: "",
                options = console.devices.map { device ->
                    RelayOption(
                        value = device.id.toString(),
                        label = "${device.deviceName} ${device.deviceModel} (ID: ${device.id})",
                    )
                },
                onValueChange = { value -> value.toLongOrNull()?.let { console.selectDevice(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MetricRow(
            cards = listOf(
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "apps.metric.tracked"),
                        value = appInfos.size.toString(),
                        helper = DesktopMessages.t(locale, "apps.metric.trackedHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "apps.metric.blocked"),
                        value = appInfos.count { it.blocked }.toString(),
                        tone = MetricTone.WARNING,
                        helper = DesktopMessages.t(locale, "apps.metric.blockedHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "apps.metric.forwarding"),
                        value = appInfos.count { it.forwarding }.toString(),
                        tone = MetricTone.SUCCESS,
                        helper = DesktopMessages.t(locale, "apps.metric.forwardingHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "apps.metric.routingAssets"),
                        value = (notifyRoutes.size + smsCodeRules.size + forwardFilters.size).toString(),
                        tone = MetricTone.INFO,
                        helper = DesktopMessages.t(locale, "apps.metric.routingAssetsHelper"),
                    )
                },
            ),
        )

        SurfaceCard(
            title = DesktopMessages.t(locale, "apps.addTitle"),
            subtitle = DesktopMessages.t(locale, "apps.addSubtitle"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draftPackageName,
                    onValueChange = { draftPackageName = it },
                    label = { Text(DesktopMessages.t(locale, "apps.packagePlaceholder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draftLabel,
                        onValueChange = { draftLabel = it },
                        label = { Text(DesktopMessages.t(locale, "apps.labelPlaceholder")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        text = if (console.saving) {
                            DesktopMessages.t(locale, "common.saving")
                        } else {
                            DesktopMessages.t(locale, "apps.addAction")
                        },
                        onClick = {
                            val packageName = draftPackageName.trim()
                            if (packageName.isEmpty()) {
                                console.showError(DesktopMessages.t(locale, "apps.packageRequired"))
                                return@ActionButton
                            }
                            val nextApp = SnapshotAppInfo(
                                packageName = packageName,
                                label = draftLabel.trim().ifEmpty { packageName },
                            )
                            persistApps(listOf(nextApp) + appInfos.filter { it.packageName != packageName })
                            draftPackageName = ""
                            draftLabel = ""
                        },
                        tone = ActionTone.PRIMARY,
                        enabled = !console.saving,
                    )
                }
            }
        }

        SurfaceCard(
            title = DesktopMessages.t(locale, "apps.listTitle"),
            subtitle = DesktopMessages.t(
                locale,
                "apps.filteredSubtitle",
                mapOf("filtered" to filteredItems.size, "total" to appInfos.size),
            ),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(DesktopMessages.t(locale, "apps.searchPlaceholder")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            if (filteredItems.isEmpty()) {
                EmptyCard(
                    title = DesktopMessages.t(
                        locale,
                        if (appInfos.isNotEmpty()) "apps.emptyFilteredTitle" else "apps.emptyTitle",
                    ),
                    message = DesktopMessages.t(
                        locale,
                        if (appInfos.isNotEmpty()) "apps.emptyFilteredMessage" else "apps.emptyMessage",
                    ),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    filteredItems.forEach { item ->
                        val routeCount = notifyRoutes.count { it.packageName == item.packageName }
                        val filterCount = forwardFilters.count { rule ->
                            rule.scopeKey == item.packageName || rule.scopeKey.startsWith("${item.packageName}:")
                        }
                        AppCard(
                            item = item,
                            routeCount = routeCount,
                            filterCount = filterCount,
                            locale = locale,
                            saving = console.saving,
                            onPersist = { updated ->
                                persistApps(appInfos.map { if (it.packageName == item.packageName) updated else it })
                            },
                            onDeleteRequest = { pendingDelete = item.packageName },
                        )
                    }
                }
            }
        }
    }

    val deletingPackage = pendingDelete
    if (deletingPackage != null) {
        ConfirmDialog(
            message = DesktopMessages.t(locale, "apps.deleteConfirm"),
            confirmLabel = DesktopMessages.t(locale, "common.delete"),
            dismissLabel = DesktopMessages.t(locale, "common.cancel"),
            onConfirm = {
                persistApps(appInfos.filter { it.packageName != deletingPackage })
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun AppCard(
    item: SnapshotAppInfo,
    routeCount: Int,
    filterCount: Int,
    locale: DesktopLocale,
    saving: Boolean,
    onPersist: (SnapshotAppInfo) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var label by remember(item.packageName) { mutableStateOf(item.label.orEmpty()) }
    var notifyTemplate by remember(item.packageName) { mutableStateOf(item.notifyTemplate) }

    fun commitLabel() {
        if (label != item.label.orEmpty()) onPersist(item.copy(label = label))
    }

    fun commitTemplate() {
        if (notifyTemplate != item.notifyTemplate) {
            onPersist(
                item.copy(
                    notifyTemplate = notifyTemplate,
                    forwardingConfigured = notifyTemplate.isNotBlank() || item.forwardingConfigured,
                ),
            )
        }
    }

    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label?.takeIf { it.isNotBlank() } ?: item.packageName,
                    style = MaterialTheme.typography.titleMedium,
                    color = ConsoleInk,
                )
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = ConsoleMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RelayBadge(
                        text = DesktopMessages.t(
                            locale,
                            if (item.blocked) "apps.badge.blocked" else "apps.badge.allowed",
                        ),
                        tone = if (item.blocked) RelayTone.WARNING else RelayTone.MUTED,
                    )
                    RelayBadge(
                        text = DesktopMessages.t(
                            locale,
                            if (item.forwarding) "apps.badge.forwardingEnabled" else "apps.badge.forwardingDisabled",
                        ),
                        tone = if (item.forwarding) RelayTone.SUCCESS else RelayTone.MUTED,
                    )
                    RelayBadge(
                        text = DesktopMessages.t(locale, "apps.badge.routes", mapOf("count" to routeCount)),
                    )
                    RelayBadge(
                        text = DesktopMessages.t(locale, "apps.badge.filters", mapOf("count" to filterCount)),
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
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(DesktopMessages.t(locale, "apps.labelPlaceholder")) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focus -> if (!focus.isFocused) commitLabel() },
                )
                SurfaceCard(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DesktopMessages.t(locale, "apps.forwardingConfigured") + ":",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConsoleMuted,
                        )
                        Text(
                            text = DesktopMessages.t(
                                locale,
                                if (item.forwardingConfigured) "common.yes" else "common.no",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConsoleInk,
                        )
                    }
                }
            }

            ToggleRow(
                label = DesktopMessages.t(locale, "apps.table.blocked"),
                checked = item.blocked,
                enabled = !saving,
                onCheckedChange = { value -> onPersist(item.copy(blocked = value)) },
            )
            ToggleRow(
                label = DesktopMessages.t(locale, "apps.table.forwarding"),
                checked = item.forwarding,
                enabled = !saving,
                onCheckedChange = { value ->
                    onPersist(
                        item.copy(
                            forwarding = value,
                            forwardingConfigured = value || item.forwardingConfigured,
                        ),
                    )
                },
            )

            OutlinedTextField(
                value = notifyTemplate,
                onValueChange = { notifyTemplate = it },
                label = { Text(DesktopMessages.t(locale, "apps.table.template")) },
                minLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus -> if (!focus.isFocused) commitTemplate() },
            )
        }
    }
}
