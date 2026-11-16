package io.github.magisk317.relay.ui.backup

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.backup.BackupEntry
import io.github.magisk317.uikit.backup.BackupProviderKind
import io.github.magisk317.uikit.backup.BackupUiActions
import io.github.magisk317.uikit.backup.BackupUiState
import io.github.magisk317.uikit.backup.BackupWebDavConfig
import io.github.magisk317.uikit.backup.CloudBackupPanel
import io.github.magisk317.uikit.backup.R as BackupUiKitR
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.xposed.backup.BackupSource
import io.github.magisk317.xposed.backup.CloudBackupMeta
import io.github.magisk317.xposed.backup.webdav.WebDavConfig
import kotlinx.coroutines.launch

/**
 * Cloud backup page: hosts the shared [CloudBackupPanel] from the ui-kit and
 * keeps the relay-specific pieces (Google Drive folder configuration, sign-in
 * authorization flow and snackbar event routing) around it. All backup
 * business logic stays in [CloudBackupViewModel]; this screen only projects
 * its state onto the kit contract.
 */
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    initialSource: BackupSource? = null,
    backupNow: Boolean = false,
    viewModel: CloudBackupViewModel = viewModel(),
) {
    val activity = checkNotNull(LocalActivity.current) { "CloudBackupScreen requires an Activity context" }
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

    val googleDriveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.handleGoogleDriveAuthorizationResult(result.data)
    }

    var showGoogleDriveConfig by remember { mutableStateOf(false) }
    var googleDrivePath by remember { mutableStateOf(GoogleDriveBackupConfig.DEFAULT_FOLDER_PATH) }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showLatestSnackbar(message) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CloudBackupViewModel.CloudBackupEvent.BackupSuccess -> showMessage(backupSuccessMessage)
                is CloudBackupViewModel.CloudBackupEvent.RestoreSuccess -> showMessage(restoreSuccessMessage)
                is CloudBackupViewModel.CloudBackupEvent.WebDavConnectionSuccess ->
                    showMessage(webDavConnectionSuccessMessage)
                is CloudBackupViewModel.CloudBackupEvent.LoginSuccess -> Unit // view model reloads backups
                is CloudBackupViewModel.CloudBackupEvent.GoogleDriveAuthorizationRequired -> {
                    googleDriveAuthorizationLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent).build(),
                    )
                }
                is CloudBackupViewModel.CloudBackupEvent.Error -> showMessage(event.message)
            }
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

    val uiState = BackupUiState(
        providerKinds = buildSet {
            if (hasGoogleDriveBackup) add(BackupProviderKind.GoogleDrive)
            add(BackupProviderKind.WebDav)
        },
        selectedProvider = selectedSource.toProviderKind(hasGoogleDriveBackup),
        selectedProviderReady = viewModel.canUseCloudBackup(),
        autoBackupEnabled = autoBackupEnabled,
        // Relay wires the trigger through RuntimeDependencies, so the switch stays visible.
        autoBackupSupported = true,
        webDavConfig = webDavConfig?.toBackupWebDavConfig(),
        busy = isLoading,
        backups = backups.map { it.toBackupEntry() },
        listMessage = backupListMessage,
    )
    val uiActions = BackupUiActions(
        onSelectProvider = { kind ->
            val source = kind.toBackupSource() ?: return@BackupUiActions
            if (source == selectedSource) {
                // Re-selecting the active source refreshes its backup list.
                if (viewModel.canUseCloudBackup()) viewModel.loadBackups()
            } else {
                viewModel.switchBackupSource(source)
            }
        },
        onToggleAutoBackup = viewModel::setAutoBackup,
        onUploadNow = {
            if (selectedSource == BackupSource.GOOGLE_DRIVE && hasGoogleDriveBackup && !viewModel.canUseCloudBackup()) {
                viewModel.signInWithGoogle(activity, CloudBackupViewModel.AfterLoginAction.BackupNow)
            } else {
                viewModel.backupNow()
            }
        },
        onSaveWebDavConfig = { draft ->
            viewModel.updateWebDavConfig(draft.toWebDavConfig()) != null
        },
        onTestWebDavConnection = { draft ->
            viewModel.testWebDavConfig(draft.toWebDavConfig())
        },
        onRemoveWebDavConfig = viewModel::removeWebDavConfig,
        onRefreshBackups = viewModel::loadBackups,
        onRestoreBackup = viewModel::restoreBackup,
        onDeleteBackup = viewModel::deleteBackup,
    )

    val cloudBackupBody: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (selectedSource == BackupSource.GOOGLE_DRIVE && hasGoogleDriveBackup) {
                GoogleDriveConfigSection(
                    showConfig = showGoogleDriveConfig,
                    folderPath = googleDrivePath,
                    savedFolderPath = googleDriveConfig.folderPath,
                    onFolderPathChange = { googleDrivePath = it },
                    onEdit = { showGoogleDriveConfig = true },
                    onSave = {
                        viewModel.updateGoogleDriveConfig(GoogleDriveBackupConfig(folderPath = googleDrivePath))
                        showGoogleDriveConfig = false
                        viewModel.loadBackups()
                    },
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                )
            }
            CloudBackupPanel(
                state = uiState,
                actions = uiActions,
                modifier = Modifier.weight(1f),
            )
        }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> CloudBackupScreenMiuix(
            title = stringResource(R.string.cloud_backup_screen_title),
            onBack = onBack,
            body = cloudBackupBody,
        )

        UiKitStyle.Expressive -> CloudBackupScreenMaterial(
            title = stringResource(R.string.cloud_backup_screen_title),
            onBack = onBack,
            body = cloudBackupBody,
        )
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showConfig) {
            AppText(
                text = stringResource(id = R.string.cloud_backup_google_drive_config_title),
                role = AppTextRole.Subtitle,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppTextField(
                value = folderPath,
                onValueChange = onFolderPathChange,
                label = stringResource(id = R.string.cloud_backup_google_drive_folder_path),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppPrimaryButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(id = BackupUiKitR.string.uikit_backup_save))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = stringResource(id = R.string.cloud_backup_google_drive_location, savedFolderPath),
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = stringResource(id = R.string.cloud_backup_google_drive_visible_hint),
                        role = AppTextRole.BodySmall,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
                AppSecondaryButton(onClick = onEdit) {
                    AppText(stringResource(id = BackupUiKitR.string.uikit_backup_edit_config))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun BackupSource.toProviderKind(hasGoogleDriveBackup: Boolean): BackupProviderKind? = when (this) {
    BackupSource.GOOGLE_DRIVE -> if (hasGoogleDriveBackup) BackupProviderKind.GoogleDrive else null
    BackupSource.WEBDAV -> BackupProviderKind.WebDav
}

private fun BackupProviderKind.toBackupSource(): BackupSource? = when (this) {
    BackupProviderKind.GoogleDrive -> BackupSource.GOOGLE_DRIVE
    BackupProviderKind.WebDav -> BackupSource.WEBDAV
}

private fun BackupSource.toProviderKind(): BackupProviderKind = when (this) {
    BackupSource.GOOGLE_DRIVE -> BackupProviderKind.GoogleDrive
    BackupSource.WEBDAV -> BackupProviderKind.WebDav
}

private fun WebDavConfig.toBackupWebDavConfig(): BackupWebDavConfig = BackupWebDavConfig(
    serverUrl = serverUrl,
    username = username,
    password = password,
    remotePath = remotePath,
)

private fun BackupWebDavConfig.toWebDavConfig(): WebDavConfig = WebDavConfig(
    serverUrl = serverUrl,
    username = username,
    password = password,
    remotePath = remotePath.ifBlank { WebDavConfig.DEFAULT_REMOTE_PATH },
)

private fun CloudBackupMeta.toBackupEntry(): BackupEntry = BackupEntry(
    id = id,
    name = name,
    sizeBytes = size,
    provider = source.toProviderKind(),
)
