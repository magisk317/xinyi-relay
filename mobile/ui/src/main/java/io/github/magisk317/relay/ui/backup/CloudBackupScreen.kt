package io.github.magisk317.relay.ui.backup

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.relay.backup.BackupSource
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import io.github.magisk317.relay.core.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    viewModel: CloudBackupViewModel = viewModel(),
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val webDavConfig by viewModel.webDavConfig.collectAsStateWithLifecycle()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    var showWebDavConfig by remember { mutableStateOf(false) }
    var webDavServer by remember { mutableStateOf("") }
    var webDavUsername by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var webDavPath by remember { mutableStateOf("/xinyi-relay/backups/") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CloudBackupViewModel.CloudBackupEvent.BackupSuccess -> {
                    viewModel.loadBackups()
                }
                is CloudBackupViewModel.CloudBackupEvent.RestoreSuccess -> {
                    viewModel.loadBackups()
                }
                is CloudBackupViewModel.CloudBackupEvent.WebDavConnectionSuccess -> {
                    // TODO: Show success message
                }
                is CloudBackupViewModel.CloudBackupEvent.LoginSuccess -> {
                    // Handled automatically by view model loading backups
                }
                is CloudBackupViewModel.CloudBackupEvent.Error -> {
                    // TODO: Show error message
                }
            }
        }
    }

    LaunchedEffect(webDavConfig) {
        webDavConfig?.let {
            webDavServer = it.serverUrl
            webDavUsername = it.username
            webDavPassword = it.password
            webDavPath = it.remotePath
        }
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
                text = "Backup Source",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isGoogleDriveUsable = selectedSource == BackupSource.GOOGLE_DRIVE && viewModel.canUseCloudBackup()
                Button(
                    onClick = {
                        if (selectedSource != BackupSource.GOOGLE_DRIVE) {
                            viewModel.switchBackupSource(BackupSource.GOOGLE_DRIVE)
                        }
                        if (!viewModel.canUseCloudBackup()) {
                            googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
                        }
                    },
                    enabled = !isGoogleDriveUsable,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Google Drive")
                }
                Button(
                    onClick = { viewModel.switchBackupSource(BackupSource.WEBDAV) },
                    enabled = selectedSource != BackupSource.WEBDAV,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("WebDAV")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // WebDAV configuration
            if (selectedSource == BackupSource.WEBDAV) {
                if (showWebDavConfig || webDavConfig == null) {
                    Text(
                        text = "WebDAV Configuration",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavServer,
                        onValueChange = { webDavServer = it },
                        label = { Text("Server URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavUsername,
                        onValueChange = { webDavUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavPassword,
                        onValueChange = { webDavPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavPath,
                        onValueChange = { webDavPath = it },
                        label = { Text("Remote Path") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val config = WebDavConfig(
                                    serverUrl = webDavServer,
                                    username = webDavUsername,
                                    password = webDavPassword,
                                    remotePath = webDavPath,
                                )
                                viewModel.updateWebDavConfig(config)
                                showWebDavConfig = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save")
                        }
                        Button(
                            onClick = {
                                val config = WebDavConfig(
                                    serverUrl = webDavServer,
                                    username = webDavUsername,
                                    password = webDavPassword,
                                    remotePath = webDavPath,
                                )
                                viewModel.updateWebDavConfig(config)
                                viewModel.testWebDavConnection()
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            Text("Test")
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
                            Text("Edit Config")
                        }
                        Button(
                            onClick = { viewModel.removeWebDavConfig() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Remove")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Controls
            if (viewModel.canUseCloudBackup()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.backupNow() },
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

                // Auto backup toggle
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

                // Backups list
                if (backups.isEmpty()) {
                    Text(stringResource(id = R.string.cloud_backup_no_backups))
                } else {
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
                                    Text(backup.name)
                                    Text(
                                        text = stringResource(id = R.string.cloud_backup_item_size, backup.size),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Row {
                                    Button(onClick = { viewModel.restoreBackup(backup.id) }) {
                                        Text(stringResource(id = R.string.cloud_backup_restore))
                                    }
                                    Button(onClick = { viewModel.deleteBackup(backup.id) }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
