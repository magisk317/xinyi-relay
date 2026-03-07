package io.github.magisk317.relay.ui.rule

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.relay.forwarder.entity.Rule
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.core.R
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    senderId: Long = 0L,
    viewModel: RuleViewModel = viewModel(),
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val rules by viewModel.ruleList.collectAsStateWithLifecycle()
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val senderName = if (senderId != 0L) senders.find { it.id == senderId }?.name else null

    LaunchedEffect(senderId) {
        viewModel.loadRules(senderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(if (senderName != null) "$senderName 的规则" else "转发规则")
            })
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
                onClick = onAddClick,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加规则")
            }
        }
    ) { paddingValues ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无规则，点击右下角添加\n规则决定哪些短信发到哪个通道",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    val senderName = senders.find { it.id == rule.senderId }?.name ?: "未知通道"
                    RuleCard(
                        rule = rule,
                        senderName = senderName,
                        onEdit = { onEditClick(rule.id) },
                        onToggle = {
                            viewModel.toggleRuleStatus(rule, it)
                            Toast.makeText(
                                context,
                                context.getString(R.string.pref_sync_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
            }
        }
    }
}

@Composable
fun RuleCard(
    rule: Rule,
    senderName: String,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.title.ifEmpty { "未命名规则" },
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = rule.status == 1,
                    onCheckedChange = { onToggle(it) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val matchDesc = when (rule.filed) {
                "transpond_all" -> "匹配全部短信"
                "content" -> "内容包含: ${rule.value}"
                "sender" -> "发件人: ${rule.value}"
                else -> "${rule.filed} ${rule.check} ${rule.value}"
            }
            Text(
                text = matchDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "→ 通道: $senderName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
