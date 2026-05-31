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
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsUpdate
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput
import io.github.magisk317.relay.ui.common.normalizeIntegerInput
import io.github.magisk317.relay.ui.common.parseIntInRangeInput
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardKeepAliveScreen(onBack: () -> Unit) {
    val repository: SettingsPreferencesRepository = koinInject()
    val scope = rememberCoroutineScope()
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showLatestSnackbar(savedSnackbarText)
        }
    }
    var settings by remember { mutableStateOf<DiagnosticsSettingsSnapshot?>(null) }
    var showRootDbIntervalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = repository.getDiagnosticsSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_group_background_keepalive)) },
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
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
            SectionCard(
                title = stringResource(id = R.string.settings_group_background_keepalive),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_root_db_catchup_enable_title),
                    summary = stringResource(id = R.string.pref_root_db_catchup_enable_summary),
                    checked = current.rootDbCatchupEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(rootDbCatchupEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
                    summary = stringResource(
                        id = R.string.pref_root_db_catchup_interval_summary,
                        current.rootDbCatchupIntervalMin,
                    ),
                ) { showRootDbIntervalDialog = true }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_root_db_catchup_writeback_title),
                    summary = stringResource(id = R.string.pref_root_db_catchup_writeback_summary),
                    checked = current.rootDbCatchupWriteback,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(rootDbCatchupWriteback = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_force_stop_recovery_title),
                    summary = stringResource(id = R.string.pref_force_stop_recovery_summary),
                    checked = current.forceStopRecoveryEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(forceStopRecoveryEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_title),
                    summary = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_summary),
                    checked = current.forceStopRecoveryRelaunchOnceEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(forceStopRecoveryRelaunchOnceEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
            }

            SectionCard(
                title = stringResource(id = R.string.settings_group_keepalive_xposed_hooks),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_keepalive_oom_adj_title),
                    summary = stringResource(id = R.string.pref_keepalive_oom_adj_summary),
                    checked = current.keepAliveOomAdj,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(keepAliveOomAdj = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_keepalive_anti_kill_title),
                    summary = stringResource(id = R.string.pref_keepalive_anti_kill_summary),
                    checked = current.keepAliveAntiKill,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(keepAliveAntiKill = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_keepalive_standby_bypass_title),
                    summary = stringResource(id = R.string.pref_keepalive_standby_bypass_summary),
                    checked = current.keepAliveStandbyBypass,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(keepAliveStandbyBypass = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_keepalive_doze_bypass_title),
                    summary = stringResource(id = R.string.pref_keepalive_doze_bypass_summary),
                    checked = current.keepAliveDozeBypass,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(keepAliveDozeBypass = enabled),
                        )
                        notifySaved()
                    }
                }
            }

            SectionCard(
                title = stringResource(id = R.string.settings_group_keepalive_app_layer),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_keepalive_accessibility_heartbeat_title),
                    summary = stringResource(id = R.string.pref_keepalive_accessibility_heartbeat_summary),
                    checked = current.keepAliveAccessibilityHeartbeat,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(keepAliveAccessibilityHeartbeat = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_keepalive_dedicated_service_title),
                    summary = stringResource(id = R.string.pref_keepalive_dedicated_service_summary),
                    checked = current.keepAliveDedicatedService,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(keepAliveDedicatedService = enabled),
                        )
                        notifySaved()
                    }
                }
            }
        }
    }

    val current = settings
    if (showRootDbIntervalDialog && current != null) {
        val rootDbIntervalError = stringResource(id = R.string.pref_root_db_catchup_interval_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
            initialValue = normalizeIntegerInput(current.rootDbCatchupIntervalMin),
            onDismiss = { showRootDbIntervalDialog = false },
            supportingText = stringResource(id = R.string.pref_root_db_catchup_interval_hint),
            validator = {
                if (parseIntInRangeInput(it, 1..120) != null) {
                    null
                } else {
                    rootDbIntervalError
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            inputFilter = ::filterNonNegativeIntegerInput,
        ) { updated ->
            showRootDbIntervalDialog = false
            scope.launch {
                settings = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(rootDbCatchupIntervalMin = normalizeIntegerInput(updated)),
                )
                notifySaved()
            }
        }
    }
}
