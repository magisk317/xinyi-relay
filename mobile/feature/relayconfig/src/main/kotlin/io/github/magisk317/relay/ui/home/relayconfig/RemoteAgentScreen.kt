package io.github.magisk317.relay.ui.home.relayconfig

import io.github.magisk317.uikit.common.showLatestSnackbar
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.relay.contract.repository.ConfigSyncCoordinator
import io.github.magisk317.relay.contract.settings.RemoteAgentSnapshot
import io.github.magisk317.relay.common.utils.RelayBindLink
import io.github.magisk317.relay.common.utils.RelayBindPayload
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

@Composable
fun RemoteAgentScreen(
    onBack: () -> Unit,
    bindRequest: RelayBindPayload? = null,
) {
    val repository: ConfigSyncCoordinator = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { AppSnackbarHostState() }
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
    val scanFailedText = stringResource(id = R.string.pref_remote_agent_scan_failed)
    val bindFailedText = stringResource(id = R.string.pref_remote_agent_bind_failed)
    val saveFailedText = stringResource(id = R.string.pref_remote_agent_save_failed)
    val heartbeatFailedText = stringResource(id = R.string.pref_remote_agent_heartbeat_failed)
    val pullFailedText = stringResource(id = R.string.pref_remote_agent_pull_failed)
    val pushFailedText = stringResource(id = R.string.pref_remote_agent_push_failed)
    val recordsFailedText = stringResource(id = R.string.pref_remote_agent_records_failed)
    val systemScanHintText = stringResource(id = R.string.pref_remote_agent_system_scan_hint)
    val linkFilledText = stringResource(id = R.string.pref_remote_agent_link_filled)
    val installScannerText = stringResource(id = R.string.pref_remote_agent_install_scanner)

    suspend fun refresh() {
        val next = repository.getSnapshot()
        snapshot = next
        baseUrl = next.backendBaseUrl
    }

    suspend fun bind(code: String, url: String) {
        runCatching {
            repository.updateBackendBaseUrl(url)
            repository.bindDevice(code)
        }.onSuccess {
            refresh()
            bindCode = ""
            snackbarHostState.showLatestSnackbar(
                context.getString(R.string.pref_remote_agent_bind_done, it.deviceId),
            )
        }.onFailure {
            refresh()
            snackbarHostState.showLatestSnackbar(errorMessage(context, it, bindFailedText))
        }
    }

    val packageManager = context.packageManager
    val systemScannerIntent = remember { scannerIntent(MIUI_SCAN_ACTION, MIUI_SCANNER_PACKAGE) }
    val scannerInstallIntent = remember { scannerIntent(GMS_SCANNER_UI_ACTION, GMS_PACKAGE) }
    val hasSystemScanner = remember { systemScannerIntent.resolveActivity(packageManager) != null }
    val canInstallScanner = remember { scannerInstallIntent.resolveActivity(packageManager) != null }

    fun startScan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val contents = barcode.rawValue?.trim().orEmpty()
                if (contents.isBlank()) return@addOnSuccessListener
                val parsed = RelayBindLink.parse(contents)
                val resolvedBaseUrl = parsed.baseUrl.ifBlank { baseUrl }.trim()
                bindCode = parsed.bindCode
                if (resolvedBaseUrl.isNotBlank()) {
                    baseUrl = resolvedBaseUrl
                }
                scope.launch {
                    snackbarHostState.showLatestSnackbar(scanUpdatedText)
                    if (resolvedBaseUrl.isNotBlank()) {
                        bind(parsed.bindCode, resolvedBaseUrl)
                    }
                }
            }
            .addOnFailureListener { error ->
                if (error is ApiException && error.statusCode == CommonStatusCodes.CANCELED) {
                    return@addOnFailureListener
                }
                scope.launch {
                    val result = snackbarHostState.showLatestSnackbar(
                        message = scanFailedText,
                        actionLabel = if (canInstallScanner) installScannerText else null,
                    )
                    if (result == AppSnackbarResult.ActionPerformed) {
                        context.tryStartScanner(scannerInstallIntent)
                    }
                }
            }
    }

    fun startScanning() {
        val systemScannerLaunched = hasSystemScanner && context.tryStartScanner(systemScannerIntent)
        if (systemScannerLaunched) {
            scope.launch { snackbarHostState.showLatestSnackbar(systemScanHintText) }
            return
        }
        startScan()
    }

    LaunchedEffect(Unit) {
        refresh()
        bindRequest?.let { request ->
            if (request.baseUrl.isNotBlank()) {
                baseUrl = request.baseUrl
            }
            bindCode = request.bindCode
            snackbarHostState.showLatestSnackbar(linkFilledText)
        }
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
        AppTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(id = R.string.pref_remote_agent_base_url_title),
            supportingText = { AppText(stringResource(id = R.string.pref_remote_agent_base_url_summary)) },
            singleLine = true,
        )

        AppTextField(
            value = bindCode,
            onValueChange = { bindCode = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(id = R.string.pref_remote_agent_bind_code_title),
            supportingText = { AppText(stringResource(id = R.string.pref_remote_agent_bind_code_summary)) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppPrimaryButton(
                onClick = {
                    scope.launch {
                        bind(bindCode, baseUrl)
                    }
                },
            ) {
                AppText(bindActionText)
            }
            AppPrimaryButton(
                onClick = { startScanning() },
            ) {
                AppText(scanActionText)
            }
            AppPrimaryButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            repository.updateBackendBaseUrl(baseUrl)
                        }.onSuccess {
                            refresh()
                            snackbarHostState.showLatestSnackbar(backendSavedText)
                        }.onFailure {
                            snackbarHostState.showLatestSnackbar(errorMessage(context, it, saveFailedText))
                        }
                    }
                },
            ) {
                AppText(saveText)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppPrimaryButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.sendHeartbeat() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(heartbeatDoneText)
                            }
                            .onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(errorMessage(context, it, heartbeatFailedText))
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                AppText(heartbeatActionText)
            }
            AppPrimaryButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.pullPendingCommands() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(
                                    context.getString(R.string.pref_remote_agent_pull_done, it.revision.value),
                                )
                            }.onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(errorMessage(context, it, pullFailedText))
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                AppText(pullActionText)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppPrimaryButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.pushLocalMirror() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(pushDoneText)
                            }
                            .onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(errorMessage(context, it, pushFailedText))
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                AppText(pushActionText)
            }
            AppPrimaryButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.uploadRecentRecords() }
                            .onSuccess {
                                refresh()
                                snackbarHostState.showLatestSnackbar(recordsDoneText)
                            }
                            .onFailure {
                                refresh()
                                snackbarHostState.showLatestSnackbar(errorMessage(context, it, recordsFailedText))
                            }
                    }
                },
                enabled = current?.bound == true,
            ) {
                AppText(recordsActionText)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppPrimaryButton(
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
                AppText(unbindActionText)
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

private fun errorMessage(context: Context, error: Throwable, fallback: String): String = when {
    error is IOException -> context.getString(R.string.pref_remote_agent_error_network)
    error is IllegalArgumentException && error.message?.contains("base url", ignoreCase = true) == true ->
        context.getString(R.string.pref_remote_agent_error_invalid_base_url)
    else -> fallback
}

private fun formatEpochMillis(value: Long, emptyLabel: String): String {
    if (value <= 0L) return emptyLabel
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(value))
}

private fun Context.tryStartScanner(intent: Intent): Boolean =
    runCatching { startActivity(intent) }.isSuccess

private const val MIUI_SCANNER_PACKAGE = "com.xiaomi.scanner"
private const val MIUI_SCAN_ACTION = "miui.intent.action.scanbarcode"
private const val GMS_PACKAGE = "com.google.android.gms"
private const val GMS_SCANNER_UI_ACTION = "com.google.android.gms.mlkit_barcode_ui.SCAN_QR_CODE"

// LocalContext here can be a createConfigurationContext wrapper rather than the Activity, so the
// external scanner UI must be started in its own task.
private fun scannerIntent(action: String, targetPackage: String): Intent = Intent(action)
    .addCategory(Intent.CATEGORY_DEFAULT)
    .setPackage(targetPackage)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun StatusCard(title: String, value: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.SurfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppText(text = title, role = AppTextRole.Subtitle)
            AppText(text = value, role = AppTextRole.Body)
        }
    }
}
