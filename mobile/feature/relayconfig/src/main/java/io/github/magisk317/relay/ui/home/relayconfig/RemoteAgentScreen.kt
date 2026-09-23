package io.github.magisk317.relay.ui.home.relayconfig

import io.github.magisk317.uikit.common.showLatestSnackbar

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.relay.contract.repository.ConfigSyncCoordinator
import io.github.magisk317.relay.contract.settings.RemoteAgentSnapshot
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@Composable
fun RemoteAgentScreen(onBack: () -> Unit) {
    val repository: ConfigSyncCoordinator = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var snapshot by remember { mutableStateOf<RemoteAgentSnapshot?>(null) }
    var baseUrl by remember { mutableStateOf("") }
    var bindCode by remember { mutableStateOf("") }
    val bindActionText = stringResource(id = R.string.pref_remote_agent_bind_action)
    val saveText = stringResource(id = R.string.save)
    val heartbeatActionText = stringResource(id = R.string.pref_remote_agent_heartbeat_action)
    val pullActionText = stringResource(id = R.string.pref_remote_agent_pull_action)
    val pushActionText = stringResource(id = R.string.pref_remote_agent_push_action)
    val recordsActionText = stringResource(id = R.string.pref_remote_agent_records_action)
    val unbindActionText = stringResource(id = R.string.pref_remote_agent_unbind_action)
    val scanActionText = stringResource(id = R.string.pref_remote_agent_scan_action)
    val backendSavedText = stringResource(id = R.string.pref_remote_agent_saved)
    val heartbeatDoneText = stringResource(id = R.string.pref_remote_agent_heartbeat_done)
    val pushDoneText = stringResource(id = R.string.pref_remote_agent_push_done)
    val recordsDoneText = stringResource(id = R.string.pref_remote_agent_records_done)
    val unboundText = stringResource(id = R.string.pref_remote_agent_unbound)
    val stateUnknownText = stringResource(id = R.string.pref_remote_agent_state_unknown)
    val notBoundText = stringResource(id = R.string.pref_remote_agent_not_bound)
    val noneText = stringResource(id = R.string.pref_remote_agent_none)
    val notYetText = stringResource(id = R.string.pref_remote_agent_not_yet)
    val scanUpdatedText = stringResource(id = R.string.pref_remote_agent_scan_done)

    suspend fun refresh() {
        val next = repository.getSnapshot()
        snapshot = next
        baseUrl = next.backendBaseUrl
    }



    LaunchedEffect(Unit) {
        refresh()
    }

    val remoteAgentBody: @Composable (PaddingValues) -> Unit = { padding ->
    val current = snapshot
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(id = R.string.pref_remote_agent_base_url_title)) },
            supportingText = { Text(stringResource(id = R.string.pref_remote_agent_base_url_summary)) },
            singleLine = true,
        )

        OutlinedTextField(
            value = bindCode,
            onValueChange = { bindCode = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(id = R.string.pref_remote_agent_bind_code_title)) },
            supportingText = { Text(stringResource(id = R.string.pref_remote_agent_bind_code_summary)) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            repository.updateBackendBaseUrl(baseUrl)
                            repository.bindDevice(bindCode)
                        }.onSuccess {
                            refresh()
                            snackbarHostState.showLatestSnackbar(
                                context.getString(R.string.pref_remote_agent_bind_done, it.deviceId),
                            )
                        }.onFailure {
                            refresh()
                            snackbarHostState.showLatestSnackbar(it.message ?: it.javaClass.simpleName)
                        }
                    }
                },
            ) {
                Text(bindActionText)
            }
            Button(
                onClick = {
                    val options = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    val scanner = GmsBarcodeScanning.getClient(context, options)
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val contents = barcode.rawValue?.trim().orEmpty()
                            if (contents.isBlank()) return@addOnSuccessListener
                            val parsed = parseBindPayload(contents)
                            val resolvedCode = parsed.code.ifBlank { contents }
                            val resolvedBaseUrl = parsed.baseUrl.ifBlank { baseUrl }.trim()
                            bindCode = resolvedCode
                            if (resolvedBaseUrl.isNotBlank()) {
                                baseUrl = resolvedBaseUrl
                            }
                            scope.launch {
                                snackbarHostState.showLatestSnackbar(scanUpdatedText)
                                if (resolvedBaseUrl.isBlank() || resolvedCode.isBlank()) return@launch
                                runCatching {
                                    repository.updateBackendBaseUrl(resolvedBaseUrl)
                                    repository.bindDevice(resolvedCode)
                                }.onSuccess {
                                    refresh()
                                    bindCode = ""
                                    snackbarHostState.showLatestSnackbar(
                                        context.getString(R.string.pref_remote_agent_bind_done, it.deviceId),
                                    )
                                }.onFailure {
                                    refresh()
                                    snackbarHostState.showLatestSnackbar(it.message ?: it.javaClass.simpleName)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            scope.launch {
                                snackbarHostState.showLatestSnackbar(e.message ?: "Scan failed")
                            }
                        }
                },
            ) {
                Text(scanActionText)
            }
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            repository.updateBackendBaseUrl(baseUrl)
                        }.onSuccess {
                            refresh()
                            snackbarHostState.showLatestSnackbar(backendSavedText)
                        }.onFailure {
                            snackbarHostState.showLatestSnackbar(it.message ?: it.javaClass.simpleName)
                        }
                    }
                },
            ) {
                Text(saveText)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        runCatching { repository.sendHeartbeat() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(heartbeatDoneText)
                            }
                            .onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(it.message ?: it.javaClass.simpleName)
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                Text(heartbeatActionText)
            }
            Button(
                onClick = {
                    scope.launch {
                        runCatching { repository.pullPendingCommands() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(
                                    context.getString(R.string.pref_remote_agent_pull_done, it.revision.value),
                                )
                            }
                            .onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(it.message ?: it.javaClass.simpleName)
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                Text(pullActionText)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        runCatching { repository.pushLocalMirror() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(pushDoneText)
                            }
                            .onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(it.message ?: it.javaClass.simpleName)
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                Text(pushActionText)
            }
            Button(
                onClick = {
                    scope.launch {
                        runCatching { repository.uploadRecentRecords() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(recordsDoneText)
                            }
                            .onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(it.message ?: it.javaClass.simpleName)
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                Text(recordsActionText)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        repository.clearBinding()
                        bindCode = ""
                        refresh()
                        snackbarHostState.showLatestSnackbar(unboundText)
                    }
                },
                enabled = current?.bound == true,
            ) {
                Text(unbindActionText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_status_title),
            value = current?.syncState ?: stateUnknownText,
        )
        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_bound_device_title),
            value = if (current?.bound == true) {
                "user=${current.userId} device=${current.deviceId}"
            } else {
                notBoundText
            },
        )
        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_revision_title),
            value = current?.localConfigRevision?.toString()
                ?: stateUnknownText,
        )
        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_last_error_title),
            value = current?.lastError?.ifBlank {
                noneText
            } ?: noneText,
        )
        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_last_heartbeat_title),
            value = formatEpochMillis(current?.lastHeartbeatAt ?: 0L, notYetText),
        )
        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_last_pull_title),
            value = formatEpochMillis(current?.lastPullAt ?: 0L, notYetText),
        )
        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_last_push_title),
            value = formatEpochMillis(current?.lastPushAt ?: 0L, notYetText),
        )
        StatusCard(
            title = stringResource(id = R.string.pref_remote_agent_pending_mutations_title),
            value = (current?.pendingLocalChanges ?: 0).toString(),
        )
    }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> RemoteAgentScreenMiuix(
            title = stringResource(R.string.pref_remote_agent_title),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = remoteAgentBody,
        )

        UiKitStyle.Expressive -> RemoteAgentScreenMaterial(
            title = stringResource(R.string.pref_remote_agent_title),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = remoteAgentBody,
        )
    }
}

private fun formatEpochMillis(value: Long, emptyLabel: String): String {
    if (value <= 0L) return emptyLabel
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(value))
}

private data class ParsedBindPayload(
    val code: String,
    val baseUrl: String,
)

private fun parseBindPayload(raw: String): ParsedBindPayload {
    return runCatching {
        val uri = Uri.parse(raw.trim())
        if (uri.scheme == "xinyi-relay" && uri.host == "bind") {
            ParsedBindPayload(
                code = uri.getQueryParameter("code").orEmpty(),
                baseUrl = uri.getQueryParameter("base_url").orEmpty(),
            )
        } else {
            ParsedBindPayload(code = raw.trim(), baseUrl = "")
        }
    }.getOrDefault(ParsedBindPayload(code = raw.trim(), baseUrl = ""))
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
