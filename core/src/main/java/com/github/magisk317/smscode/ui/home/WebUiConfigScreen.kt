package com.github.magisk317.smscode.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.ClipboardUtils
import com.github.magisk317.smscode.common.utils.WebUiCertificateHelper
import io.github.magisk317.xinyi.relay.core.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebUiConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val savedToastText = stringResource(id = R.string.pref_sync_toast)
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

    val exportCertLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-x509-ca-cert"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            WebUiCertificateHelper.exportCertificateDerToUri(context, uri)
                .onSuccess {
                    Toast.makeText(context, certExportSuccessText, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        "$certExportFailedText: ${it.message ?: "unknown"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }
    val exportP12Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pkcs12"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            WebUiCertificateHelper.exportKeystoreP12ToUri(context, uri)
                .onSuccess { password ->
                    exportedP12Password = password
                    Toast.makeText(context, certExportP12SuccessText, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        "$certExportP12FailedText: ${it.message ?: "unknown"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }

    fun copyValue(labelRes: Int, value: String) {
        if (value.isBlank()) return
        ClipboardUtils.copyToClipboard(context, value)
        Toast.makeText(
            context,
            context.getString(R.string.prompt_field_copied, context.getString(labelRes)),
            Toast.LENGTH_SHORT,
        ).show()
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
        WebUiCertificateHelper.loadCertificateInfo(context)
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
                certStatusSummary = "$certLoadFailedText: ${err.message ?: "unknown"}"
            }
    }

    LaunchedEffect(Unit) {
        webUiEnabled = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_WEBUI_ENABLE,
            true,
        )
        lanAccess = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_WEBUI_LAN_ACCESS,
            false,
        )
        port = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_WEBUI_PORT,
            PrefConst.KEY_WEBUI_PORT_DEFAULT,
        )
        username = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_WEBUI_USERNAME,
            PrefConst.KEY_WEBUI_USERNAME_DEFAULT,
        )
        password = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_WEBUI_PASSWORD,
            "",
        )
        refreshCertificateInfo()
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
                                onCheckedChange = { webUiEnabled = it },
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
                                    onCheckedChange = { lanAccess = it },
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
                                        Toast.makeText(
                                            context,
                                            certOpenInstallerFailedText,
                                            Toast.LENGTH_SHORT,
                                        ).show()
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

            Button(
                onClick = {
                    if (!validateInput()) return@Button
                    scope.launch {
                        AppPreferencesDataStore.setBoolean(
                            context,
                            PrefConst.KEY_WEBUI_ENABLE,
                            webUiEnabled,
                        )
                        AppPreferencesDataStore.setBoolean(
                            context,
                            PrefConst.KEY_WEBUI_LAN_ACCESS,
                            lanAccess,
                        )
                        AppPreferencesDataStore.setString(
                            context,
                            PrefConst.KEY_WEBUI_PORT,
                            port.trim(),
                        )
                        AppPreferencesDataStore.setString(
                            context,
                            PrefConst.KEY_WEBUI_USERNAME,
                            username.trim(),
                        )
                        AppPreferencesDataStore.setString(
                            context,
                            PrefConst.KEY_WEBUI_PASSWORD,
                            password.trim(),
                        )
                        AppPreferencesDataStore.syncToSharedPrefs(context)
                        Toast.makeText(context, savedToastText, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(text = stringResource(id = R.string.confirm))
            }
        }
    }
}
