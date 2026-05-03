package io.github.magisk317.relay.ui.scheduled

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.schedule.CronUtils
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.ScheduledTask
import io.github.magisk317.relay.sender.SmsUtils
import io.github.magisk317.relay.sender.config.SmsSetting
import io.github.magisk317.relay.ui.common.DismissibleSnackbarHost
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.sender.forms.ActiveScheduleTimeValueButton
import io.github.magisk317.relay.ui.sender.forms.ActiveScheduleWeekdayRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

private enum class ScheduledTaskScheduleMode {
    SIMPLE,
    ADVANCED,
}

private data class ScheduledTaskQueryPreset(
    val id: String,
    @param:StringRes val carrierNameRes: Int,
    @param:StringRes val queryNameRes: Int,
    val target: String,
    val content: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskConfigScreen(
    taskId: Long,
    onBack: () -> Unit,
    viewModel: ScheduledTaskViewModel = org.koin.compose.viewmodel.koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var name by remember(taskId) { mutableStateOf("") }
    var scheduleMode by remember(taskId) { mutableStateOf(ScheduledTaskScheduleMode.SIMPLE) }
    var simpleWeekdays by remember(taskId) { mutableStateOf(SCHEDULED_TASK_ALL_WEEKDAYS) }
    var simpleTime by remember(taskId) { mutableStateOf("10:00") }
    var cron by remember(taskId) { mutableStateOf("") }
    var mobiles by remember(taskId) { mutableStateOf("") }
    var content by remember(taskId) { mutableStateOf("") }
    var simSlot by remember(taskId) { mutableStateOf("0") }
    var status by remember(taskId) { mutableStateOf(ScheduledTask.STATUS_ENABLED) }
    var queryPresetId by remember(taskId) { mutableStateOf(SCHEDULED_TASK_QUERY_PRESET_CUSTOM_ID) }
    var queryPresetExpanded by remember(taskId) { mutableStateOf(false) }
    var loadedTaskId by remember(taskId) { mutableStateOf<Long?>(null) }
    var smsTestRunning by remember(taskId) { mutableStateOf(false) }
    var shortCodeConfirmationBypassed by remember { mutableStateOf(false) }
    var shortCodeConfirmationUpdating by remember { mutableStateOf(false) }

    var simpleWeekdaysError by remember(taskId) { mutableStateOf<String?>(null) }
    var simpleTimeError by remember(taskId) { mutableStateOf<String?>(null) }
    var cronError by remember(taskId) { mutableStateOf<String?>(null) }
    var simSlotError by remember(taskId) { mutableStateOf<String?>(null) }
    var mobilesError by remember(taskId) { mutableStateOf<String?>(null) }
    var contentError by remember(taskId) { mutableStateOf<String?>(null) }
    var saveError by remember(taskId) { mutableStateOf<String?>(null) }

    val promptPermissionsForTask = rememberScheduledTaskPermissionPrompter()
    val tasks by viewModel.tasks.collectAsState()
    val defaultName = stringResource(id = R.string.scheduled_task_default_name)
    val cronBlankError = stringResource(id = R.string.scheduled_task_error_cron_blank)
    val simpleWeekdaysBlankError = stringResource(id = R.string.scheduled_task_error_weekdays_blank)
    val simpleTimeInvalidError = stringResource(id = R.string.scheduled_task_error_time_invalid)
    val simSlotErrorText = stringResource(id = R.string.scheduled_task_error_sim_slot)
    val mobilesBlankError = stringResource(id = R.string.scheduled_task_error_mobiles_blank)
    val contentBlankError = stringResource(id = R.string.scheduled_task_error_content_blank)
    val queryPresetCustomLabel = stringResource(id = R.string.scheduled_task_query_preset_custom)
    val selectedQueryPreset = SCHEDULED_TASK_QUERY_PRESETS.firstOrNull { it.id == queryPresetId }
    val selectedQueryPresetLabel = selectedQueryPreset?.label() ?: queryPresetCustomLabel
    val smsTestStartedText = stringResource(id = R.string.scheduled_task_test_started)
    val smsTestSucceededText = stringResource(id = R.string.scheduled_task_test_succeeded)
    val smsTestFailedFormat = stringResource(id = R.string.scheduled_task_test_failed)
    val shortCodeBypassEnabledText = stringResource(id = R.string.scheduled_task_short_code_bypass_enabled)
    val shortCodeBypassDisabledText = stringResource(id = R.string.scheduled_task_short_code_bypass_disabled)
    val shortCodeBypassFailedFormat = stringResource(id = R.string.scheduled_task_short_code_bypass_failed)

    LaunchedEffect(taskId, tasks) {
        if (taskId != 0L && loadedTaskId != taskId) {
            val task = tasks.find { it.id == taskId }
            if (task != null) {
                name = task.name
                cron = task.cronExpression
                CronUtils.parseSimpleWeeklyCron(task.cronExpression)?.let { simpleSchedule ->
                    scheduleMode = ScheduledTaskScheduleMode.SIMPLE
                    simpleWeekdays = simpleSchedule.weekdays
                    simpleTime = simpleSchedule.time
                } ?: run {
                    scheduleMode = ScheduledTaskScheduleMode.ADVANCED
                }
                mobiles = task.mobiles
                content = task.content
                queryPresetId = findQueryPresetId(task.mobiles, task.content)
                simSlot = task.simSlot.toString()
                status = task.status
                loadedTaskId = taskId
            }
        }
    }

    LaunchedEffect(Unit) {
        shortCodeConfirmationBypassed =
            ScheduledTaskRootDebugTools.isShortCodeConfirmationBypassed(context.applicationContext)
    }

    fun validateSmsFieldsForTest(): Int? {
        simSlotError = null
        mobilesError = null
        contentError = null

        val simSlotInt = simSlot.toIntOrNull()
        if (simSlotInt == null || simSlotInt !in 0..2) {
            simSlotError = simSlotErrorText
            return null
        }

        if (mobiles.isBlank()) {
            mobilesError = mobilesBlankError
            return null
        }

        if (content.isBlank()) {
            contentError = contentBlankError
            return null
        }

        return simSlotInt
    }

    fun sendTestSms() {
        val simSlotInt = validateSmsFieldsForTest() ?: return
        if (smsTestRunning) return
        scope.launch {
            smsTestRunning = true
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                snackbarHostState.showSnackbar(smsTestStartedText)
            }
            val result = runCatching {
                SmsUtils.sendMsg(
                    context = context.applicationContext,
                    setting = SmsSetting(
                        simSlot = simSlotInt,
                        mobiles = mobiles,
                        onlyNoNetwork = false,
                    ),
                    msgInfo = MsgInfo(
                        type = "sms",
                        from = "ScheduledTaskTest",
                        content = content,
                        date = Date(),
                        simInfo = "",
                        simSlot = simSlotInt,
                    ),
                    waitForSentResult = true,
                )
            }
            smsTestRunning = false
            snackbarHostState.currentSnackbarData?.dismiss()
            result.onSuccess {
                snackbarHostState.showSnackbar(smsTestSucceededText)
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar(
                    smsTestFailedFormat.format(throwable.message ?: throwable.javaClass.simpleName),
                )
            }
        }
    }

    fun updateShortCodeConfirmationBypass(enabled: Boolean) {
        if (shortCodeConfirmationUpdating) return
        scope.launch {
            shortCodeConfirmationUpdating = true
            val result = ScheduledTaskRootDebugTools.setShortCodeConfirmationBypassed(enabled)
            shortCodeConfirmationUpdating = false
            result.onSuccess {
                shortCodeConfirmationBypassed = enabled
                snackbarHostState.showSnackbar(
                    if (enabled) {
                        shortCodeBypassEnabledText
                    } else {
                        shortCodeBypassDisabledText
                    },
                )
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar(
                    shortCodeBypassFailedFormat.format(throwable.message ?: throwable.javaClass.simpleName),
                )
            }
        }
    }

    fun validateAndSave() {
        simpleWeekdaysError = null
        simpleTimeError = null
        cronError = null
        simSlotError = null
        mobilesError = null
        contentError = null
        saveError = null

        val cronForSave = if (scheduleMode == ScheduledTaskScheduleMode.SIMPLE) {
            if (simpleWeekdays.isEmpty()) {
                simpleWeekdaysError = simpleWeekdaysBlankError
                return
            }
            runCatching {
                CronUtils.buildSimpleWeeklyCron(simpleTime, simpleWeekdays)
            }.getOrElse {
                simpleTimeError = simpleTimeInvalidError
                return
            }
        } else {
            if (cron.isBlank()) {
                cronError = cronBlankError
                return
            }
            cron.trim()
        }

        val simSlotInt = simSlot.toIntOrNull()
        if (simSlotInt == null || simSlotInt !in 0..2) {
            simSlotError = simSlotErrorText
            return
        }

        if (mobiles.isBlank()) {
            mobilesError = mobilesBlankError
            return
        }

        if (content.isBlank()) {
            contentError = contentBlankError
            return
        }

        val existingTask = tasks.find { it.id == taskId }
        val task = ScheduledTask(
            id = taskId,
            name = name.ifBlank { defaultName },
            taskType = ScheduledTask.TASK_TYPE_SMS,
            cronExpression = cronForSave,
            simSlot = simSlotInt,
            mobiles = mobiles,
            content = content,
            status = status,
            nextRunTime = existingTask?.nextRunTime ?: 0L,
            lastRunTime = existingTask?.lastRunTime ?: 0L,
            createdAt = existingTask?.createdAt ?: System.currentTimeMillis()
        )

        viewModel.saveTask(
            task = task,
            onSuccess = {
                promptPermissionsForTask(task, onBack)
            },
            onError = { error -> saveError = error }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (taskId == 0L) {
                            stringResource(id = R.string.scheduled_task_add_title)
                        } else {
                            stringResource(id = R.string.scheduled_task_edit_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { validateAndSave() }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(id = R.string.save))
                    }
                }
            )
        },
        snackbarHost = {
            DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(id = R.string.scheduled_task_name_label)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text(stringResource(id = R.string.scheduled_task_name_placeholder)) }
            )

            Text(
                text = stringResource(id = R.string.scheduled_task_schedule_mode_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            SingleChoiceSegmentedSelector(
                options = listOf(
                    SegmentedOption(
                        ScheduledTaskScheduleMode.SIMPLE,
                        stringResource(id = R.string.scheduled_task_schedule_mode_simple),
                    ),
                    SegmentedOption(
                        ScheduledTaskScheduleMode.ADVANCED,
                        stringResource(id = R.string.scheduled_task_schedule_mode_advanced),
                    ),
                ),
                selected = scheduleMode,
                onSelect = { nextMode ->
                    if (nextMode == ScheduledTaskScheduleMode.ADVANCED && cron.isBlank()) {
                        cron = runCatching {
                            CronUtils.buildSimpleWeeklyCron(simpleTime, simpleWeekdays)
                        }.getOrDefault("")
                    }
                    scheduleMode = nextMode
                    simpleWeekdaysError = null
                    simpleTimeError = null
                    cronError = null
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (scheduleMode == ScheduledTaskScheduleMode.SIMPLE) {
                Text(
                    text = stringResource(id = R.string.sender_active_schedule_weekdays_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ActiveScheduleWeekdayRow(
                    weekdays = listOf(1, 2, 3, 4),
                    selectedWeekdays = simpleWeekdays,
                    onWeekdayToggle = { weekday ->
                        simpleWeekdays = simpleWeekdays.toggleWeekday(weekday)
                        simpleWeekdaysError = null
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActiveScheduleWeekdayRow(
                    weekdays = SCHEDULED_TASK_SECOND_WEEKDAY_ROW,
                    selectedWeekdays = simpleWeekdays,
                    onWeekdayToggle = { weekday ->
                        simpleWeekdays = simpleWeekdays.toggleWeekday(weekday)
                        simpleWeekdaysError = null
                    },
                )
                simpleWeekdaysError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Text(
                    text = stringResource(id = R.string.scheduled_task_simple_time_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                ActiveScheduleTimeValueButton(
                    value = simpleTime,
                    onValueChange = { value ->
                        simpleTime = value
                        simpleTimeError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                simpleTimeError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = stringResource(
                        id = R.string.scheduled_task_generated_cron,
                        runCatching {
                            CronUtils.buildSimpleWeeklyCron(simpleTime, simpleWeekdays)
                        }.getOrDefault(""),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
            } else {
                OutlinedTextField(
                    value = cron,
                    onValueChange = {
                        cron = it
                        cronError = null
                    },
                    label = { Text(stringResource(id = R.string.scheduled_task_cron_label)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    isError = cronError != null,
                    supportingText = {
                        Text(
                            cronError ?: stringResource(id = R.string.scheduled_task_cron_hint),
                            color = if (cronError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }

            Text(
                text = stringResource(id = R.string.scheduled_task_query_preset_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            ExposedDropdownMenuBox(
                expanded = queryPresetExpanded,
                onExpandedChange = { queryPresetExpanded = !queryPresetExpanded },
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                OutlinedTextField(
                    value = selectedQueryPresetLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(id = R.string.scheduled_task_query_preset_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = queryPresetExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = queryPresetExpanded,
                    onDismissRequest = { queryPresetExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(queryPresetCustomLabel) },
                        onClick = {
                            queryPresetId = SCHEDULED_TASK_QUERY_PRESET_CUSTOM_ID
                            queryPresetExpanded = false
                        },
                    )
                    SCHEDULED_TASK_QUERY_PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label()) },
                            onClick = {
                                queryPresetId = preset.id
                                mobiles = preset.target
                                content = preset.content
                                mobilesError = null
                                contentError = null
                                queryPresetExpanded = false
                            },
                        )
                    }
                }
            }
            Text(
                text = stringResource(id = R.string.scheduled_task_query_preset_reference_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = simSlot,
                onValueChange = {
                    simSlot = it
                    simSlotError = null
                },
                label = { Text(stringResource(id = R.string.scheduled_task_sim_slot_label)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                isError = simSlotError != null,
                supportingText = {
                    Text(
                        simSlotError ?: stringResource(id = R.string.scheduled_task_sim_slot_hint),
                        color = if (simSlotError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            OutlinedTextField(
                value = mobiles,
                onValueChange = {
                    mobiles = it
                    queryPresetId = SCHEDULED_TASK_QUERY_PRESET_CUSTOM_ID
                    mobilesError = null
                },
                label = { Text(stringResource(id = R.string.scheduled_task_mobiles_label)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                isError = mobilesError != null,
                supportingText = {
                    Text(
                        mobilesError ?: stringResource(id = R.string.scheduled_task_mobiles_hint),
                        color = if (mobilesError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    queryPresetId = SCHEDULED_TASK_QUERY_PRESET_CUSTOM_ID
                    contentError = null
                },
                label = { Text(stringResource(id = R.string.scheduled_task_content_label)) },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 8.dp),
                isError = contentError != null,
                supportingText = {
                    contentError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                },
                maxLines = 5
            )

            ScheduledTaskDebugSection(
                testRunning = smsTestRunning,
                shortCodeConfirmationBypassed = shortCodeConfirmationBypassed,
                shortCodeConfirmationEnabled = !shortCodeConfirmationUpdating,
                onTestClick = ::sendTestSms,
                onShortCodeConfirmationBypassChange = ::updateShortCodeConfirmationBypass,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(id = R.string.scheduled_task_enable_label), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = status == ScheduledTask.STATUS_ENABLED,
                    onCheckedChange = {
                        status = if (it) {
                            ScheduledTask.STATUS_ENABLED
                        } else {
                            ScheduledTask.STATUS_DISABLED
                        }
                    }
                )
            }

            saveError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private val SCHEDULED_TASK_ALL_WEEKDAYS = (1..7).toList()
private val SCHEDULED_TASK_SECOND_WEEKDAY_ROW = listOf(5, 6, 7)
private const val SCHEDULED_TASK_QUERY_PRESET_CUSTOM_ID = "custom"
private val SCHEDULED_TASK_QUERY_PRESETS = listOf(
    ScheduledTaskQueryPreset(
        id = "cmcc_balance",
        carrierNameRes = R.string.scheduled_task_carrier_cmcc,
        queryNameRes = R.string.scheduled_task_query_type_balance,
        target = "10086",
        content = "CXYE",
    ),
    ScheduledTaskQueryPreset(
        id = "cmcc_data",
        carrierNameRes = R.string.scheduled_task_carrier_cmcc,
        queryNameRes = R.string.scheduled_task_query_type_data,
        target = "10086",
        content = "CXSJLL",
    ),
    ScheduledTaskQueryPreset(
        id = "cucc_balance",
        carrierNameRes = R.string.scheduled_task_carrier_cucc,
        queryNameRes = R.string.scheduled_task_query_type_balance,
        target = "10010",
        content = "CXYE",
    ),
    ScheduledTaskQueryPreset(
        id = "cucc_data",
        carrierNameRes = R.string.scheduled_task_carrier_cucc,
        queryNameRes = R.string.scheduled_task_query_type_data,
        target = "10010",
        content = "CXLL",
    ),
    ScheduledTaskQueryPreset(
        id = "ctcc_balance",
        carrierNameRes = R.string.scheduled_task_carrier_ctcc,
        queryNameRes = R.string.scheduled_task_query_type_balance,
        target = "10001",
        content = "102",
    ),
    ScheduledTaskQueryPreset(
        id = "ctcc_data",
        carrierNameRes = R.string.scheduled_task_carrier_ctcc,
        queryNameRes = R.string.scheduled_task_query_type_data,
        target = "10001",
        content = "108",
    ),
    ScheduledTaskQueryPreset(
        id = "cbn_balance",
        carrierNameRes = R.string.scheduled_task_carrier_cbn,
        queryNameRes = R.string.scheduled_task_query_type_balance,
        target = "10099",
        content = "CXMYYE",
    ),
    ScheduledTaskQueryPreset(
        id = "cbn_data",
        carrierNameRes = R.string.scheduled_task_carrier_cbn,
        queryNameRes = R.string.scheduled_task_query_type_data,
        target = "10099",
        content = "CXLL",
    ),
)

private fun List<Int>.toggleWeekday(weekday: Int): List<Int> {
    return if (weekday in this) {
        filterNot { it == weekday }
    } else {
        (this + weekday).distinct().sorted()
    }
}

@Composable
private fun ScheduledTaskQueryPreset.label(): String {
    return stringResource(
        id = R.string.scheduled_task_query_preset_format,
        stringResource(id = carrierNameRes),
        stringResource(id = queryNameRes),
    )
}

private fun findQueryPresetId(target: String, content: String): String {
    return SCHEDULED_TASK_QUERY_PRESETS.firstOrNull { preset ->
        preset.target == target.trim() && preset.content.equals(content.trim(), ignoreCase = true)
    }?.id ?: SCHEDULED_TASK_QUERY_PRESET_CUSTOM_ID
}

@Composable
private fun ScheduledTaskDebugSection(
    testRunning: Boolean,
    shortCodeConfirmationBypassed: Boolean,
    shortCodeConfirmationEnabled: Boolean,
    onTestClick: () -> Unit,
    onShortCodeConfirmationBypassChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.scheduled_task_debug_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onTestClick,
                enabled = !testRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (testRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(id = R.string.scheduled_task_test_send_button))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.scheduled_task_short_code_bypass_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(id = R.string.scheduled_task_short_code_bypass_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = shortCodeConfirmationBypassed,
                    enabled = shortCodeConfirmationEnabled,
                    onCheckedChange = onShortCodeConfirmationBypassChange,
                )
            }
        }
    }
}

private object ScheduledTaskRootDebugTools {
    private const val SHORT_CODE_CONFIRMATION_SETTING = "sms_short_code_confirmation"
    private const val ROOT_COMMAND_TIMEOUT_SEC = 10L

    fun isShortCodeConfirmationBypassed(context: android.content.Context): Boolean {
        return Settings.Global.getString(context.contentResolver, SHORT_CODE_CONFIRMATION_SETTING) == "0"
    }

    suspend fun setShortCodeConfirmationBypassed(enabled: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val value = if (enabled) "0" else "1"
            val result = runSuCommand("settings put global $SHORT_CODE_CONFIRMATION_SETTING $value")
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("root exit=${result.exitCode}"))
            }
        }
    }

    private fun runSuCommand(command: String): ShellCommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(ROOT_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                return ShellCommandResult(exitCode = -2, output = "")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            ShellCommandResult(exitCode = process.exitValue(), output = output)
        } catch (e: Exception) {
            ShellCommandResult(exitCode = -1, output = e.message.orEmpty())
        }
    }

    private data class ShellCommandResult(
        val exitCode: Int,
        val output: String,
    ) {
        val success: Boolean
            get() = exitCode == 0
    }
}
