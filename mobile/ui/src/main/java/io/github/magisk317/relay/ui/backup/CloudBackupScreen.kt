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
import io.github.magisk317.relay.backup.CloudBackupMeta
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
            BackupSourceSelector(
                selectedSource = selectedSource,
                hasGoogleDriveBackup = hasGoogleDriveBackup,
                canUseGoogleDrive = selectedSource == BackupSource.GOOGLE_DRIVE && viewModel.canUseCloudBackup(),
                onLoadBackups = viewModel::loadBackups,
                onSwitchSource = viewModel::switchBackupSource,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedSource == BackupSource.GOOGLE_DRIVE && hasGoogleDriveBackup) {
                GoogleDriveConfigSection(
                    showConfig = showGoogleDriveConfig,
                    folderPath = googleDrivePath,
                    savedFolderPath = googleDriveConfig.folderPath,
                    onFolderPathChange = { googleDrivePath = it },
                    onEdit = { showGoogleDriveConfig = true },
                    onSave = {
                        if (saveGoogleDriveConfig()) {
                            showGoogleDriveConfig = false
                            viewModel.loadBackups()
                        }
                    },
                )
            }

            if (selectedSource == BackupSource.WEBDAV) {
                WebDavConfigSection(
                    showConfig = showWebDavConfig || webDavConfig == null,
                    server = webDavServer,
                    username = webDavUsername,
                    password = webDavPassword,
                    passwordVisible = webDavPasswordVisible,
                    remotePath = webDavPath,
                    isServerMissing = isWebDavServerMissing,
                    isUsernameMissing = isWebDavUsernameMissing,
                    isPasswordMissing = isWebDavPasswordMissing,
                    isLoading = isLoading,
                    onServerChange = { webDavServer = it },
                    onUsernameChange = { webDavUsername = it },
                    onPasswordChange = { webDavPassword = it },
                    onTogglePasswordVisibility = { webDavPasswordVisible = !webDavPasswordVisible },
                    onRemotePathChange = { webDavPath = it },
                    onEdit = { showWebDavConfig = true },
                    onRemove = viewModel::removeWebDavConfig,
                    onSave = {
                        if (saveWebDavConfig(testConnection = false)) {
                            showWebDavConfig = false
                        }
                    },
                    onTestConnection = { saveWebDavConfig(testConnection = true) },
                )
            }

            val canUseSelectedBackup = viewModel.canUseCloudBackup()
            CloudBackupControls(
                selectedSource = selectedSource,
                hasGoogleDriveBackup = hasGoogleDriveBackup,
                canUseSelectedBackup = canUseSelectedBackup,
                isLoading = isLoading,
                autoBackupEnabled = autoBackupEnabled,
                onBackupNow = ::requestBackupNow,
                onAutoBackupChange = viewModel::setAutoBackup,
            )

            BackupListSection(
                title = backupsTitle,
                loadingMessage = loadingBackupsMessage,
                isLoading = isLoading,
                backups = backups,
                message = backupListMessage,
                onRestore = viewModel::restoreBackup,
                onDelete = viewModel::deleteBackup,
            )
        }
    }
}

@Composable
private fun BackupSourceSelector(
    selectedSource: BackupSource,
    hasGoogleDriveBackup: Boolean,
    canUseGoogleDrive: Boolean,
    onLoadBackups: () -> Unit,
    onSwitchSource: (BackupSource) -> Unit,
) {
    Text(
        text = stringResource(id = R.string.cloud_backup_source_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasGoogleDriveBackup) {
            BackupSourceButton(
                selected = selectedSource == BackupSource.GOOGLE_DRIVE,
                text = stringResource(id = R.string.backup_source_google),
                onClick = {
                    if (selectedSource == BackupSource.GOOGLE_DRIVE) {
                        if (canUseGoogleDrive) onLoadBackups()
                    } else {
                        onSwitchSource(BackupSource.GOOGLE_DRIVE)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        BackupSourceButton(
            selected = selectedSource == BackupSource.WEBDAV,
            text = stringResource(id = R.string.backup_source_webdav),
            onClick = {
                if (selectedSource != BackupSource.WEBDAV) {
                    onSwitchSource(BackupSource.WEBDAV)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BackupSourceButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

@Composable
private fun GoogleDriveConfigSection(
    showConfig: Boolean,
    folderPath: String,
    savedFolderPath: String,
    onFolderPathChange: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
) {
    if (showConfig) {
        Text(
            text = stringResource(id = R.string.cloud_backup_google_drive_config_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = folderPath,
            onValueChange = onFolderPathChange,
            label = { Text(stringResource(id = R.string.cloud_backup_google_drive_folder_path)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(id = R.string.cloud_backup_save))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.cloud_backup_google_drive_location, savedFolderPath),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(id = R.string.cloud_backup_google_drive_visible_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onEdit) {
                Text(stringResource(id = R.string.cloud_backup_edit_config))
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun WebDavConfigSection(
    showConfig: Boolean,
    server: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    remotePath: String,
    isServerMissing: Boolean,
    isUsernameMissing: Boolean,
    isPasswordMissing: Boolean,
    isLoading: Boolean,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onRemotePathChange: (String) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
) {
    if (showConfig) {
        WebDavConfigForm(
            server = server,
            username = username,
            password = password,
            passwordVisible = passwordVisible,
            remotePath = remotePath,
            isServerMissing = isServerMissing,
            isUsernameMissing = isUsernameMissing,
            isPasswordMissing = isPasswordMissing,
            isLoading = isLoading,
            onServerChange = onServerChange,
            onUsernameChange = onUsernameChange,
            onPasswordChange = onPasswordChange,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
            onRemotePathChange = onRemotePathChange,
            onSave = onSave,
            onTestConnection = onTestConnection,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Text(stringResource(id = R.string.cloud_backup_edit_config))
            }
            Button(onClick = onRemove, modifier = Modifier.weight(1f)) {
                Text(stringResource(id = R.string.cloud_backup_remove_config))
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun WebDavConfigForm(
    server: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    remotePath: String,
    isServerMissing: Boolean,
    isUsernameMissing: Boolean,
    isPasswordMissing: Boolean,
    isLoading: Boolean,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onRemotePathChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
) {
    Text(
        text = stringResource(id = R.string.cloud_backup_webdav_config_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    RequiredTextField(
        value = server,
        onValueChange = onServerChange,
        label = stringResource(id = R.string.cloud_backup_webdav_server_url),
        isError = isServerMissing,
    )
    RequiredTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = stringResource(id = R.string.cloud_backup_webdav_username),
        isError = isUsernameMissing,
    )
    PasswordTextField(
        value = password,
        onValueChange = onPasswordChange,
        visible = passwordVisible,
        isError = isPasswordMissing,
        onToggleVisibility = onTogglePasswordVisibility,
    )
    OutlinedTextField(
        value = remotePath,
        onValueChange = onRemotePathChange,
        label = { Text(stringResource(id = R.string.cloud_backup_webdav_remote_path)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
            Text(stringResource(id = R.string.cloud_backup_save))
        }
        Button(
            onClick = onTestConnection,
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(stringResource(id = R.string.cloud_backup_test_connection))
        }
    }
}

@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = {
            if (isError) {
                Text(stringResource(id = R.string.cloud_backup_field_required))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    isError: Boolean,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(id = R.string.cloud_backup_webdav_password)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        id = if (visible) {
                            R.string.cloud_backup_password_hide
                        } else {
                            R.string.cloud_backup_password_show
                        },
                    ),
                )
            }
        },
        isError = isError,
        supportingText = {
            if (isError) {
                Text(stringResource(id = R.string.cloud_backup_field_required))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun CloudBackupControls(
    selectedSource: BackupSource,
    hasGoogleDriveBackup: Boolean,
    canUseSelectedBackup: Boolean,
    isLoading: Boolean,
    autoBackupEnabled: Boolean,
    onBackupNow: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
) {
    if ((selectedSource == BackupSource.GOOGLE_DRIVE && hasGoogleDriveBackup) || canUseSelectedBackup) {
        Button(
            onClick = onBackupNow,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(stringResource(id = R.string.cloud_backup_manual_backup))
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
                onCheckedChange = onAutoBackupChange,
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
}

@Composable
private fun BackupListSection(
    title: String,
    loadingMessage: String,
    isLoading: Boolean,
    backups: List<CloudBackupMeta>,
    message: String?,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))

    when {
        isLoading && backups.isEmpty() -> LoadingBackupList(loadingMessage)
        backups.isEmpty() -> Text(
            text = message ?: stringResource(id = R.string.cloud_backup_no_backups),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> BackupList(backups, message, onRestore, onDelete)
    }
}

@Composable
private fun LoadingBackupList(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator()
        Text(message)
    }
}

@Composable
private fun BackupList(
    backups: List<CloudBackupMeta>,
    message: String?,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(backups) { backup ->
            BackupListItem(
                backup = backup,
                onRestore = { onRestore(backup.id) },
                onDelete = { onDelete(backup.id) },
            )
        }
    }
}

@Composable
private fun BackupListItem(
    backup: CloudBackupMeta,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
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
            Button(onClick = onRestore) {
                Text(stringResource(id = R.string.cloud_backup_restore))
            }
            Button(onClick = onDelete) {
                Text(stringResource(id = R.string.cloud_backup_delete))
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
    while (scaled >= BYTES_PER_KIB && index < units.lastIndex) {
        scaled /= BYTES_PER_KIB
        index += 1
    }
    return if (index == 0) {
        "${scaled.toLong()} ${units[index]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", scaled, units[index])
    }
}

private const val BYTES_PER_KIB = 1024.0
