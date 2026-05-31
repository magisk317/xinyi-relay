package io.github.magisk317.relay.ui.backup

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.relay.backup.BackupSource
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    initialSource: BackupSource? = null,
    backupNow: Boolean = false,
    viewModel: CloudBackupViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val backupListMessage by viewModel.backupListMessage.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val webDavConfig by viewModel.webDavConfig.collectAsStateWithLifecycle()
    val googleDriveConfig by viewModel.googleDriveConfig.collectAsStateWithLifecycle()
    val hasGoogleDriveBackup = viewModel.hasGoogleDriveBackup()
    val backupSuccessMessage = stringResource(id = R.string.cloud_backup_backup_success)
    val restoreSuccessMessage = stringResource(id = R.string.cloud_backup_restore_success)
    val webDavConnectionSuccessMessage = stringResource(id = R.string.cloud_backup_webdav_connection_success)
    val loadingBackupsMessage = stringResource(id = R.string.cloud_backup_loading_backups)
    val backupsTitle = stringResource(id = R.string.cloud_backup_list_title)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    val googleDriveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleDriveAuthorizationResult(result.resultCode == Activity.RESULT_OK)
    }

    var showWebDavConfig by remember { mutableStateOf(false) }
    var showGoogleDriveConfig by remember { mutableStateOf(false) }
    var googleDrivePath by remember { mutableStateOf(GoogleDriveBackupConfig.DEFAULT_FOLDER_PATH) }
    var webDavServer by remember { mutableStateOf(WebDavConfig.DEFAULT_SERVER_URL) }
    var webDavUsername by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var webDavPasswordVisible by remember { mutableStateOf(false) }
    var webDavPath by remember { mutableStateOf(WebDavConfig.DEFAULT_REMOTE_PATH) }
    var hasAttemptedWebDavSubmit by remember { mutableStateOf(false) }

    val isWebDavServerMissing = hasAttemptedWebDavSubmit && webDavServer.isBlank()
    val isWebDavUsernameMissing = hasAttemptedWebDavSubmit && webDavUsername.isBlank()
    val isWebDavPasswordMissing = hasAttemptedWebDavSubmit && webDavPassword.isBlank()

    fun requestGoogleDriveLogin(action: CloudBackupViewModel.AfterLoginAction? = null) {
        action?.let(viewModel::setPendingAfterLoginAction)
        googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
    }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showLatestSnackbar(message) }
    }

    fun requestBackupNow() {
        if (selectedSource == BackupSource.GOOGLE_DRIVE && hasGoogleDriveBackup && !viewModel.canUseCloudBackup()) {
            requestGoogleDriveLogin(CloudBackupViewModel.AfterLoginAction.BackupNow)
        } else {
            viewModel.backupNow()
        }
    }

    fun saveWebDavConfig(testConnection: Boolean): Boolean {
        hasAttemptedWebDavSubmit = true
        val savedConfig = viewModel.updateWebDavConfig(
            WebDavConfig(
                serverUrl = webDavServer,
                username = webDavUsername,
                password = webDavPassword,
                remotePath = webDavPath,
            )
        ) ?: return false
        webDavServer = savedConfig.serverUrl
        webDavUsername = savedConfig.username
        webDavPassword = savedConfig.password
        webDavPath = savedConfig.remotePath
        if (testConnection) {
            viewModel.testWebDavConnection()
        }
        return true
    }

    fun saveGoogleDriveConfig(): Boolean {
        val savedConfig = viewModel.updateGoogleDriveConfig(
            GoogleDriveBackupConfig(folderPath = googleDrivePath)
        )
        googleDrivePath = savedConfig.folderPath
        return true
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CloudBackupViewModel.CloudBackupEvent.BackupSuccess -> {
                    showMessage(backupSuccessMessage)
                }
                is CloudBackupViewModel.CloudBackupEvent.RestoreSuccess -> {
                    showMessage(restoreSuccessMessage)
                }
                is CloudBackupViewModel.CloudBackupEvent.WebDavConnectionSuccess -> {
                    showMessage(webDavConnectionSuccessMessage)
                }
                is CloudBackupViewModel.CloudBackupEvent.LoginSuccess -> {
                    // Handled automatically by view model loading backups
                }
                is CloudBackupViewModel.CloudBackupEvent.GoogleDriveAuthorizationRequired -> {
                    googleDriveAuthorizationLauncher.launch(event.intent)
                }
                is CloudBackupViewModel.CloudBackupEvent.Error -> {
                    showMessage(event.message)
                }
            }
        }
    }

    LaunchedEffect(webDavConfig) {
        webDavConfig?.let { config ->
            webDavServer = config.serverUrl
            webDavUsername = config.username
            webDavPassword = config.password
            webDavPath = config.remotePath
        } ?: run {
            webDavServer = WebDavConfig.DEFAULT_SERVER_URL
            webDavUsername = ""
            webDavPassword = ""
            webDavPath = WebDavConfig.DEFAULT_REMOTE_PATH
        }
    }

    LaunchedEffect(googleDriveConfig) {
        googleDrivePath = googleDriveConfig.folderPath
    }

    LaunchedEffect(initialSource) {
        if (initialSource != null && initialSource != selectedSource) {
            viewModel.switchBackupSource(initialSource)
        }
    }

    LaunchedEffect(selectedSource, webDavConfig, hasGoogleDriveBackup) {
        viewModel.loadBackups()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.cloud_backup_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // Backup source selection
            Text(
                text = stringResource(id = R.string.cloud_backup_source_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isGoogleDriveUsable = selectedSource == BackupSource.GOOGLE_DRIVE && viewModel.canUseCloudBackup()
                if (hasGoogleDriveBackup) {
                    if (selectedSource == BackupSource.GOOGLE_DRIVE) {
                        Button(
                            onClick = {
                                if (isGoogleDriveUsable) {
                                    viewModel.loadBackups()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.backup_source_google))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.switchBackupSource(BackupSource.GOOGLE_DRIVE) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.backup_source_google))
                        }
                    }
                }

                if (selectedSource == BackupSource.WEBDAV) {
                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(id = R.string.backup_source_webdav))
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.switchBackupSource(BackupSource.WEBDAV) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(id = R.string.backup_source_webdav))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedSource == BackupSource.GOOGLE_DRIVE && hasGoogleDriveBackup) {
                if (showGoogleDriveConfig) {
                    Text(
                        text = stringResource(id = R.string.cloud_backup_google_drive_config_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = googleDrivePath,
                        onValueChange = { googleDrivePath = it },
                        label = { Text(stringResource(id = R.string.cloud_backup_google_drive_folder_path)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (saveGoogleDriveConfig()) {
                                    showGoogleDriveConfig = false
                                    viewModel.loadBackups()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.cloud_backup_save))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.cloud_backup_google_drive_location, googleDriveConfig.folderPath),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(id = R.string.cloud_backup_google_drive_visible_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { showGoogleDriveConfig = true }) {
                            Text(stringResource(id = R.string.cloud_backup_edit_config))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // WebDAV configuration
            if (selectedSource == BackupSource.WEBDAV) {
                if (showWebDavConfig || webDavConfig == null) {
                    Text(
                        text = stringResource(id = R.string.cloud_backup_webdav_config_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavServer,
                        onValueChange = { webDavServer = it },
                        label = { Text(stringResource(id = R.string.cloud_backup_webdav_server_url)) },
                        isError = isWebDavServerMissing,
                        supportingText = {
                            if (isWebDavServerMissing) {
                                Text(stringResource(id = R.string.cloud_backup_field_required))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavUsername,
                        onValueChange = { webDavUsername = it },
                        label = { Text(stringResource(id = R.string.cloud_backup_webdav_username)) },
                        isError = isWebDavUsernameMissing,
                        supportingText = {
                            if (isWebDavUsernameMissing) {
                                Text(stringResource(id = R.string.cloud_backup_field_required))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavPassword,
                        onValueChange = { webDavPassword = it },
                        label = { Text(stringResource(id = R.string.cloud_backup_webdav_password)) },
                        visualTransformation = if (webDavPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { webDavPasswordVisible = !webDavPasswordVisible }) {
                                Icon(
                                    imageVector = if (webDavPasswordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = stringResource(
                                        id = if (webDavPasswordVisible) {
                                            R.string.cloud_backup_password_hide
                                        } else {
                                            R.string.cloud_backup_password_show
                                        },
                                    ),
                                )
                            }
                        },
                        isError = isWebDavPasswordMissing,
                        supportingText = {
                            if (isWebDavPasswordMissing) {
                                Text(stringResource(id = R.string.cloud_backup_field_required))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavPath,
                        onValueChange = { webDavPath = it },
                        label = { Text(stringResource(id = R.string.cloud_backup_webdav_remote_path)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (saveWebDavConfig(testConnection = false)) {
                                    showWebDavConfig = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.cloud_backup_save))
                        }
                        Button(
                            onClick = {
                                saveWebDavConfig(testConnection = true)
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            Text(stringResource(id = R.string.cloud_backup_test_connection))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showWebDavConfig = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.cloud_backup_edit_config))
                        }
                        Button(
                            onClick = { viewModel.removeWebDavConfig() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.cloud_backup_remove_config))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Controls
            val canUseSelectedBackup = viewModel.canUseCloudBackup()
            if ((selectedSource == BackupSource.GOOGLE_DRIVE && hasGoogleDriveBackup) || canUseSelectedBackup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = ::requestBackupNow,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(stringResource(id = R.string.cloud_backup_manual_backup))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (canUseSelectedBackup) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackup(it) },
                    )
                    Column {
                        Text(stringResource(id = R.string.cloud_backup_auto_enable))
                        Text(
                            text = stringResource(id = R.string.cloud_backup_auto_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = backupsTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading && backups.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text(loadingBackupsMessage)
                }
            } else if (backups.isEmpty()) {
                Text(
                    text = backupListMessage ?: stringResource(id = R.string.cloud_backup_no_backups),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                backupListMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(backups) { backup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backup.source.displayName(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(backup.name)
                                Text(
                                    text = stringResource(id = R.string.cloud_backup_item_size, backup.size.readableSize()),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row {
                                Button(onClick = { viewModel.restoreBackup(backup.id) }) {
                                    Text(stringResource(id = R.string.cloud_backup_restore))
                                }
                                Button(onClick = { viewModel.deleteBackup(backup.id) }) {
                                    Text(stringResource(id = R.string.cloud_backup_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupSource.displayName(): String = when (this) {
    BackupSource.GOOGLE_DRIVE -> stringResource(id = R.string.backup_source_google)
    BackupSource.WEBDAV -> stringResource(id = R.string.backup_source_webdav_display)
}

private fun Long.readableSize(): String {
    val value = this.coerceAtLeast(0L).toDouble()
    val units = listOf("B", "KB", "MB", "GB")
    var scaled = value
    var index = 0
    while (scaled >= 1024.0 && index < units.lastIndex) {
        scaled /= 1024.0
        index += 1
    }
    return if (index == 0) {
        "${scaled.toLong()} ${units[index]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", scaled, units[index])
    }
}
