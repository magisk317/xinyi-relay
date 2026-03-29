package io.github.magisk317.relay.ui.rule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.domain.model.Rule
import io.github.magisk317.relay.domain.model.Sender
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.sender.displayName
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleConfigScreen(
    ruleId: Long,
    onBack: () -> Unit,
    viewModel: RuleViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoaded by remember { mutableStateOf(false) }

    // Fields
    var title by remember { mutableStateOf("") }
    var filed by remember { mutableStateOf("transpond_all") }  // transpond_all | content | sender
    var check by remember { mutableStateOf("contains") }       // contains | is | regex
    var value by remember { mutableStateOf("") }
    var selectedSenderId by remember { mutableStateOf(0L) }
    var smsTemplate by remember { mutableStateOf("") }

    val senders by viewModel.senderList.collectAsStateWithLifecycle()

    LaunchedEffect(ruleId) {
        if (ruleId != 0L) {
            val rule = viewModel.getRule(ruleId)
            rule?.let {
                title = it.title
                filed = it.filed
                check = it.check
                value = it.value
                selectedSenderId = it.senderId
                smsTemplate = it.smsTemplate
            }
        }
        isLoaded = true
    }

    if (!isLoaded) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (ruleId == 0L) R.string.create_rule else R.string.edit_rule)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 规则备注
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.rule_config_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            // 目标通道选择
            Text(stringResource(R.string.rule_config_sender_target_label), style = MaterialTheme.typography.labelMedium)
            SenderDropdown(
                senders = senders,
                selectedId = selectedSenderId,
                context = context,
                onSelect = { selectedSenderId = it }
            )

            // 匹配字段
            Text(stringResource(R.string.rule_config_match_field_label), style = MaterialTheme.typography.labelMedium)
            SegmentedPicker(
                options = listOf(
                    "transpond_all" to stringResource(R.string.rule_config_match_all),
                    "content" to stringResource(R.string.rule_config_match_content),
                    "sender" to stringResource(R.string.rule_config_match_sender),
                ),
                selected = filed,
                onSelect = { filed = it }
            )

            // 仅在内容/发件人匹配时显示
            if (filed != "transpond_all") {
                // 匹配方式
                Text(stringResource(R.string.rule_config_match_mode_label), style = MaterialTheme.typography.labelMedium)
                SegmentedPicker(
                    options = listOf(
                        "contains" to stringResource(R.string.rule_config_match_contains),
                        "is" to stringResource(R.string.rule_config_match_exact),
                        "regex" to stringResource(R.string.rule_config_match_regex),
                    ),
                    selected = check,
                    onSelect = { check = it }
                )

                // 匹配值
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            stringResource(
                                if (filed == "content") R.string.rule_config_content_value_label
                                else R.string.rule_config_sender_value_label,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 消息模板（选填）
            OutlinedTextField(
                value = smsTemplate,
                onValueChange = { smsTemplate = it },
                label = { Text(stringResource(R.string.rule_config_template_label)) },
                placeholder = { Text(stringResource(R.string.rule_config_template_placeholder)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val rule = Rule(
                        id = ruleId,
                        type = "sms",
                        filed = filed,
                        check = check,
                        value = value,
                        senderId = selectedSenderId,
                        title = title,
                        smsTemplate = smsTemplate,
                        senderList = emptyList(),
                        time = Date()
                    )
                    coroutineScope.launch {
                        viewModel.saveRuleSync(rule)
                        onBack()
                    }
                },
                enabled = selectedSenderId != 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (ruleId == 0L) R.string.rule_config_save_new else R.string.rule_config_save_update))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderDropdown(
    senders: List<Sender>,
    selectedId: Long,
    context: android.content.Context,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = senders.find { it.id == selectedId }?.displayName(context)
        ?: context.getString(R.string.rule_config_sender_placeholder)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            senders.forEach { sender ->
                DropdownMenuItem(
                    text = { Text(sender.displayName(context)) },
                    onClick = {
                        onSelect(sender.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SegmentedPicker(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    SingleChoiceSegmentedSelector(
        options = options.map { (key, label) -> SegmentedOption(key, label) },
        selected = selected,
        onSelect = onSelect,
    )
}
