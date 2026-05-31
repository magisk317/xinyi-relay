@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.rule

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.ui.sender.displayName
import io.github.magisk317.relay.core.R
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    senderId: Long = 0L,
    viewModel: RuleViewModel = koinViewModel(),
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rules by viewModel.ruleList.collectAsStateWithLifecycle()
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val senderName = if (senderId != 0L) senders.find { it.id == senderId }?.displayName(context) else null

    LaunchedEffect(senderId) {
        viewModel.loadRules(senderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    senderName?.let { context.getString(R.string.rule_list_title_named, it) }
                        ?: stringResource(R.string.rule_list_title),
                )
            })
        },
        snackbarHost = {
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
                onClick = onAddClick,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rule_add_rule_content_description))
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
                Text(
                    stringResource(R.string.rule_list_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    val senderName =
                        senders.find { it.id == rule.senderId }?.displayName(context)
                            ?: stringResource(R.string.rule_unknown_sender)
                    RuleCard(
                        rule = rule,
                        senderName = senderName,
                        onEdit = { onEditClick(rule.id) },
                        onToggle = {
                            viewModel.toggleRuleStatus(rule, it)
                            scope.launch {
                                snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar))
                            }
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
                    text = rule.title.ifEmpty { stringResource(R.string.rule_unnamed) },
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = rule.status == 1,
                    onCheckedChange = { onToggle(it) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val matchDesc = when (rule.filed) {
                "transpond_all" -> stringResource(R.string.rule_match_all_sms)
                "content" -> stringResource(R.string.rule_match_content, rule.value)
                "sender" -> stringResource(R.string.rule_match_sender, rule.value)
                else -> "${rule.filed} ${rule.check} ${rule.value}"
            }
            Text(
                text = matchDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.rule_sender_channel_format, senderName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
