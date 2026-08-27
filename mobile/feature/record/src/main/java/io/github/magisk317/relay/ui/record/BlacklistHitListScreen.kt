@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.record

import io.github.magisk317.relay.ui.common.rememberBlacklistHitDateFormat
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.platform.icon.AppIconEncoder
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.RecordSettingsUpdate
import io.github.magisk317.relay.engine.model.ReadSmsBlacklistHitData
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.ui.common.Item
import io.github.magisk317.relay.ui.common.AppIconCache
import io.github.magisk317.relay.ui.common.RetentionDialog
import io.github.magisk317.uikit.preference.SectionHeader
import io.github.magisk317.relay.ui.common.StateSwitchItem
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.uikit.common.DismissibleSnackbarHost
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistHitListScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val recordRepository: MessageRecordRepository = koinInject()
    val settingsRepository: SettingsPreferencesRepository = koinInject()
    var recordEnabled by remember { mutableStateOf(true) }
    var historyLimit by remember { mutableStateOf(PrefConst.SMS_BLACKLIST_HIT_HISTORY_LIMIT_DEFAULT) }
    val effectiveLimit = remember(historyLimit) {
        historyLimit.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: Int.MAX_VALUE
    }
    val hits by recordRepository.observeSmsBlacklistHits(effectiveLimit)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()
    val dateFormat = rememberBlacklistHitDateFormat()
    val detailDateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var defaultSmsIcon by remember { mutableStateOf<Bitmap?>(null) }
    var fixedTopHeightPx by remember { mutableIntStateOf(0) }
    var detailHit by remember { mutableStateOf<ReadSmsBlacklistHitData?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showHistoryLimitInput by remember { mutableStateOf(false) }
    val defaultTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val fixedTopHeight = if (fixedTopHeightPx > 0) {
        with(density) { fixedTopHeightPx.toDp() }
    } else {
        defaultTopPadding
    }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val savedSnackbarText = remember(context) { context.getString(R.string.pref_sync_snackbar) }

    LaunchedEffect(context, density) {
        val targetIconPx = with(density) { 40.dp.roundToPx() }
        defaultSmsIcon = withContext(Dispatchers.IO) {
            val packageName = AppIconEncoder.resolveDefaultSmsPackage(context)
            packageName?.let {
                AppIconCache.load(
                    context = context.applicationContext,
                    packageName = it,
                    sizePx = targetIconPx,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val settings = settingsRepository.getRecordSettings()
        recordEnabled = settings.smsBlacklistHitRecordEnabled
        historyLimit = settings.smsBlacklistHitHistoryLimit
    }
    val deleteAndUndo: (ReadSmsBlacklistHitData) -> Unit = remember(
        recordRepository,
        scope,
        context,
        snackbarHostState,
    ) {
        { hit ->
            scope.launch {
                recordRepository.removeSmsBlacklistHits(listOf(hit))
                val result = snackbarHostState.showLatestSnackbar(
                    message = context.getString(R.string.some_items_removed, 1),
                    actionLabel = context.getString(R.string.revoke),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    recordRepository.restoreSmsBlacklistHits(listOf(hit))
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.sms_blacklist_hit_clear_dialog_title)) },
            text = { Text(stringResource(R.string.sms_blacklist_hit_clear_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            val deleted = recordRepository.listSmsBlacklistHits(Int.MAX_VALUE)
                            if (deleted.isNotEmpty()) {
                                recordRepository.clearSmsBlacklistHits()
                                val result = snackbarHostState.showLatestSnackbar(
                                    message = context.getString(R.string.some_items_removed, deleted.size),
                                    actionLabel = context.getString(R.string.revoke),
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    recordRepository.restoreSmsBlacklistHits(deleted)
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_clear_records))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionHeader(
                    text = stringResource(
                        R.string.record_settings_title_with_target,
                        stringResource(R.string.sms_blacklist_hit_list_title),
                    ),
                )
                StateSwitchItem(
                    title = stringResource(R.string.pref_enable_sms_blacklist_hit_records_title),
                    summary = "",
                    checked = recordEnabled,
                ) { enabled ->
                    recordEnabled = enabled
                    scope.launch {
                        settingsRepository.updateRecordSettings(
                            RecordSettingsUpdate(smsBlacklistHitRecordEnabled = enabled),
                        )
                        snackbarHostState.showLatestSnackbar(savedSnackbarText)
                    }
                }
                Item(
                    title = stringResource(
                        R.string.pref_history_limit_title_with_target,
                        stringResource(R.string.sms_blacklist_hit_list_title),
                    ),
                    summary = blacklistHitHistoryLimitSummary(historyLimit),
                ) {
                    showHistoryLimitDialog = true
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showHistoryLimitDialog) {
        RetentionDialog(
            selectedValue = historyLimit,
            onDismiss = { showHistoryLimitDialog = false },
            titleId = R.string.pref_history_limit_title,
            entriesId = R.array.history_limit_entry_list,
            valuesId = R.array.history_limit_value_list,
        ) { value ->
            if (value == "-1") {
                showHistoryLimitInput = true
            } else {
                historyLimit = value
                scope.launch {
                    settingsRepository.updateRecordSettings(
                        RecordSettingsUpdate(smsBlacklistHitHistoryLimit = value),
                    )
                    snackbarHostState.showLatestSnackbar(savedSnackbarText)
                }
            }
            showHistoryLimitDialog = false
        }
    }

    if (showHistoryLimitInput) {
        TextInputDialog(
            title = stringResource(R.string.history_limit_custom_entry),
            initialValue = if (historyLimit == "0" || historyLimit == "-1") "" else historyLimit,
            selectAllOnOpen = true,
            onDismiss = { showHistoryLimitInput = false },
        ) { value ->
            if (value.all { it.isDigit() } && value.isNotEmpty()) {
                historyLimit = value
                scope.launch {
                    settingsRepository.updateRecordSettings(
                        RecordSettingsUpdate(smsBlacklistHitHistoryLimit = value),
                    )
                    snackbarHostState.showLatestSnackbar(savedSnackbarText)
                }
            }
            showHistoryLimitInput = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (hits.isEmpty()) {
                WorkspaceEmptyState(
                    title = stringResource(R.string.sms_blacklist_hit_list_title),
                    summary = stringResource(R.string.sms_blacklist_hit_recent_empty),
                    modifier = Modifier.fillMaxSize(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    color = Color.Transparent,
                    shadowElevation = 0.dp,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = fixedTopHeight,
                            bottom = bottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(
                            items = hits,
                            key = { it.id },
                        ) { hit ->
                            BlacklistHitSwipeItem(
                                hit = hit,
                                dateFormat = dateFormat,
                                defaultSmsIcon = defaultSmsIcon,
                                onDelete = deleteAndUndo,
                                onClick = { detailHit = hit },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onSizeChanged { fixedTopHeightPx = it.height }
                .background(chromeSurfaceColor()),
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.sms_blacklist_hit_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = hits.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_clear_records_content_description),
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.pref_code_records_title),
                        )
                    }
                },
                colors = chromeTopAppBarColors(),
                windowInsets = WindowInsets.statusBars,
            )
        }

        DismissibleSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        detailHit?.let { hit ->
            BlacklistHitDetailDialog(
                hit = hit,
                dateFormat = detailDateFormat,
                onDismiss = { detailHit = null },
                onDelete = {
                    deleteAndUndo(hit)
                    detailHit = null
                },
            )
        }
    }
}

@Composable
private fun BlacklistHitSwipeItem(
    hit: ReadSmsBlacklistHitData,
    dateFormat: SimpleDateFormat,
    defaultSmsIcon: android.graphics.Bitmap?,
    onDelete: (ReadSmsBlacklistHitData) -> Unit,
    onClick: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        onDelete(hit)
        dismissState.reset()
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val fromStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        SmsBlacklistHitListItem(
            hit = hit,
            onClick = onClick,
            dateFormat = dateFormat,
            defaultSmsIcon = defaultSmsIcon,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BlacklistHitDetailDialog(
    hit: ReadSmsBlacklistHitData,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val notSet = stringResource(R.string.blacklist_not_set)
    val sender = hit.sender.orEmpty().ifBlank { notSet }
    val body = hit.body.orEmpty().ifBlank { notSet }
    val smsTime = formatBlacklistHitTime(dateFormat, hit.smsDate)
    val createdTime = formatBlacklistHitTime(dateFormat, hit.createdAt)
    val source = blacklistHitSourceText(hit.source)
    val match = stringResource(
        R.string.sms_blacklist_hit_match,
        blacklistHitMatchTypeText(hit.matchType),
        hit.pattern.orEmpty().ifBlank { notSet },
    )
    val actions = blacklistHitActionText(hit.actionDelete, hit.actionBlock)
    val blockReason = hit.blockReason
        ?.let { blacklistHitBlockReasonText(it) }
        .orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sms_blacklist_hit_detail_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BlacklistHitDetailField(stringResource(R.string.detail_sender), sender)
                BlacklistHitDetailField(stringResource(R.string.detail_content), body)
                BlacklistHitDetailField(stringResource(R.string.detail_original_time), smsTime)
                BlacklistHitDetailField(stringResource(R.string.sms_blacklist_hit_created_at), createdTime)
                BlacklistHitDetailField(stringResource(R.string.sms_blacklist_hit_source), source)
                BlacklistHitDetailField(stringResource(R.string.sms_blacklist_hit_match_label), match)
                BlacklistHitDetailField(stringResource(R.string.sms_blacklist_hit_actions_label), actions)
                if (blockReason.isNotBlank()) {
                    BlacklistHitDetailField(stringResource(R.string.sms_blacklist_hit_block_reason_label), blockReason)
                }
                BlacklistHitDetailField(stringResource(R.string.sms_blacklist_hit_event_id), hit.eventId)
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text(stringResource(R.string.action_close))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        },
    )
}

@Composable
private fun BlacklistHitDetailField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun blacklistHitHistoryLimitSummary(value: String): String {
    val entries = stringArrayResource(R.array.history_limit_entry_list)
    val values = stringArrayResource(R.array.history_limit_value_list)
    val index = values.indexOf(value)
    return if (index >= 0) {
        entries[index]
    } else {
        stringResource(R.string.pref_history_limit_summary, value)
    }
}

private fun formatBlacklistHitTime(
    dateFormat: SimpleDateFormat,
    value: Long,
): String {
    return if (value > 0L) dateFormat.format(Date(value)) else "-"
}
