package io.github.magisk317.relay.ui.sender

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.AppIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.text.Collator
import java.util.Locale

private data class InstalledAppOption(
    val packageName: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderNotifyScopeScreen(
    senderId: Long,
    onBack: () -> Unit,
    viewModel: SenderViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val senderName = senders.firstOrNull { it.id == senderId }?.name.orEmpty()

    val allowFlow = remember(senderId) { viewModel.senderAllowPackagesFlow(senderId) }
    val denyFlow = remember(senderId) { viewModel.senderDenyPackagesFlow(senderId) }
    val allowPackages by allowFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val denyPackages by denyFlow.collectAsStateWithLifecycle(initialValue = emptySet())

    var draftAllow by remember(senderId) { mutableStateOf(emptySet<String>()) }
    var draftDeny by remember(senderId) { mutableStateOf(emptySet<String>()) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(allowPackages) {
        draftAllow = allowPackages
    }
    LaunchedEffect(denyPackages) {
        draftDeny = denyPackages
    }

    val installedApps by produceState<List<InstalledAppOption>>(initialValue = emptyList(), senderId) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val collator = Collator.getInstance(Locale.getDefault())
            pm.getInstalledApplications(PackageManager.MATCH_ALL)
                .asSequence()
                .map { appInfo ->
                    val label = runCatching {
                        pm.getApplicationLabel(appInfo).toString()
                    }.getOrDefault(appInfo.packageName)
                    InstalledAppOption(
                        packageName = appInfo.packageName,
                        label = label,
                    )
                }
                .sortedWith(compareBy(collator) { it.label.lowercase() })
                .toList()
        }
    }

    val filteredApps = remember(installedApps, searchText) {
        val query = searchText.trim().lowercase()
        if (query.isEmpty()) {
            installedApps
        } else {
            installedApps.filter { app ->
                app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            }
        }
    }

    val conflicts = remember(draftAllow, draftDeny) { draftAllow.intersect(draftDeny) }
    val canSave = conflicts.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (senderName.isNotBlank()) {
                            stringResource(R.string.sender_notify_scope_title_named, senderName)
                        } else {
                            stringResource(R.string.sender_notify_scope_title)
                        },
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
                            scope.launch {
                                val result = viewModel.saveSenderNotifyScopeSync(
                                    senderId = senderId,
                                    allowPackages = draftAllow,
                                    denyPackages = draftDeny,
                                )
                                if (result.success) {
                                    onBack()
                                } else {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.sender_notify_scope_conflict_count,
                                            result.conflictPackages.size,
                                        ),
                                    )
                                }
                            }
                        },
                        enabled = canSave,
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
        snackbarHost = {
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
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
            item(key = "summary") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.sender_notify_scope_summary,
                                draftAllow.size,
                                draftDeny.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (conflicts.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.sender_notify_scope_conflict_count,
                                    conflicts.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { draftAllow = emptySet() }) {
                                Text(stringResource(R.string.sender_notify_scope_clear_whitelist))
                            }
                            TextButton(onClick = { draftDeny = emptySet() }) {
                                Text(stringResource(R.string.sender_notify_scope_clear_blacklist))
                            }
                        }
                    }
                }
            }
            items(filteredApps, key = { it.packageName }) { app ->
                val isAllow = app.packageName in draftAllow
                val isDeny = app.packageName in draftDeny
                val isConflict = isAllow && isDeny

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppIconImage(
                                packageName = app.packageName,
                                contentDescription = null,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isAllow,
                                    onCheckedChange = { checked ->
                                        draftAllow = if (checked) {
                                            draftAllow + app.packageName
                                        } else {
                                            draftAllow - app.packageName
                                        }
                                    },
                                )
                                Text(stringResource(R.string.sender_notify_scope_whitelist))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isDeny,
                                    onCheckedChange = { checked ->
                                        draftDeny = if (checked) {
                                            draftDeny + app.packageName
                                        } else {
                                            draftDeny - app.packageName
                                        }
                                    },
                                )
                                Text(stringResource(R.string.sender_notify_scope_blacklist))
                            }
                        }
                        if (isConflict) {
                            Text(
                                text = stringResource(R.string.sender_notify_scope_conflict_single),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (filteredApps.isEmpty()) {
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
