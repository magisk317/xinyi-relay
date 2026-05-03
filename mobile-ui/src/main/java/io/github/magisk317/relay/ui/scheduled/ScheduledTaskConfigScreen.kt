package io.github.magisk317.relay.ui.scheduled

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskConfigScreen(
    taskId: Long,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cron by remember { mutableStateOf("") }
    var mobiles by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var simSlot by remember { mutableStateOf("0") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == 0L) "新增定时任务" else "编辑定时任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("任务名称") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = cron,
                onValueChange = { cron = it },
                label = { Text("Cron 表达式 / 时间规则") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = simSlot,
                onValueChange = { simSlot = it },
                label = { Text("SIM 卡 (0/1/2)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = mobiles,
                onValueChange = { mobiles = it },
                label = { Text("目标号码") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("发送内容") },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 8.dp)
            )
        }
    }
}
