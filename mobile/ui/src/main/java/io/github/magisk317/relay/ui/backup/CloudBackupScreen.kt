package io.github.magisk317.relay.ui.backup

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.relay.core.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: CloudBackupViewModel = viewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CloudBackupViewModel.CloudBackupEvent.BackupSuccess -> {
                    viewModel.loadBackups()
                }
                is CloudBackupViewModel.CloudBackupEvent.RestoreSuccess -> {
                    viewModel.loadBackups()
                }
                is CloudBackupViewModel.CloudBackupEvent.Error -> {
                    // TODO: Show error message
                }
            }
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
            if (!viewModel.isLoggedIn()) {
                Text(stringResource(id = R.string.cloud_backup_login_required))
                Button(onClick = onNavigateToLogin) {
                    Text("Sign In")
                }
                return@Scaffold
            }

            if (!viewModel.isSubscriptionActive()) {
                Text(stringResource(id = R.string.cloud_backup_subscription_required))
                Button(onClick = onNavigateToDonate) {
                    Text("Subscribe")
                }
                return@Scaffold
            }

            // Controls
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
