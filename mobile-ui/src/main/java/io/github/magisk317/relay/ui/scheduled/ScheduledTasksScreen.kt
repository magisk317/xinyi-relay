package io.github.magisk317.relay.ui.scheduled

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    onBack: () -> Unit,
    onNavigateToConfig: (Long) -> Unit
) {
    val tasks by flowOf(emptyList<ScheduledTaskEntity>()).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定时任务管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToConfig(0L) }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("暂无定时任务")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(tasks) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        onClick = { onNavigateToConfig(task.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = task.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Cron: \${task.cronExpression}", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "SIM: \${task.simSlot} | Content: \${task.content}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
