package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.remote.RelayRecord
import io.github.magisk317.relay.desktop.ui.pages.records.EnrichedRecord
import io.github.magisk317.relay.desktop.ui.pages.records.parseRecordMetadata
import io.github.magisk317.relay.desktop.ui.pages.records.resolveLineLabel
import io.github.magisk317.relay.desktop.ui.pages.records.resolveSimLabel
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.ConsoleInk
import io.github.magisk317.relay.desktop.ui.ConsoleMuted
import io.github.magisk317.relay.desktop.ui.EmptyCard
import io.github.magisk317.relay.desktop.ui.ErrorBanner
import io.github.magisk317.relay.desktop.ui.FacetBadge
import io.github.magisk317.relay.desktop.ui.FacetTone
import io.github.magisk317.relay.desktop.ui.LiveBadge
import io.github.magisk317.relay.desktop.ui.LoadingCard
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.RelayBadge
import io.github.magisk317.relay.desktop.ui.RelayOption
import io.github.magisk317.relay.desktop.ui.RelaySelect
import io.github.magisk317.relay.desktop.ui.RelayTone
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import io.github.magisk317.relay.desktop.ui.copyToClipboard
import io.github.magisk317.relay.desktop.ui.formatTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The four record classes the webUI splits the log into. */
private enum class RecordTab(val key: String) {
    SMS_CODE("sms_code"),
    SMS_PLAIN("sms_plain"),
    APP_NOTIFY("app_notify"),
    CALL("call"),
}

private enum class RecordFacet { APP, PACKAGE, LINE }

/**
 * Desktop port of the webUI records page: the last 80 records for the
 * selected device, split into four classes with app, package, line and SIM
 * facet filters that can be toggled straight from a record badge.
 */
@Composable
fun RecordsPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<RelayRecord>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(RecordTab.SMS_CODE) }
    var selectedAppLabel by remember { mutableStateOf("") }
    var selectedPackageName by remember { mutableStateOf("") }
    var selectedLineLabel by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var copiedRecordId by remember { mutableStateOf<Long?>(null) }

    suspend fun load() {
        try {
            loading = true
            error = ""
            records = session.dataClient()?.records(limit = 80, deviceId = console.selectedDeviceId)?.records
                ?: emptyList()
        } catch (failure: Exception) {
            error = failure.message ?: DesktopMessages.t(locale, "common.loadFailed")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(console.selectedDeviceId) { load() }
    LaunchedEffect(feed.lastEvent) {
        val eventType = feed.lastEvent?.type
        if (eventType == "records.ingested" || eventType == "device.heartbeat" || eventType == "device.revoked") {
            load()
        }
    }

    LaunchedEffect(copiedRecordId) {
        if (copiedRecordId != null) {
            delay(1600)
            copiedRecordId = null
        }
    }

    val tabs = remember(locale) {
        listOf(
            RecordTab.SMS_CODE to DesktopMessages.t(locale, "records.tab.code"),
            RecordTab.SMS_PLAIN to DesktopMessages.t(locale, "records.tab.plain"),
            RecordTab.APP_NOTIFY to DesktopMessages.t(locale, "records.tab.app"),
            RecordTab.CALL to DesktopMessages.t(locale, "records.tab.call"),
        )
    }
    val tabLabel = remember(tabs, selectedTab) {
        tabs.firstOrNull { it.first == selectedTab }?.second.orEmpty()
    }

    val deviceNameById = remember(console.devices, locale) {
        console.devices.associate { device ->
            device.id to device.displayName.ifBlank { device.deviceName.ifBlank {
                DesktopMessages.t(locale, "common.unknown")
            } }
        }
    }

    val enrichedRecords = remember(records, deviceNameById, locale) {
        records.map { item ->
            val metadata = parseRecordMetadata(item.metadata)
            EnrichedRecord(
                record = item,
                appLabel = metadata.company?.trim().orEmpty(),
                deviceLabel = deviceNameById[item.deviceId]
                    ?: DesktopMessages.t(locale, "records.deviceBadge", mapOf("deviceId" to item.deviceId)),
                lineLabel = resolveLineLabel(item, metadata),
                simLabel = resolveSimLabel(metadata, locale),
            )
        }
    }

    val filteredRecords = enrichedRecords.filter { item ->
        if (console.selectedDeviceId != null && item.record.deviceId != console.selectedDeviceId) return@filter false
        if (selectedAppLabel.isNotEmpty() && item.appLabel != selectedAppLabel) return@filter false
        if (selectedPackageName.isNotEmpty() && item.record.packageName != selectedPackageName) return@filter false
        if (selectedLineLabel.isNotEmpty() && item.lineLabel != selectedLineLabel && item.simLabel != selectedLineLabel) {
            return@filter false
        }
        item.record.recordType == selectedTab.key
    }

    val hasActiveFacetFilters =
        selectedAppLabel.isNotEmpty() || selectedPackageName.isNotEmpty() || selectedLineLabel.isNotEmpty()

    val actions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        RelaySelect(
            value = console.selectedDeviceId?.toString() ?: "",
            options = console.devices.map { device ->
                RelayOption(
                    value = device.id.toString(),
                    label = device.displayName.ifBlank { device.deviceName },
                    description = "${device.platform} / ${device.deviceModel.ifBlank {
                        DesktopMessages.t(locale, "common.unknownModel")
                    }}",
                )
            },
            onValueChange = { value -> value.toLongOrNull()?.let { console.selectDevice(it) } },
        )
        ActionButton(
            text = DesktopMessages.t(locale, "records.refresh"),
            onClick = { scope.launch { load() } },
            tone = ActionTone.NEUTRAL,
        )
    }

    if (loading && records.isEmpty() && error.isBlank()) {
        PageShell(
            title = DesktopMessages.t(locale, "records.title"),
            description = DesktopMessages.t(locale, "records.description"),
            badge = DesktopMessages.t(locale, "records.title"),
            locale = locale,
            actions = actions,
        ) {
            LoadingCard(
                title = DesktopMessages.t(locale, "records.loadingTitle"),
                message = DesktopMessages.t(locale, "records.loadingMessage"),
                locale = locale,
            )
        }
        return
    }

    PageShell(
        title = DesktopMessages.t(locale, "records.title"),
        description = DesktopMessages.t(locale, "records.remoteDescription"),
        badge = DesktopMessages.t(locale, "records.title"),
        locale = locale,
        actions = actions,
    ) {
        ErrorBanner(message = error, locale = locale)

        SurfaceCard(
            title = DesktopMessages.t(locale, "records.classificationTitle"),
            subtitle = DesktopMessages.t(locale, "records.classificationSubtitle"),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tabs.forEach { (tab, label) ->
                    ActionButton(
                        text = label,
                        onClick = { selectedTab = tab },
                        tone = if (tab == selectedTab) ActionTone.PRIMARY else ActionTone.NEUTRAL,
                    )
                }
            }
        }

        if (hasActiveFacetFilters) {
            SurfaceCard(title = DesktopMessages.t(locale, "records.filtersTitle")) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selectedAppLabel.isNotEmpty()) {
                        FacetBadge(
                            text = DesktopMessages.t(locale, "records.appBadge", mapOf("name" to selectedAppLabel)),
                            selected = true,
                            tone = FacetTone.ACCENT,
                            onClick = { selectedAppLabel = "" },
                        )
                    }
                    if (selectedPackageName.isNotEmpty()) {
                        FacetBadge(
                            text = selectedPackageName,
                            selected = true,
                            tone = FacetTone.ACCENT,
                            onClick = { selectedPackageName = "" },
                        )
                    }
                    if (selectedLineLabel.isNotEmpty()) {
                        FacetBadge(
                            text = selectedLineLabel,
                            selected = true,
                            tone = FacetTone.ACCENT,
                            onClick = { selectedLineLabel = "" },
                        )
                    }
                    ActionButton(
                        text = DesktopMessages.t(locale, "records.filtersClear"),
                        onClick = {
                            selectedAppLabel = ""
                            selectedPackageName = ""
                            selectedLineLabel = ""
                        },
                        tone = ActionTone.NEUTRAL,
                    )
                }
            }
        }

        when {
            records.isEmpty() -> EmptyCard(
                title = DesktopMessages.t(locale, "records.empty"),
                message = DesktopMessages.t(locale, "records.emptyMessage"),
            )

            filteredRecords.isEmpty() -> EmptyCard(
                title = DesktopMessages.t(locale, "records.emptyTab", mapOf("label" to tabLabel)),
                message = DesktopMessages.t(locale, "records.emptyTabMessage"),
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                filteredRecords.forEach { item ->
                    RecordCard(
                        item = item,
                        tab = selectedTab,
                        selectedAppLabel = selectedAppLabel,
                        selectedPackageName = selectedPackageName,
                        selectedLineLabel = selectedLineLabel,
                        selectedDeviceId = console.selectedDeviceId,
                        copied = copiedRecordId == item.record.id,
                        locale = locale,
                        onCopyCode = { code ->
                            copyToClipboard(code)
                            copiedRecordId = item.record.id
                        },
                        onFacetChange = { facet, value ->
                            when (facet) {
                                RecordFacet.APP -> selectedAppLabel = if (selectedAppLabel == value) "" else value
                                RecordFacet.PACKAGE ->
                                    selectedPackageName = if (selectedPackageName == value) "" else value
                                RecordFacet.LINE -> selectedLineLabel = if (selectedLineLabel == value) "" else value
                            }
                        },
                        onDeviceSelect = { console.selectDevice(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    item: EnrichedRecord,
    tab: RecordTab,
    selectedAppLabel: String,
    selectedPackageName: String,
    selectedLineLabel: String,
    selectedDeviceId: Long?,
    copied: Boolean,
    locale: DesktopLocale,
    onCopyCode: (String) -> Unit,
    onFacetChange: (RecordFacet, String) -> Unit,
    onDeviceSelect: (Long) -> Unit,
) {
    val record = item.record
    SurfaceCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = record.sender.ifBlank { record.packageName.ifBlank { record.recordType } },
                style = MaterialTheme.typography.titleMedium,
                color = ConsoleInk,
            )
            RelayBadge(
                text = DesktopMessages.t(
                    locale,
                    when (tab) {
                        RecordTab.SMS_CODE -> "records.badge.code"
                        RecordTab.SMS_PLAIN -> "records.badge.plain"
                        RecordTab.APP_NOTIFY -> "records.badge.app"
                        RecordTab.CALL -> "records.badge.call"
                    },
                ),
                tone = when {
                    record.smsCode.isNotBlank() -> RelayTone.ACCENT
                    tab == RecordTab.CALL -> RelayTone.WARNING
                    else -> RelayTone.MUTED
                },
            )
        }
        Text(
            text = formatTimestamp(record.occurredAt),
            style = MaterialTheme.typography.bodySmall,
            color = ConsoleMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = record.body.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = ConsoleInk,
            modifier = Modifier.padding(top = 12.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (item.appLabel.isNotBlank()) {
                FacetBadge(
                    text = DesktopMessages.t(locale, "records.appBadge", mapOf("name" to item.appLabel)),
                    selected = selectedAppLabel == item.appLabel,
                    tone = FacetTone.MUTED,
                    onClick = { onFacetChange(RecordFacet.APP, item.appLabel) },
                )
            }
            if (record.smsCode.isNotBlank()) {
                FacetBadge(
                    text = if (copied) {
                        DesktopMessages.t(locale, "records.codeCopied")
                    } else {
                        DesktopMessages.t(locale, "records.codeBadge", mapOf("code" to record.smsCode))
                    },
                    selected = copied,
                    tone = if (copied) FacetTone.SUCCESS else FacetTone.ACCENT,
                    onClick = { onCopyCode(record.smsCode) },
                )
            }
            if (record.packageName.isNotBlank()) {
                FacetBadge(
                    text = record.packageName,
                    selected = selectedPackageName == record.packageName,
                    tone = FacetTone.MUTED,
                    onClick = { onFacetChange(RecordFacet.PACKAGE, record.packageName) },
                )
            }
            FacetBadge(
                text = item.deviceLabel,
                selected = selectedDeviceId == record.deviceId,
                tone = FacetTone.MUTED,
                onClick = { onDeviceSelect(record.deviceId) },
            )
            if (item.simLabel.isNotBlank()) {
                FacetBadge(
                    text = item.simLabel,
                    selected = selectedLineLabel == item.simLabel,
                    tone = FacetTone.WARNING,
                    onClick = { onFacetChange(RecordFacet.LINE, item.simLabel) },
                )
            }
            if (item.simLabel.isBlank() && item.lineLabel.isNotBlank() && record.recordType != "app_notify") {
                FacetBadge(
                    text = DesktopMessages.t(locale, "records.lineBadge", mapOf("value" to item.lineLabel)),
                    selected = selectedLineLabel == item.lineLabel,
                    tone = FacetTone.WARNING,
                    onClick = { onFacetChange(RecordFacet.LINE, item.lineLabel) },
                )
            }
        }
    }
}
