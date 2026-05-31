package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.RelaySettingsSnapshot
import io.github.magisk317.relay.contract.settings.RelaySettingsUpdate
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput
import io.github.magisk317.relay.ui.common.parseIntInRangeInput
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayConfigScreen(
    onBack: () -> Unit,
    onOpenSenders: () -> Unit,
    onOpenAppRouting: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val repository: SettingsPreferencesRepository = koinInject()
    val scope = rememberCoroutineScope()
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showLatestSnackbar(savedSnackbarText)
        }
    }
    var relay by remember { mutableStateOf<RelaySettingsSnapshot?>(null) }
    var showDedupWindowDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        relay = repository.getRelaySettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.pref_relay_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = {
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        val current = relay ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
            StateSwitchItem(
                title = stringResource(id = R.string.pref_relay_features_title),
                summary = stringResource(id = R.string.pref_relay_features_summary),
                checked = current.relayFeaturesEnabled,
                modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
            ) { enabled ->
                scope.launch {
                    relay = repository.updateRelaySettings(RelaySettingsUpdate(relayFeaturesEnabled = enabled))
                    notifySaved()
                }
            }
            if (current.relayFeaturesEnabled) {
                SectionCard(
                    title = stringResource(id = R.string.pref_relay_config_title),
                    accordionMode = false,
                    sectionExpanded = true,
                    onExpandedChange = {},
                ) {
                    Item(
                        title = stringResource(id = R.string.tab_senders),
                        summary = stringResource(id = R.string.pref_enable_forward_summary),
                    ) { onOpenSenders() }
                    Item(
                        title = stringResource(id = R.string.title_notification_rules),
                        summary = stringResource(id = R.string.subtitle_notification_rules),
                    ) { onOpenAppRouting() }
                    Item(
                        title = stringResource(id = R.string.advanced_filter_title),
                        summary = stringResource(id = R.string.advanced_filter_summary),
                    ) { onOpenFilters() }
                    Item(
                        title = stringResource(id = R.string.pref_relay_records_title),
                        summary = stringResource(id = R.string.pref_relay_records_summary),
                    ) { onOpenRecords() }
                    Item(
                        title = stringResource(id = R.string.pref_sms_forward_dedup_window_title),
                        summary = stringResource(
                            id = R.string.pref_sms_forward_dedup_window_summary,
                            current.smsForwardDedupWindowSec,
                        ),
                    ) { showDedupWindowDialog = true }
                }
            }
        }
    }

    val current = relay
    if (showDedupWindowDialog && current != null) {
        val rangeError = stringResource(
            id = R.string.pref_sms_forward_dedup_window_error,
            PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MIN,
            PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MAX,
        )
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_forward_dedup_window_title),
            initialValue = current.smsForwardDedupWindowSec.toString(),
            onDismiss = { showDedupWindowDialog = false },
            supportingText = stringResource(id = R.string.pref_sms_forward_dedup_window_hint),
            validator = {
                if (parseIntInRangeInput(
                    it,
                    PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MIN..PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MAX,
                ) != null) {
                    null
                } else {
                    rangeError
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            inputFilter = ::filterNonNegativeIntegerInput,
        ) { updated ->
            showDedupWindowDialog = false
            scope.launch {
                relay = repository.updateRelaySettings(
                    RelaySettingsUpdate(
                        smsForwardDedupWindowSec = parseIntInRangeInput(
                            updated,
                            PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MIN..PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MAX,
                        ) ?: PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT,
                    ),
                )
                notifySaved()
            }
        }
    }
}
