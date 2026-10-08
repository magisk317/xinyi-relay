package io.github.magisk317.relay.ui.scheduled

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.preference.AppSwitch
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppTextButton

@Composable
fun ScheduledTasksScreen(
    onBack: () -> Unit,
    onNavigateToConfig: (Long) -> Unit,
    viewModel: ScheduledTaskViewModel = org.koin.compose.viewmodel.koinViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var taskToDelete by remember { mutableStateOf<Long?>(null) }
    val promptPermissionsForTask = rememberScheduledTaskPermissionPrompter()
    val listState = rememberLazyListState()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Error will be shown in snackbar
        }
    }

    val scheduledTasksBody: @Composable (PaddingValues) -> Unit = { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when {
            isLoading -> {
                AppCircularProgressIndicator(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                )
            }
            tasks.isEmpty() -> {
                AppText(
                    text = stringResource(id = R.string.scheduled_task_empty),
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                )
            }
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(tasks, key = { it.id }) { task ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            onClick = { onNavigateToConfig(task.id) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    AppText(text = task.name, role = AppTextRole.Subtitle)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AppText(
                                        text = stringResource(
                                            id = R.string.scheduled_task_line_cron,
                                            task.cronExpression,
                                        ),
                                        role = AppTextRole.Body,
                                    )
                                    val statusText = if (task.status == ScheduledTask.STATUS_ENABLED) {
                                        stringResource(id = R.string.scheduled_task_status_enabled)
                                    } else {
                                        stringResource(id = R.string.scheduled_task_status_disabled)
                                    }
                                    AppText(
                                        text = stringResource(
                                            id = R.string.scheduled_task_line_sim_status,
                                            task.simSlot,
                                            statusText,
                                        ),
                                        role = AppTextRole.BodySmall,
                                    )
                                }
                                Column {
                                    AppSwitch(
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
                                    AppIconButton(onClick = { taskToDelete = task.id }) {
                                        AppIcon(
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
        AppAlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { AppText(stringResource(id = R.string.scheduled_task_delete_dialog_title)) },
            text = { AppText(stringResource(id = R.string.scheduled_task_delete_dialog_message)) },
            confirmButton = {
                AppTextButton(text = stringResource(id = R.string.action_delete), onClick = {
                        viewModel.deleteTask(
                            taskId = taskId,
                            onSuccess = { taskToDelete = null },
                            onError = { taskToDelete = null }
                        )
                    })
            },
            dismissButton = {
                AppTextButton(text = stringResource(id = R.string.cancel), onClick = { taskToDelete = null })
            }
        )
    }

    // Error snackbar
    errorMessage?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                AppTextButton(text = stringResource(id = R.string.action_close), onClick = { viewModel.clearError() })
            }
        ) {
            AppText(error)
        }
    }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> ScheduledTasksScreenMiuix(
            title = stringResource(R.string.scheduled_task_list_title),
            onBack = onBack,
            onAddClick = { onNavigateToConfig(0L) },
            fabContentDescription = stringResource(R.string.scheduled_task_add_title),
            listState = listState,
            body = scheduledTasksBody,
        )

        UiKitStyle.Expressive -> ScheduledTasksScreenMaterial(
            title = stringResource(R.string.scheduled_task_list_title),
            onBack = onBack,
            onAddClick = { onNavigateToConfig(0L) },
            fabContentDescription = stringResource(R.string.scheduled_task_add_title),
            listState = listState,
            body = scheduledTasksBody,
        )
    }
}
