package io.github.magisk317.relay.ui.scheduled

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.app.sender.SenderTestService
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.forward.engine.model.MsgInfo
import io.github.magisk317.forward.engine.model.ScheduledTask
import io.github.magisk317.forward.engine.schedule.CronUtils
import io.github.magisk317.forward.engine.service.ScheduledTaskRepository
import io.github.magisk317.relay.mobilefeature.scheduled.BuildConfig
import java.util.Date
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
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val senderTestService: SenderTestService,
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

    suspend fun sendTestSms(simSlot: Int, mobiles: String, content: String) {
        if (!BuildConfig.ENABLE_SMS_CHANNEL) {
            throw IllegalStateException(string(R.string.sender_channel_disabled_summary))
        }
        withContext(Dispatchers.IO) {
            senderTestService.sendScheduledSms(
                simSlot = simSlot,
                mobiles = mobiles,
                msgInfo = MsgInfo(
                    type = "sms",
                    from = "ScheduledTaskTest",
                    content = content,
                    date = Date(),
                    simInfo = "",
                    simSlot = simSlot,
                ),
                waitForSentResult = true,
            )
        }
    }

    private fun validateTask(task: ScheduledTask): String? {
        return ScheduledTaskValidation.validate(
            taskType = task.taskType,
            smsChannelEnabled = BuildConfig.ENABLE_SMS_CHANNEL,
            cronExpression = task.cronExpression,
            simSlot = task.simSlot,
            content = task.content,
            mobiles = task.mobiles,
        )?.let { report ->
            when (report.kind) {
                ScheduledTaskProblem.SMS_CHANNEL_DISABLED ->
                    string(R.string.sender_channel_disabled_summary)
                ScheduledTaskProblem.CRON_INVALID ->
                    string(R.string.scheduled_task_error_cron_invalid)
                ScheduledTaskProblem.SIM_SLOT_INVALID ->
                    string(R.string.scheduled_task_error_sim_slot)
                ScheduledTaskProblem.CONTENT_BLANK ->
                    string(R.string.scheduled_task_error_content_blank)
                ScheduledTaskProblem.MOBILES_BLANK ->
                    string(R.string.scheduled_task_error_mobiles_blank)
                ScheduledTaskProblem.MOBILE_INVALID ->
                    string(R.string.scheduled_task_error_mobile_invalid, report.invalidTarget ?: "")
            }
        }
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
