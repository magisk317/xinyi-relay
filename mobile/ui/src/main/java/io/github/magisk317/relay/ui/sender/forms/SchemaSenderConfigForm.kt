package io.github.magisk317.relay.ui.sender.forms

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderActiveSchedule
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.sender.SenderSettingDrafts
import io.github.magisk317.relay.sender.SenderSettingFieldMetadata
import io.github.magisk317.relay.sender.SenderSettingFieldType
import io.github.magisk317.relay.sender.SenderSettingJson
import io.github.magisk317.relay.sender.SenderSettingSchemas
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

internal data class SchemaSenderFormFieldSpec(
    val name: String,
    @StringRes val labelRes: Int,
    @StringRes val placeholderRes: Int? = null,
    @StringRes val supportingTextRes: Int? = null,
    val minLines: Int = 1,
    val optionLabelRes: Map<String, Int> = emptyMap(),
)

internal val MessageTypeOptionLabels = mapOf(
    "text" to R.string.sender_segment_text,
    "markdown" to R.string.sender_segment_markdown,
)

internal val InteractiveMessageTypeOptionLabels = mapOf(
    "interactive" to R.string.sender_segment_interactive,
    "text" to R.string.sender_segment_text,
)

internal fun SenderSettingDraft.keepOnlyFields(names: Collection<String>): SenderSettingDraft {
    val visibleNameSet = names.toSet()
    return SenderSettingDraft(
        senderType = senderType,
        values = values.filterKeys { it in visibleNameSet },
    )
}

private fun SenderSettingDraft.normalizedStructuredFields(): SenderSettingDraft {
    var nextDraft = this
    schema?.fields.orEmpty().forEach { field ->
        when (field.type) {
            SenderSettingFieldType.STRING_MAP -> {
                val element = nextDraft.element(field.name)
                if (element is JsonObject) return@forEach
                val rawValue = nextDraft.string(field.name)
                val mapValue = if (rawValue.isBlank()) {
                    emptyMap()
                } else {
                    SenderSettingJson.decodeStringMapLenientOrNull(rawValue)
                        ?: throw IllegalArgumentException("Invalid ${field.name} JSON, e.g. {\"Authorization\":\"Bearer xxx\"}")
                }
                nextDraft = nextDraft.withStringMap(field.name, mapValue)
            }
            SenderSettingFieldType.EMAIL_RECIPIENTS -> {
                val element = nextDraft.element(field.name)
                if (element is JsonObject) return@forEach
                val rawValue = nextDraft.string(field.name)
                val objectValue = if (rawValue.isBlank()) {
                    JsonObject(emptyMap())
                } else {
                    SenderSettingJson.parseObject(rawValue)
                        ?: throw IllegalArgumentException("Invalid ${field.name} JSON")
                }
                nextDraft = nextDraft.withElement(field.name, objectValue)
            }
            else -> Unit
        }
    }
    return nextDraft
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SchemaSenderConfigForm(
    senderId: Long,
    senderType: Int,
    channel: String,
    fields: List<SchemaSenderFormFieldSpec>,
    onBack: () -> Unit,
    viewModel: SenderViewModel,
    normalizeDraft: (SenderSettingDraft) -> SenderSettingDraft = { it },
    validateDraft: (SenderSettingDraft, Int) -> String? = { _, _ -> null },
    extraContent: @Composable (SenderSettingDraft, (SenderSettingDraft) -> Unit) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val activeScheduleEntry = LocalSenderActiveScheduleEntry.current
    val activeSchedule = activeScheduleEntry?.schedule ?: SenderActiveSchedule()

    var name by remember { mutableStateOf("") }
    var draft by remember(senderType) {
        mutableStateOf(normalizeDraft(SenderSettingDrafts.emptyWithDefaults(senderType)))
    }
    var receiveCode by remember { mutableStateOf(true) }
    var receiveNonCode by remember { mutableStateOf(true) }
    var receiveAppNotify by remember { mutableStateOf(true) }
    var receiveCallNotify by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }
    var currentSender by remember { mutableStateOf<Sender?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(senderId, senderType) {
        if (senderId > 0) {
            val sender = viewModel.getSender(senderId)
            if (sender != null) {
                currentSender = sender
                name = sender.name
                receiveCode = sender.receiveCode == 1
                receiveNonCode = sender.receiveNonCode == 1
                receiveAppNotify = sender.receiveAppNotify == 1
                receiveCallNotify = sender.receiveCallNotify == 1
                draft = normalizeDraft(SenderSettingDrafts.fromSenderWithDefaults(sender))
            }
        } else {
            currentSender = null
            name = ""
            draft = normalizeDraft(SenderSettingDrafts.emptyWithDefaults(senderType))
            receiveCode = true
            receiveNonCode = true
            receiveAppNotify = true
            receiveCallNotify = false
        }
        isLoaded = true
    }

    fun buildSender(status: Int): Sender {
        validateDraft(draft, status)?.let { message ->
            throw IllegalArgumentException(message)
        }
        val jsonSetting = draft.normalizedStructuredFields().toJson()
        return currentSender?.copy(
            name = name,
            jsonSetting = jsonSetting,
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date(),
        ) ?: Sender(
            id = 0,
            type = senderType,
            name = name,
            jsonSetting = jsonSetting,
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date(),
        )
    }

    fun save(status: Int, onSaved: () -> Unit) {
        coroutineScope.launch {
            validateDraft(draft, status)?.let { message ->
                showMessage(message)
                return@launch
            }
            runCatching { viewModel.saveSenderSync(buildSender(status)) }
                .onSuccess { onSaved() }
                .onFailure { error ->
                    val messageRes = if (status == 0) {
                        R.string.sender_form_draft_save_failed
                    } else {
                        R.string.sender_form_save_failed
                    }
                    showMessage(context.getString(messageRes, error.message.orEmpty()))
                }
        }
    }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        DraftExitDialog(
            onSaveDraft = {
                save(status = 0) {
                    showMessage(context.getString(R.string.sender_form_draft_saved))
                    showExitDialog = false
                    onBack()
                }
            },
            onDiscard = {
                showExitDialog = false
                onBack()
            },
            onCancel = { showExitDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        context.getString(
                            if (senderId == 0L) R.string.sender_form_create_title else R.string.sender_form_edit_title,
                            getSenderTypeName(context, senderType),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            save(status = 1) {
                                showMessage(context.getString(R.string.sender_form_save_success))
                                onBack()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        if (!isLoaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.sender_form_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            fields.forEach { spec ->
                SchemaSenderField(
                    spec = spec,
                    metadata = SenderSettingSchemas.fieldsFor(senderType).single { it.name == spec.name },
                    draft = draft,
                    onDraftChange = { draft = normalizeDraft(it) },
                )
            }
            extraContent(draft) { nextDraft -> draft = normalizeDraft(nextDraft) }
            Spacer(modifier = Modifier.height(8.dp))
            ForwardToggleSection(
                receiveCode = receiveCode,
                onReceiveCodeChange = { receiveCode = it },
                receiveNonCode = receiveNonCode,
                onReceiveNonCodeChange = { receiveNonCode = it },
                receiveAppNotify = receiveAppNotify,
                onReceiveAppNotifyChange = { receiveAppNotify = it },
                receiveCallNotify = receiveCallNotify,
                onReceiveCallNotifyChange = { receiveCallNotify = it },
                activeSchedule = activeSchedule,
                onActiveScheduleChange = { activeScheduleEntry?.onChange(it) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SenderTestActionRow(
                channel = channel,
                viewModel = viewModel,
                senderType = senderType,
            ) {
                buildSender(status = 1)
            }
        }
    }
}

@Composable
private fun SchemaSenderField(
    spec: SchemaSenderFormFieldSpec,
    metadata: SenderSettingFieldMetadata,
    draft: SenderSettingDraft,
    onDraftChange: (SenderSettingDraft) -> Unit,
) {
    val value = when (metadata.type) {
        SenderSettingFieldType.STRING_MAP -> {
            if (draft.element(spec.name) is JsonObject) {
                val mapValue = draft.stringMap(spec.name)
                if (mapValue.isEmpty()) "" else SenderSettingJson.encodeStringMap(mapValue)
            } else {
                draft.string(spec.name)
            }
        }
        SenderSettingFieldType.EMAIL_RECIPIENTS -> {
            (draft.element(spec.name) as? JsonObject)?.toString() ?: draft.string(spec.name)
        }
        else -> draft.string(spec.name)
    }
    if (metadata.options.isNotEmpty()) {
        SingleChoiceSegmentedSelector(
            options = metadata.options.map { option ->
                SegmentedOption(
                    value = option.value,
                    label = spec.optionLabelRes[option.value]?.let { stringResource(it) } ?: option.value,
                )
            },
            selected = value,
            onSelect = { onDraftChange(draft.withString(spec.name, it)) },
        )
        return
    }

    if (metadata.type == SenderSettingFieldType.BOOLEAN) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(spec.labelRes))
            Switch(
                checked = draft.boolean(spec.name),
                onCheckedChange = { onDraftChange(draft.withBoolean(spec.name, it)) },
            )
        }
        return
    }

    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            onDraftChange(
                when (metadata.type) {
                    SenderSettingFieldType.INTEGER -> draft.withInt(spec.name, next.toIntOrNull() ?: 0)
                    else -> draft.withString(spec.name, next)
                },
            )
        },
        label = { Text(stringResource(spec.labelRes)) },
        placeholder = spec.placeholderRes?.let { placeholderRes ->
            { Text(stringResource(placeholderRes)) }
        },
        supportingText = spec.supportingTextRes?.let { supportingTextRes ->
            { Text(stringResource(supportingTextRes)) }
        },
        minLines = spec.minLines,
        keyboardOptions = if (metadata.type == SenderSettingFieldType.INTEGER) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
