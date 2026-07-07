@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home.relayconfig

import io.github.magisk317.relay.ui.common.StateSwitchItem
import io.github.magisk317.relay.ui.common.Item
import io.github.magisk317.relay.ui.common.TextInputDialog
import io.github.magisk317.relay.ui.common.rememberBlacklistHitDateFormat

import io.github.magisk317.uikit.common.showLatestSnackbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.SmsBlacklistSettingsUpdate
import io.github.magisk317.relay.engine.model.ReadSmsBlacklistHitData
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate
import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate.Feature.*
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterceptScreen(
    refreshTrigger: Int = 0,
    onOpenBlacklistHits: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: SettingsPreferencesRepository = koinInject()
    val recordRepository: MessageRecordRepository = koinInject()
    val blacklistHits by recordRepository.observeSmsBlacklistHits(BLACKLIST_HIT_DISPLAY_LIMIT)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val workMode by WorkModeResolver.mode.collectAsStateWithLifecycle()
    val isXposedFeatureAvailable = { feature: StandardModeFeatureGate.Feature ->
        StandardModeFeatureGate.isAvailable(feature, workMode)
    }
    val xposedDisabledHint = stringResource(R.string.feature_requires_xposed)
    var smsBlacklistEnabled by remember { mutableStateOf(false) }
    var deleteBlockedSms by remember { mutableStateOf(true) }
    var blockIncomingSms by remember { mutableStateOf(false) }
    var smsBlacklistNumbers by remember { mutableStateOf("") }
    var smsBlacklistPrefixes by remember { mutableStateOf("") }
    var smsBlacklistRegex by remember { mutableStateOf("") }
    var smsBlacklistContent by remember { mutableStateOf("") }
    var showSmsBlacklistNumbersDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistPrefixesDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistRegexDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistContentDialog by remember { mutableStateOf(false) }

    suspend fun reload() {
        val snapshot = repository.getSmsBlacklistSettings()
        smsBlacklistEnabled = snapshot.enabled
        deleteBlockedSms = snapshot.deleteBlockedSms
        blockIncomingSms = snapshot.blockIncomingSms
        smsBlacklistNumbers = snapshot.numbers
        smsBlacklistPrefixes = snapshot.prefixes
        smsBlacklistRegex = snapshot.regexRules
        smsBlacklistContent = snapshot.contentRules
    }

    LaunchedEffect(Unit) {
        reload()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            reload()
        }
    }

    LaunchedEffect(smsBlacklistEnabled) {
        if (!smsBlacklistEnabled) {
            showSmsBlacklistNumbersDialog = false
            showSmsBlacklistPrefixesDialog = false
            showSmsBlacklistRegexDialog = false
            showSmsBlacklistContentDialog = false
        }
    }

    val notSetText = context.getString(R.string.blacklist_not_set)
    val savedSnackbarText = context.getString(R.string.pref_sync_snackbar)
    val notifySaved = {
        scope.launch {
            snackbarHostState.showLatestSnackbar(savedSnackbarText)
        }
    }
    val dateFormat = rememberBlacklistHitDateFormat()
    fun saveSettingsIfChanged(
        update: SmsBlacklistSettingsUpdate,
        applyState: () -> Unit = {},
    ) {
        scope.launch {
            repository.updateSmsBlacklistSettings(update)
            applyState()
            notifySaved()
        }
    }
    fun saveStringIfChanged(oldValue: String, newValue: String, updateState: (String) -> Unit, buildUpdate: (String) -> SmsBlacklistSettingsUpdate) {
        if (newValue == oldValue) return
        saveSettingsIfChanged(buildUpdate(newValue)) { updateState(newValue) }
    }
    val formatSummary: (String) -> String = { raw ->
        val count = raw.split('\n', ',', ';').map { it.trim() }.count { it.isNotEmpty() }
        if (count == 0) notSetText else context.getString(R.string.blacklist_rule_count, count)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_filter_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
            )
        },
        snackbarHost = {
            io.github.magisk317.uikit.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp),
            ) {
                StateSwitchItem(
                    title = stringResource(R.string.pref_enable_sms_blacklist_title),
                    summary = stringResource(R.string.pref_enable_sms_blacklist_summary),
                    checked = smsBlacklistEnabled,
                ) { enabled ->
                    smsBlacklistEnabled = enabled
                    saveSettingsIfChanged(SmsBlacklistSettingsUpdate(enabled = enabled))
                }
                if (smsBlacklistEnabled) {
                    val canDelete = isXposedFeatureAvailable(SMS_BLACKLIST_DELETE)
                    StateSwitchItem(
                        title = stringResource(R.string.pref_sms_blacklist_action_delete_title),
                        summary = if (canDelete) {
                            stringResource(R.string.pref_sms_blacklist_action_delete_summary)
                        } else {
                            stringResource(R.string.pref_sms_blacklist_action_delete_summary) + "\n" + xposedDisabledHint
                        },
                        checked = deleteBlockedSms && canDelete,
                        enabled = canDelete,
                    ) { enabled ->
                        deleteBlockedSms = enabled
                        saveSettingsIfChanged(SmsBlacklistSettingsUpdate(deleteBlockedSms = enabled))
                    }
                    val canBlock = isXposedFeatureAvailable(SMS_BLACKLIST_BLOCK)
                    StateSwitchItem(
                        title = stringResource(R.string.pref_sms_blacklist_action_block_title),
                        summary = if (canBlock) {
                            stringResource(R.string.pref_sms_blacklist_action_block_summary)
                        } else {
                            stringResource(R.string.pref_sms_blacklist_action_block_summary) + "\n" + xposedDisabledHint
                        },
                        checked = blockIncomingSms && canBlock,
                        enabled = canBlock,
                    ) { enabled ->
                        blockIncomingSms = enabled
                        saveSettingsIfChanged(SmsBlacklistSettingsUpdate(blockIncomingSms = enabled))
                    }
                    Item(
                        title = stringResource(R.string.pref_sms_blacklist_numbers_title),
                        summary = buildString {
                            append(formatSummary(smsBlacklistNumbers))
                            append('\n')
                            append(stringResource(R.string.pref_sms_blacklist_numbers_summary))
                        },
                    ) { showSmsBlacklistNumbersDialog = true }
                    Item(
                        title = stringResource(R.string.pref_sms_blacklist_prefixes_title),
                        summary = buildString {
                            append(formatSummary(smsBlacklistPrefixes))
                            append('\n')
                            append(stringResource(R.string.pref_sms_blacklist_prefixes_summary))
                        },
                    ) { showSmsBlacklistPrefixesDialog = true }
                    Item(
                        title = stringResource(R.string.pref_sms_blacklist_regex_title),
                        summary = buildString {
                            append(formatSummary(smsBlacklistRegex))
                            append('\n')
                            append(stringResource(R.string.pref_sms_blacklist_regex_hint))
                        },
                    ) { showSmsBlacklistRegexDialog = true }
                    Item(
                        title = stringResource(R.string.pref_sms_blacklist_content_title),
                        summary = buildString {
                            append(formatSummary(smsBlacklistContent))
                            append('\n')
                            append(stringResource(R.string.pref_sms_blacklist_content_summary))
                        },
                    ) { showSmsBlacklistContentDialog = true }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))
                Item(
                    title = stringResource(R.string.sms_blacklist_hit_list_title),
                    summary = blacklistHitSummary(
                        hits = blacklistHits,
                        dateFormat = dateFormat,
                    ),
                ) {
                    onOpenBlacklistHits()
                }
            }
        }
    }

    val separatorHint = stringResource(id = R.string.pref_sms_blacklist_separator_hint)
    val invalidSeparatorError = stringResource(id = R.string.pref_sms_blacklist_invalid_separator_error)
    val separatorValidator: (String) -> String? = { value ->
        if (value.contains("，") || value.contains("；") || value.contains("|")) {
            invalidSeparatorError
        } else {
            null
        }
    }

    val regexSeparatorHint = stringResource(id = R.string.pref_sms_blacklist_regex_separator_hint)
    val invalidRegexSeparatorError = stringResource(id = R.string.pref_sms_blacklist_invalid_regex_separator_error)
    val regexSeparatorValidator: (String) -> String? = { value ->
        if (value.contains("，") || value.contains("；")) {
            invalidRegexSeparatorError
        } else {
            null
        }
    }

    if (smsBlacklistEnabled && showSmsBlacklistNumbersDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_numbers_title),
            initialValue = smsBlacklistNumbers,
            onDismiss = { showSmsBlacklistNumbersDialog = false },
            supportingText = separatorHint,
            validator = separatorValidator,
            onFocusLost = { value ->
                if (separatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistNumbers, value, { smsBlacklistNumbers = it }) {
                        SmsBlacklistSettingsUpdate(numbers = it)
                    }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistNumbers, value, { smsBlacklistNumbers = it }) {
                SmsBlacklistSettingsUpdate(numbers = it)
            }
            showSmsBlacklistNumbersDialog = false
        }
    }

    if (smsBlacklistEnabled && showSmsBlacklistPrefixesDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_prefixes_title),
            initialValue = smsBlacklistPrefixes,
            onDismiss = { showSmsBlacklistPrefixesDialog = false },
            supportingText = separatorHint,
            validator = separatorValidator,
            onFocusLost = { value ->
                if (separatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistPrefixes, value, { smsBlacklistPrefixes = it }) {
                        SmsBlacklistSettingsUpdate(prefixes = it)
                    }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistPrefixes, value, { smsBlacklistPrefixes = it }) {
                SmsBlacklistSettingsUpdate(prefixes = it)
            }
            showSmsBlacklistPrefixesDialog = false
        }
    }

    if (smsBlacklistEnabled && showSmsBlacklistRegexDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_regex_title),
            initialValue = smsBlacklistRegex,
            onDismiss = { showSmsBlacklistRegexDialog = false },
            supportingText = regexSeparatorHint,
            validator = regexSeparatorValidator,
            onFocusLost = { value ->
                if (regexSeparatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistRegex, value, { smsBlacklistRegex = it }) {
                        SmsBlacklistSettingsUpdate(regexRules = it)
                    }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistRegex, value, { smsBlacklistRegex = it }) {
                SmsBlacklistSettingsUpdate(regexRules = it)
            }
            showSmsBlacklistRegexDialog = false
        }
    }

    if (smsBlacklistEnabled && showSmsBlacklistContentDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_content_title),
            initialValue = smsBlacklistContent,
            onDismiss = { showSmsBlacklistContentDialog = false },
            supportingText = separatorHint,
            validator = separatorValidator,
            onFocusLost = { value ->
                if (separatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistContent, value, { smsBlacklistContent = it }) {
                        SmsBlacklistSettingsUpdate(contentRules = it)
                    }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistContent, value, { smsBlacklistContent = it }) {
                SmsBlacklistSettingsUpdate(contentRules = it)
            }
            showSmsBlacklistContentDialog = false
        }
    }
}

@Composable
private fun blacklistHitSummary(
    hits: List<ReadSmsBlacklistHitData>,
    dateFormat: java.text.SimpleDateFormat,
): String {
    if (hits.isEmpty()) {
        return stringResource(R.string.sms_blacklist_hit_recent_empty)
    }
    val latest = hits.first()
    return buildString {
        append(stringResource(R.string.sms_blacklist_hit_count_summary, hits.size))
        append('\n')
        append(
            stringResource(
                R.string.sms_blacklist_hit_latest_summary,
                dateFormat.format(Date(latest.createdAt)),
            ),
        )
    }
}

private const val BLACKLIST_HIT_DISPLAY_LIMIT = 20
