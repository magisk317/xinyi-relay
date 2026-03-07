package com.github.magisk317.smscode.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import io.github.magisk317.xinyi.relay.core.R
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterceptScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    refreshTrigger: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var smsBlacklistNumbers by remember { mutableStateOf("") }
    var smsBlacklistPrefixes by remember { mutableStateOf("") }
    var smsBlacklistRegex by remember { mutableStateOf("") }
    var smsBlacklistContent by remember { mutableStateOf("") }
    var showSmsBlacklistNumbersDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistPrefixesDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistRegexDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistContentDialog by remember { mutableStateOf(false) }

    suspend fun reload() {
        smsBlacklistNumbers = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, "")
        smsBlacklistPrefixes = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, "")
        smsBlacklistRegex = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, "")
        smsBlacklistContent = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, "")
    }

    LaunchedEffect(Unit) {
        reload()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            reload()
        }
    }

    val notSetText = context.getString(R.string.blacklist_not_set)
    val savedToastText = context.getString(R.string.pref_sync_toast)
    val notifySaved = {
        Toast.makeText(context, savedToastText, Toast.LENGTH_SHORT).show()
    }
    fun saveStringIfChanged(oldValue: String, key: String, newValue: String, updateState: (String) -> Unit) {
        if (newValue == oldValue) return
        updateState(newValue)
        scope.launch {
            AppPreferencesDataStore.setString(context, key, newValue)
            AppPreferencesDataStore.syncToSharedPrefs(context)
            notifySaved()
        }
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
                title = { Text(stringResource(R.string.tab_intercept)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier.hazeEffect(hazeState, hazeStyle) { forceInvalidateOnPreDraw = true },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
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
                    .hazeSource(hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp),
            ) {
                SectionHeader(
                    text = stringResource(R.string.pref_sms_blacklist_title),
                    modifier = Modifier.padding(top = Const.SPACING_SMALL.dp),
                )
                SwitchItem(
                    title = stringResource(R.string.pref_enable_sms_blacklist_title),
                    summary = stringResource(R.string.pref_enable_sms_blacklist_summary),
                    key = PrefConst.KEY_ENABLE_SMS_BLACKLIST,
                    defaultValue = false,
                    onSaved = notifySaved,
                )
                SwitchItem(
                    title = stringResource(R.string.pref_sms_blacklist_action_delete_title),
                    summary = stringResource(R.string.pref_sms_blacklist_action_delete_summary),
                    key = PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
                    defaultValue = true,
                    onSaved = notifySaved,
                )
                SwitchItem(
                    title = stringResource(R.string.pref_sms_blacklist_action_block_title),
                    summary = stringResource(R.string.pref_sms_blacklist_action_block_summary),
                    key = PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
                    defaultValue = false,
                    onSaved = notifySaved,
                )
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

                HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))
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

    if (showSmsBlacklistNumbersDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_numbers_title),
            initialValue = smsBlacklistNumbers,
            onDismiss = { showSmsBlacklistNumbersDialog = false },
            supportingText = separatorHint,
            validator = separatorValidator,
            onFocusLost = { value ->
                if (separatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistNumbers, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, value) { smsBlacklistNumbers = it }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistNumbers, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, value) { smsBlacklistNumbers = it }
            showSmsBlacklistNumbersDialog = false
        }
    }

    if (showSmsBlacklistPrefixesDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_prefixes_title),
            initialValue = smsBlacklistPrefixes,
            onDismiss = { showSmsBlacklistPrefixesDialog = false },
            supportingText = separatorHint,
            validator = separatorValidator,
            onFocusLost = { value ->
                if (separatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistPrefixes, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, value) { smsBlacklistPrefixes = it }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistPrefixes, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, value) { smsBlacklistPrefixes = it }
            showSmsBlacklistPrefixesDialog = false
        }
    }

    if (showSmsBlacklistRegexDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_regex_title),
            initialValue = smsBlacklistRegex,
            onDismiss = { showSmsBlacklistRegexDialog = false },
            supportingText = regexSeparatorHint,
            validator = regexSeparatorValidator,
            onFocusLost = { value ->
                if (regexSeparatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistRegex, PrefConst.KEY_SMS_BLACKLIST_REGEX, value) { smsBlacklistRegex = it }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistRegex, PrefConst.KEY_SMS_BLACKLIST_REGEX, value) { smsBlacklistRegex = it }
            showSmsBlacklistRegexDialog = false
        }
    }

    if (showSmsBlacklistContentDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_content_title),
            initialValue = smsBlacklistContent,
            onDismiss = { showSmsBlacklistContentDialog = false },
            supportingText = separatorHint,
            validator = separatorValidator,
            onFocusLost = { value ->
                if (separatorValidator(value) == null) {
                    saveStringIfChanged(smsBlacklistContent, PrefConst.KEY_SMS_BLACKLIST_CONTENT, value) { smsBlacklistContent = it }
                }
            },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            saveStringIfChanged(smsBlacklistContent, PrefConst.KEY_SMS_BLACKLIST_CONTENT, value) { smsBlacklistContent = it }
            showSmsBlacklistContentDialog = false
        }
    }
}
