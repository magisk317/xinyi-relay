package io.github.magisk317.relay.ui.scheduled

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.ScheduledTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    onBack: () -> Unit,
    onNavigateToConfig: (Long) -> Unit,
    viewModel: ScheduledTaskViewModel = org.koin.compose.viewmodel.koinViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var taskToDelete by remember { mutableStateOf<Long?>(null) }
    val promptPermissionsForTask = rememberScheduledTaskPermissionPrompter()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Error will be shown in snackbar
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.scheduled_task_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back),
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToConfig(0L) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.scheduled_task_add_title))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                }
                tasks.isEmpty() -> {
                    Text(
                        text = stringResource(id = R.string.scheduled_task_empty),
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(tasks, key = { it.id }) { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                onClick = { onNavigateToConfig(task.id) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = task.name, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(
                                                id = R.string.scheduled_task_line_cron,
                                                task.cronExpression,
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        val statusText = if (task.status == ScheduledTask.STATUS_ENABLED) {
                                            stringResource(id = R.string.scheduled_task_status_enabled)
                                        } else {
                                            stringResource(id = R.string.scheduled_task_status_disabled)
                                        }
                                        Text(
                                            text = stringResource(
                                                id = R.string.scheduled_task_line_sim_status,
                                                task.simSlot,
                                                statusText,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Column {
                                        Switch(
                                            checked = task.status == ScheduledTask.STATUS_ENABLED,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    promptPermissionsForTask(
                                                        task.copy(status = ScheduledTask.STATUS_ENABLED),
                                                    ) {}
                                                }
                                                viewModel.toggleTaskStatus(task.id) { error ->
                                                    // Error handled by viewModel
                                                }
                                            }
                                        )
                                        IconButton(onClick = { taskToDelete = task.id }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.action_delete),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        taskToDelete?.let { taskId ->
            AlertDialog(
                onDismissRequest = { taskToDelete = null },
                title = { Text(stringResource(id = R.string.scheduled_task_delete_dialog_title)) },
                text = { Text(stringResource(id = R.string.scheduled_task_delete_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteTask(
                                taskId = taskId,
                                onSuccess = { taskToDelete = null },
                                onError = { taskToDelete = null }
                            )
                        }
                    ) {
                        Text(stringResource(id = R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskToDelete = null }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            )
        }

        // Error snackbar
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}
