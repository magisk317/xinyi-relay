package com.github.magisk317.smscode.ui.rule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.magisk317.smscode.forwarder.entity.Rule
import com.github.magisk317.smscode.forwarder.entity.Sender
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleConfigScreen(
    ruleId: Long,
    onBack: () -> Unit,
    viewModel: RuleViewModel = viewModel()
) {
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
                title = { Text(if (ruleId == 0L) "新建规则" else "编辑规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                label = { Text("规则名称（备注）") },
                modifier = Modifier.fillMaxWidth()
            )

            // 目标通道选择
            Text("目标发送通道", style = MaterialTheme.typography.labelMedium)
            SenderDropdown(
                senders = senders,
                selectedId = selectedSenderId,
                onSelect = { selectedSenderId = it }
            )

            // 匹配字段
            Text("匹配字段", style = MaterialTheme.typography.labelMedium)
            SegmentedPicker(
                options = listOf("transpond_all" to "全部转发", "content" to "内容匹配", "sender" to "发件人匹配"),
                selected = filed,
                onSelect = { filed = it }
            )

            // 仅在内容/发件人匹配时显示
            if (filed != "transpond_all") {
                // 匹配方式
                Text("匹配方式", style = MaterialTheme.typography.labelMedium)
                SegmentedPicker(
                    options = listOf(
                        "contains" to "包含",
                        "is" to "完全匹配",
                        "regex" to "正则"
                    ),
                    selected = check,
                    onSelect = { check = it }
                )

                // 匹配值
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(if (filed == "content") "关键词 / 正则表达式" else "发件号码") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 消息模板（选填）
            OutlinedTextField(
                value = smsTemplate,
                onValueChange = { smsTemplate = it },
                label = { Text("自定义消息模板（留空使用默认）") },
                placeholder = { Text("[来源] {from}\\n{content}") },
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
                Text(if (ruleId == 0L) "保存规则" else "更新规则")
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
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = senders.find { it.id == selectedId }?.name ?: "请选择通道"

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
                    text = { Text("${sender.name} (${getSenderTypeShort(sender.type)})") },
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Suppress("MagicNumber")
private fun getSenderTypeShort(type: Int) = when (type) {
    1 -> "钉钉"
    4 -> "Webhook"
    6 -> "PushPlus"
    12 -> "TG"
    else -> "??"
}
