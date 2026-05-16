package io.github.magisk317.relay.ui.scheduled

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.relay.engine.schedule.CronUtils
import io.github.magisk317.relay.engine.service.ScheduledTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduledTaskViewModel(
    application: Application,
    private val scheduledTaskRepository: ScheduledTaskRepository
) : AndroidViewModel(application) {

    val tasks: StateFlow<List<ScheduledTask>> = scheduledTaskRepository.getAllTasksFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    scheduledTaskRepository.getAllTasks()
                }
            } catch (e: Exception) {
                XLog.e("Failed to load scheduled tasks", e)
                _errorMessage.value = string(
                    R.string.scheduled_task_error_load_failed,
                    e.messageOrType(),
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveTask(task: ScheduledTask, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val validationError = validateTask(task)
                if (validationError != null) {
                    onError(validationError)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    if (task.id == 0L) {
                        scheduledTaskRepository.insertTask(task)
                    } else {
                        scheduledTaskRepository.updateTask(task)
                    }
                }

                onSuccess()
            } catch (e: Exception) {
                XLog.e("Failed to save scheduled task", e)
                onError(string(R.string.scheduled_task_error_save_failed, e.messageOrType()))
            }
        }
    }

    fun deleteTask(taskId: Long, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val task = scheduledTaskRepository.getTaskById(taskId)
                    if (task != null) {
                        scheduledTaskRepository.deleteTask(task)
                    }
                }

                onSuccess()
            } catch (e: Exception) {
                XLog.e("Failed to delete scheduled task", e)
                onError(string(R.string.scheduled_task_error_delete_failed, e.messageOrType()))
            }
        }
    }

    fun toggleTaskStatus(taskId: Long, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val task = scheduledTaskRepository.getTaskById(taskId)
                    if (task != null) {
                        val updatedTask = task.copy(
                            status = if (task.status == ScheduledTask.STATUS_ENABLED) {
                                ScheduledTask.STATUS_DISABLED
                            } else {
                                ScheduledTask.STATUS_ENABLED
                            }
                        )
                        if (updatedTask.status == ScheduledTask.STATUS_ENABLED) {
                            validateTask(updatedTask)?.let { error ->
                                throw IllegalArgumentException(error)
                            }
                        }
                        scheduledTaskRepository.updateTask(updatedTask)
                    }
                }
            } catch (e: Exception) {
                XLog.e("Failed to toggle task status", e)
                val message = string(R.string.scheduled_task_error_toggle_failed, e.messageOrType())
                _errorMessage.value = message
                onError(message)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun validateTask(task: ScheduledTask): String? {
        CronUtils.validateCronExpression(task.cronExpression)?.let {
            return string(R.string.scheduled_task_error_cron_invalid)
        }
        if (task.simSlot !in 0..2) {
            return string(R.string.scheduled_task_error_sim_slot)
        }
        if (task.content.isBlank()) {
            return string(R.string.scheduled_task_error_content_blank)
        }

        val targets = task.mobiles
            .replace("[,，;；]".toRegex(), ",")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (targets.isEmpty()) {
            return string(R.string.scheduled_task_error_mobiles_blank)
        }

        val mobilePattern = Regex("""^\+?[0-9][0-9\-\s]{2,}$""")
        val invalidTarget = targets.firstOrNull { target -> !mobilePattern.matches(target) }
        if (invalidTarget != null) {
            return string(R.string.scheduled_task_error_mobile_invalid, invalidTarget)
        }

        return null
    }

    private fun string(@StringRes id: Int, vararg args: Any): String {
        return if (args.isEmpty()) {
            getApplication<Application>().getString(id)
        } else {
            getApplication<Application>().getString(id, *args)
        }
    }

    private fun Throwable.messageOrType(): String {
        return message ?: javaClass.simpleName
    }
}
