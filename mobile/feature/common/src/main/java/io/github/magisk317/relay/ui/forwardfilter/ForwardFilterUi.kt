package io.github.magisk317.relay.ui.forwardfilter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.filter.ForwardFilterConst
import io.github.magisk317.relay.ui.common.CenteredChipText
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.preference.AppSwitch
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.surface.rememberSaveableTextFieldState
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

data class ForwardFilterEditorState(
    val id: Long,
    val policy: String,
    val matchMode: String,
    val pattern: String,
    val enabled: Boolean,
    val channelId: String = "",
)

fun ForwardFilterRule.toEditorState(channelId: String = ""): ForwardFilterEditorState =
    ForwardFilterEditorState(
        id = id,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = enabled == 1,
        channelId = channelId,
    )

@Composable
fun ForwardFilterScreenScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    selectedMsgType: String,
    onSelectMsgType: (String) -> Unit,
    rules: List<ForwardFilterRule>,
    onAdd: () -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onEdit: (ForwardFilterRule) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val forwardFilterBody: @Composable (PaddingValues) -> Unit = { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        ForwardFilterMsgTypeTabs(
            selectedMsgType = selectedMsgType,
            onSelect = onSelectMsgType,
        )
        ForwardFilterRuleList(
            rules = rules,
            emptyText = stringResource(id = R.string.forward_filter_empty),
            onToggleEnabled = onToggleEnabled,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> ForwardFilterScreenScaffoldMiuix(
            title = title,
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            onAdd = onAdd,
            body = forwardFilterBody,
        )

        UiKitStyle.Expressive -> ForwardFilterScreenScaffoldMaterial(
            title = title,
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            onAdd = onAdd,
            body = forwardFilterBody,
        )
    }
}

@Composable
fun ForwardFilterMsgTypeTabs(
    selectedMsgType: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedSelector(
        options = listOf(
            SegmentedOption(ForwardFilterConst.MSG_TYPE_SMS, stringResource(id = R.string.forward_filter_msg_type_sms)),
            SegmentedOption(ForwardFilterConst.MSG_TYPE_APP_NOTIFY, stringResource(id = R.string.forward_filter_msg_type_app_notify)),
            SegmentedOption(ForwardFilterConst.MSG_TYPE_CALL_NOTIFY, stringResource(id = R.string.forward_filter_msg_type_call_notify)),
        ),
        selected = selectedMsgType,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
fun ForwardFilterRuleList(
    rules: List<ForwardFilterRule>,
    emptyText: String,
    channelIdLabelProvider: (ForwardFilterRule) -> String? = { null },
    onToggleEnabled: (Long, Boolean) -> Unit,
    onEdit: (ForwardFilterRule) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (rules.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    Column {
        rules.forEach { rule ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val policyLabel =
                    if (rule.policy == ForwardFilterConst.POLICY_ALLOW) {
                        stringResource(id = R.string.forward_filter_rule_allow)
                    } else {
                        stringResource(id = R.string.forward_filter_rule_deny)
                    }
                val modeLabel =
                    if (rule.matchMode == ForwardFilterConst.MATCH_REGEX) {
                        stringResource(id = R.string.forward_filter_rule_regex)
                    } else {
                        stringResource(id = R.string.forward_filter_rule_contains)
                    }
                Text(
                    text = "[$policyLabel][$modeLabel] ${rule.pattern}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                channelIdLabelProvider(rule)?.takeIf { it.isNotBlank() }?.let { channelId ->
                    Text(
                        text = stringResource(id = R.string.forward_filter_rule_channel_id, channelId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rule.enabled == 1,
                            onCheckedChange = { onToggleEnabled(rule.id, it) },
                        )
                        Text(
                            text = if (rule.enabled == 1) {
                                stringResource(id = R.string.forward_filter_rule_enabled)
                            } else {
                                stringResource(id = R.string.forward_filter_rule_disabled)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row {
                        IconButton(onClick = { onEdit(rule) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(id = R.string.forward_filter_action_edit),
                            )
                        }
                        IconButton(onClick = { onDelete(rule.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(id = R.string.action_delete),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForwardFilterRuleEditorDialog(
    title: String,
    initialPolicy: String,
    initialMatchMode: String,
    initialPattern: String,
    initialEnabled: Boolean,
    showChannelInput: Boolean,
    initialChannelId: String,
    channelCandidates: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (
        policy: String,
        matchMode: String,
        pattern: String,
        enabled: Boolean,
        channelId: String,
    ) -> Unit,
) {
    var policy by remember(initialPolicy) { mutableStateOf(initialPolicy) }
    var matchMode by remember(initialMatchMode) { mutableStateOf(initialMatchMode) }
    var enabled by remember(initialEnabled) { mutableStateOf(initialEnabled) }
    val patternState = rememberSaveableTextFieldState(initialPattern)
    val channelIdState = rememberSaveableTextFieldState(initialChannelId)

    val canSave = patternState.text.toString().trim().isNotEmpty() &&
        (!showChannelInput || channelIdState.text.toString().trim().isNotEmpty())

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppText(
                    text = stringResource(id = R.string.forward_filter_strategy),
                    role = AppTextRole.Footnote,
                )
                SingleChoiceSegmentedSelector(
                    options = listOf(
                        SegmentedOption(
                            ForwardFilterConst.POLICY_ALLOW,
                            stringResource(id = R.string.forward_filter_policy_allow),
                        ),
                        SegmentedOption(
                            ForwardFilterConst.POLICY_DENY,
                            stringResource(id = R.string.forward_filter_policy_deny),
                        ),
                    ),
                    selected = policy,
                    onSelect = { policy = it },
                )

                AppText(
                    text = stringResource(id = R.string.forward_filter_match_mode),
                    role = AppTextRole.Footnote,
                )
                SingleChoiceSegmentedSelector(
                    options = listOf(
                        SegmentedOption(
                            ForwardFilterConst.MATCH_CONTAINS,
                            stringResource(id = R.string.forward_filter_match_contains),
                        ),
                        SegmentedOption(
                            ForwardFilterConst.MATCH_REGEX,
                            stringResource(id = R.string.forward_filter_match_regex),
                        ),
                    ),
                    selected = matchMode,
                    onSelect = { matchMode = it },
                )

                AppTextField(
                    state = patternState,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(id = R.string.forward_filter_pattern_label),
                    supportingText = {
                        Text(
                            if (matchMode == ForwardFilterConst.MATCH_REGEX) {
                                stringResource(id = R.string.forward_filter_pattern_regex_hint)
                            } else {
                                stringResource(id = R.string.forward_filter_pattern_contains_hint)
                            },
                        )
                    },
                    singleLine = false,
                    minLines = 2,
                )

                if (showChannelInput) {
                    AppTextField(
                        state = channelIdState,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(id = R.string.forward_filter_channel_id_label),
                        singleLine = true,
                    )
                    if (channelCandidates.isNotEmpty()) {
                        AppText(
                            text = stringResource(id = R.string.forward_filter_channel_history),
                            role = AppTextRole.Footnote,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            channelCandidates.take(6).forEach { candidate ->
                                AssistChip(
                                    onClick = {
                                        channelIdState.edit {
                                            replace(0, length, candidate)
                                            selection = TextRange(candidate.length)
                                        }
                                    },
                                    label = { CenteredChipText(candidate) },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppText(text = stringResource(id = R.string.forward_filter_enabled))
                    AppSwitch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(id = R.string.save),
                enabled = canSave,
                onClick = {
                    onConfirm(
                        policy.trim(),
                        matchMode.trim(),
                        patternState.text.toString().trim(),
                        enabled,
                        channelIdState.text.toString().trim(),
                    )
                },
            )
        },
        dismissButton = {
            AppTextButton(text = stringResource(id = R.string.cancel), onClick = onDismiss)
        },
    )
}
