@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils
import io.github.magisk317.relay.common.utils.Utils
import io.github.magisk317.relay.platform.web.WebUiCertificateHelper
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebUiConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: SettingsRepository = koinInject()
    val preferenceDataSource: PreferenceDataSource = koinInject()
    val snackbarHostState = remember { SnackbarHostState() }

    var webUiEnabled by remember { mutableStateOf(true) }
    var lanAccess by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf(PrefConst.KEY_WEBUI_PORT_DEFAULT) }
    var username by remember { mutableStateOf(PrefConst.KEY_WEBUI_USERNAME_DEFAULT) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var certFingerprint by remember { mutableStateOf("") }
    var certPeriodSummary by remember { mutableStateOf("") }
    var certStatusSummary by remember { mutableStateOf("") }
    var exportedP12Password by remember { mutableStateOf("") }
    var lanIpv4Hosts by remember { mutableStateOf(emptyList<String>()) }
    var initialLoading by remember { mutableStateOf(true) }
    var certLoading by remember { mutableStateOf(false) }
    var saveInProgress by remember { mutableStateOf(false) }

    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val portInvalidText = stringResource(id = R.string.pref_webui_port_invalid)
    val usernameInvalidText = stringResource(id = R.string.pref_webui_username_invalid)
    val passwordInvalidText = stringResource(id = R.string.pref_webui_password_invalid)
    val certLoadFailedText = stringResource(id = R.string.pref_webui_cert_load_failed)
    val certExportSuccessText = stringResource(id = R.string.pref_webui_cert_export_success)
    val certExportFailedText = stringResource(id = R.string.pref_webui_cert_export_failed)
    val certExportP12SuccessText = stringResource(id = R.string.pref_webui_cert_export_p12_success)
    val certExportP12FailedText = stringResource(id = R.string.pref_webui_cert_export_p12_failed)
    val certOpenInstallerFailedText = stringResource(id = R.string.pref_webui_cert_open_installer_failed)
    val certTimeLabelText = stringResource(id = R.string.pref_webui_cert_validity_label)
    val unknownText = stringResource(id = R.string.unknown)
    val browserInstallOrEnablePrompt = stringResource(id = R.string.browser_install_or_enable_prompt)
    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    val notifySaved = { showMessage(savedSnackbarText) }

    val exportCertLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-x509-ca-cert"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                WebUiCertificateHelper.exportCertificateDerToUri(context, preferenceDataSource, uri)
            }
                .onSuccess {
                    showMessage(certExportSuccessText)
                }
                .onFailure {
                    showMessage("$certExportFailedText: ${it.message ?: unknownText}")
                }
        }
    }
    val exportP12Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pkcs12"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                WebUiCertificateHelper.exportKeystoreP12ToUri(context, preferenceDataSource, uri)
            }
                .onSuccess { password ->
                    exportedP12Password = password
                    showMessage(certExportP12SuccessText)
                }
                .onFailure {
                    showMessage("$certExportP12FailedText: ${it.message ?: unknownText}")
                }
        }
    }

    fun copyValue(labelRes: Int, value: String) {
        if (value.isBlank()) return
        ClipboardUtils.copyToClipboard(context, value)
        showMessage(context.getString(R.string.prompt_field_copied, context.getString(labelRes)))
    }

    fun copyValue(label: String, value: String) {
        if (value.isBlank()) return
        ClipboardUtils.copyToClipboard(context, value)
        showMessage(context.getString(R.string.prompt_field_copied, label))
    }

    fun openWebUiUrl(url: String) {
        if (url.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                throw IllegalStateException(browserInstallOrEnablePrompt)
            }
        }.onFailure {
            Utils.showWebPage(context, url)?.let(::showMessage)
                ?: showMessage(it.message ?: browserInstallOrEnablePrompt)
        }
    }

    fun validateInput(): Boolean {
        if (!webUiEnabled) {
            portError = null
            usernameError = null
            passwordError = null
            return true
        }
        val parsedPort = port.trim().toIntOrNull()
        portError = if (parsedPort == null || parsedPort !in 1..65535) portInvalidText else null

        val trimmedUsername = username.trim()
        usernameError = if (trimmedUsername.isBlank()) usernameInvalidText else null

        val trimmedPassword = password.trim()
        passwordError = if (trimmedPassword.isBlank()) passwordInvalidText else null

        return portError == null && usernameError == null && passwordError == null
    }

    suspend fun refreshCertificateInfo() {
        certLoading = true
        withContext(Dispatchers.IO) {
            WebUiCertificateHelper.loadCertificateInfo(context, preferenceDataSource)
        }
            .onSuccess { info ->
                certFingerprint = info.sha256Fingerprint
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val start = formatter.format(Date(info.notBeforeTimeMillis))
                val end = formatter.format(Date(info.notAfterTimeMillis))
                certPeriodSummary = "$certTimeLabelText: $start ~ $end"
                certStatusSummary = ""
            }
            .onFailure { err ->
                certFingerprint = ""
                certPeriodSummary = ""
                exportedP12Password = ""
                certStatusSummary = "$certLoadFailedText: ${err.message ?: unknownText}"
            }
        certLoading = false
    }

    LaunchedEffect(Unit) {
        initialLoading = true
        val snapshotDeferred = async(Dispatchers.IO) { repository.getWebUiConfig() }
        val certDeferred = async(Dispatchers.IO) {
            WebUiCertificateHelper.loadCertificateInfo(context, preferenceDataSource)
        }

        val snapshot = snapshotDeferred.await()
        webUiEnabled = snapshot.enabled
        lanAccess = snapshot.lanAccess
        port = snapshot.port
        username = snapshot.username
        password = snapshot.password

        certLoading = true
        certDeferred.await()
            .onSuccess { info ->
                certFingerprint = info.sha256Fingerprint
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val start = formatter.format(Date(info.notBeforeTimeMillis))
                val end = formatter.format(Date(info.notAfterTimeMillis))
                certPeriodSummary = "$certTimeLabelText: $start ~ $end"
                certStatusSummary = ""
            }
            .onFailure { err ->
                certFingerprint = ""
                certPeriodSummary = ""
                exportedP12Password = ""
                certStatusSummary = "$certLoadFailedText: ${err.message ?: unknownText}"
            }
        certLoading = false
        initialLoading = false
    }

    LaunchedEffect(lanAccess) {
        lanIpv4Hosts = if (lanAccess) {
            withContext(Dispatchers.IO) { resolveLanIpv4Hosts() }
        } else {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.pref_webui_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel),
                        )
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.pref_webui_config_summary_short),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (initialLoading) {
                        Text(
                            text = stringResource(id = R.string.pref_webui_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(text = stringResource(id = R.string.pref_webui_enable_title)) },
                        supportingContent = {
                            Text(
                                text = stringResource(id = R.string.pref_webui_enable_summary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = webUiEnabled,
                                onCheckedChange = { enabled ->
                                    webUiEnabled = enabled
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            repository.updateWebUiConfig(
                                                io.github.magisk317.relay.data.repository.WebUiConfigUpdate(
                                                    enabled = enabled,
                                                ),
                                            )
                                        }
                                        notifySaved()
                                    }
                                },
                            )
                        },
                    )

                    if (webUiEnabled) {
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(text = stringResource(id = R.string.pref_webui_lan_access_title)) },
                            supportingContent = {
                                Text(
                                    text = stringResource(id = R.string.pref_webui_lan_access_summary_short),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = lanAccess,
                                    onCheckedChange = { enabled ->
                                        lanAccess = enabled
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                repository.updateWebUiConfig(
                                                    io.github.magisk317.relay.data.repository.WebUiConfigUpdate(
                                                        lanAccess = enabled,
                                                    ),
                                                )
                                            }
                                            notifySaved()
                                        }
                                    },
                                )
                            },
                        )

                        OutlinedTextField(
                            value = port,
                            onValueChange = {
                                port = it
                                portError = null
                            },
                            label = { Text(text = stringResource(id = R.string.pref_webui_port_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = portError != null,
                            supportingText = {
                                Text(text = portError ?: stringResource(id = R.string.pref_webui_port_hint))
                            },
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                usernameError = null
                            },
                            label = { Text(text = stringResource(id = R.string.pref_webui_username_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = usernameError != null,
                            supportingText = {
                                if (usernameError != null) {
                                    Text(text = usernameError ?: "")
                                }
                            },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    copyValue(R.string.pref_webui_username_title, username.trim())
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(id = R.string.action_copy),
                                    )
                                }
                            },
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                            },
                            label = { Text(text = stringResource(id = R.string.pref_webui_password_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = passwordError != null,
                            supportingText = {
                                if (passwordError != null) {
                                    Text(text = passwordError ?: "")
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                Row(
                                    modifier = Modifier.padding(end = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = stringResource(id = R.string.pref_webui_toggle_password_visibility),
                                        )
                                    }
                                    IconButton(onClick = {
                                    copyValue(R.string.pref_webui_password_title, password.trim())
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(id = R.string.action_copy),
                                        )
                                    }
                                }
                            },
                        )

                        Button(
                            onClick = {
                                if (!validateInput()) return@Button
                                scope.launch {
                                    saveInProgress = true
                                    withContext(Dispatchers.IO) {
                                        repository.updateWebUiConfig(
                                            io.github.magisk317.relay.data.repository.WebUiConfigUpdate(
                                                enabled = webUiEnabled,
                                                lanAccess = lanAccess,
                                                port = port.trim(),
                                                username = username.trim(),
                                                password = password.trim(),
                                            ),
                                        )
                                    }
                                    saveInProgress = false
                                    showMessage(savedSnackbarText)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            enabled = !saveInProgress,
                        ) {
                            Text(text = stringResource(id = R.string.confirm))
                        }

                        val normalizedPort = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                        val localUrl = normalizedPort?.let { "https://127.0.0.1:$it" }.orEmpty()
                        val lanUrls = normalizedPort
                            ?.takeIf { lanAccess }
                            ?.let { resolvedPort -> lanIpv4Hosts.map { host -> "https://$host:$resolvedPort" } }
                            .orEmpty()

                        Text(
                            text = stringResource(id = R.string.pref_webui_access_section_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(id = R.string.pref_webui_access_section_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (normalizedPort == null) {
                            Text(
                                text = stringResource(id = R.string.pref_webui_access_invalid_port),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            val localUrlLabel = stringResource(id = R.string.pref_webui_access_local_url_title)
                            WebUiAccessUrlField(
                                label = localUrlLabel,
                                url = localUrl,
                                onCopy = { copyValue(localUrlLabel, localUrl) },
                                onOpen = { openWebUiUrl(localUrl) },
                            )
                            if (lanAccess) {
                                if (lanUrls.isEmpty()) {
                                    Text(
                                        text = stringResource(id = R.string.pref_webui_access_lan_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    lanUrls.forEachIndexed { index, url ->
                                        val label = if (lanUrls.size == 1) {
                                            stringResource(id = R.string.pref_webui_access_lan_url_title)
                                        } else {
                                            "${stringResource(id = R.string.pref_webui_access_lan_url_title)} ${index + 1}"
                                        }
                                        WebUiAccessUrlField(
                                            label = label,
                                            url = url,
                                            onCopy = { copyValue(label, url) },
                                            onOpen = { openWebUiUrl(url) },
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(id = R.string.pref_webui_https_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = stringResource(id = R.string.pref_webui_cert_section_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(id = R.string.pref_webui_cert_section_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = certFingerprint.ifBlank {
                                stringResource(id = R.string.pref_webui_cert_fingerprint_empty)
                            },
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(id = R.string.pref_webui_cert_fingerprint_title)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        copyValue(
                                            R.string.pref_webui_cert_fingerprint_title,
                                            certFingerprint,
                                        )
                                    },
                                    enabled = certFingerprint.isNotBlank(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(id = R.string.action_copy),
                                    )
                                }
                            },
                        )
                        if (certPeriodSummary.isNotBlank()) {
                            Text(
                                text = certPeriodSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (certStatusSummary.isNotBlank()) {
                            Text(
                                text = certStatusSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (certLoading) {
                            Text(
                                text = stringResource(id = R.string.pref_webui_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { scope.launch { refreshCertificateInfo() } },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(id = R.string.pref_webui_cert_refresh))
                            }
                            Button(
                                onClick = {
                                    exportCertLauncher.launch("xinyi-relay-webui-cert.cer")
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(id = R.string.pref_webui_cert_export))
                            }
                        }
                        Text(
                            text = stringResource(id = R.string.pref_webui_cert_private_key_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    } else {
                                        showMessage(certOpenInstallerFailedText)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(id = R.string.pref_webui_cert_open_installer))
                            }
                            Button(
                                onClick = {
                                    exportP12Launcher.launch("xinyi-relay-webui-cert-with-key.p12")
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(id = R.string.pref_webui_cert_export_p12))
                            }
                        }
                        if (exportedP12Password.isNotBlank()) {
                            OutlinedTextField(
                                value = exportedP12Password,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(text = stringResource(id = R.string.pref_webui_cert_export_p12_password_title))
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            copyValue(
                                                R.string.pref_webui_cert_export_p12_password_title,
                                                exportedP12Password,
                                            )
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(id = R.string.action_copy),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebUiAccessUrlField(
    label: String,
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(id = R.string.action_copy),
                    )
                }
            },
        )
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(id = R.string.pref_webui_open_in_browser))
        }
    }
}

private fun resolveLanIpv4Hosts(): List<String> {
    val hosts = mutableListOf<String>()
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
    while (interfaces != null && interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement() ?: continue
        if (!networkInterface.isUp || networkInterface.isLoopback) continue
        val addresses = networkInterface.inetAddresses
        while (addresses.hasMoreElements()) {
            val raw = addresses.nextElement().hostAddress.orEmpty()
            val normalized = raw.substringBefore('%').trim()
            if (normalized.isBlank() || normalized.startsWith("127.") || normalized == "::1") continue
            if (!normalized.contains(".")) continue
            if (!hosts.contains(normalized)) hosts.add(normalized)
        }
    }
    return hosts
}
