package io.github.magisk317.relay.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.domain.model.Sender
import io.github.magisk317.relay.ui.sender.displayName
import io.github.magisk317.relay.core.R
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNotifySenderBindingScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val senders by viewModel.notifySenderListFlow.collectAsStateWithLifecycle()
    val selectedFlow = remember(packageName) { viewModel.appNotifyBoundSenderIdsFlow(packageName) }
    val deniedBySenderFlow = remember(packageName) { viewModel.senderDenyingPackageIdsFlow(packageName) }
    val selectedSenderIds by selectedFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val deniedBySenderIds by deniedBySenderFlow.collectAsStateWithLifecycle(initialValue = emptySet())

    var draftSelectedIds by remember(packageName) { mutableStateOf(emptySet<Long>()) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(selectedSenderIds) {
        draftSelectedIds = selectedSenderIds
    }

    val filteredSenders = remember(senders, searchText) {
        val query = searchText.trim().lowercase()
        if (query.isEmpty()) {
            senders
        } else {
            senders.filter { sender ->
                senderDisplayName(sender, context).lowercase().contains(query) ||
                    sender.id.toString().contains(query)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_notify_channel_config_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveAppNotifySenderBindings(
                                packageName = packageName,
                                senderIds = draftSelectedIds,
                            )
                            onBack()
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                )
            }
            item(key = "tip") {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (draftSelectedIds.isEmpty()) {
                                stringResource(R.string.app_notify_channel_global_summary)
                            } else {
                                stringResource(
                                    R.string.app_notify_channel_bound_count,
                                    draftSelectedIds.size,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.app_notify_channel_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RowEnd {
                            TextButton(onClick = { draftSelectedIds = emptySet() }) {
                                Text(stringResource(R.string.sender_notify_scope_clear_whitelist))
                            }
                        }
                    }
                }
            }
            items(filteredSenders, key = { it.id }) { sender ->
                val checked = sender.id in draftSelectedIds
                val denied = sender.id in deniedBySenderIds
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.ListItem(
                            headlineContent = {
                                Text(
                                    text = senderDisplayName(sender, context),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(
                                        R.string.sender_notify_scope_sender_id,
                                        sender.id,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        draftSelectedIds = if (isChecked) {
                                            draftSelectedIds + sender.id
                                        } else {
                                            draftSelectedIds - sender.id
                                        }
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (checked && denied) {
                            Text(
                                text = stringResource(R.string.app_notify_channel_deny_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (filteredSenders.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.list_empty_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowEnd(
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

private fun senderDisplayName(sender: Sender, context: android.content.Context): String {
    return sender.displayName(context)
}
