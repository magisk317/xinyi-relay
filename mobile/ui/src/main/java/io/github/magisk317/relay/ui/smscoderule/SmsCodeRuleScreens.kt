@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.smscoderule

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.android.sms.SmsCodeUtils as RelaySmsCodeUtils
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.smscode.domain.model.BuiltinSmsCodeRuleSpec
import io.github.magisk317.smscode.domain.model.BuiltinSmsCodeRules
import io.github.magisk317.smscode.runtime.common.rules.OfficialSmsCodeRule
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSnapshot
import org.koin.compose.koinInject
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BUILTIN_RULE_EDITOR_ID_ALPHANUMERIC = -101L
private const val BUILTIN_RULE_EDITOR_ID_DIGITS = -102L

private fun builtinRuleEditorId(ruleId: String): Long? = when (ruleId) {
    BuiltinSmsCodeRules.RULE_ID_ALPHANUMERIC -> BUILTIN_RULE_EDITOR_ID_ALPHANUMERIC
    BuiltinSmsCodeRules.RULE_ID_DIGITS -> BUILTIN_RULE_EDITOR_ID_DIGITS
    else -> null
}

private fun builtinRuleByEditorId(ruleId: Long): BuiltinSmsCodeRuleSpec? = when (ruleId) {
    BUILTIN_RULE_EDITOR_ID_ALPHANUMERIC ->
        BuiltinSmsCodeRules.all.firstOrNull { it.id == BuiltinSmsCodeRules.RULE_ID_ALPHANUMERIC }

    BUILTIN_RULE_EDITOR_ID_DIGITS ->
        BuiltinSmsCodeRules.all.firstOrNull { it.id == BuiltinSmsCodeRules.RULE_ID_DIGITS }

    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsCodeRuleListScreen(
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repository: AppConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val removedLabel = stringResource(id = R.string.removed)
    val emptyPrompt = stringResource(id = R.string.rule_list_empty_prompt)
    val officialTitle = stringResource(id = R.string.official_code_rules_title)
    val officialEmptyPrompt = stringResource(id = R.string.official_code_rules_empty_prompt)
    val officialRefreshSuccess = stringResource(id = R.string.official_code_rules_refresh_success)
    val officialRefreshFailed = stringResource(id = R.string.official_code_rules_refresh_failed)
    val userTitle = stringResource(id = R.string.user_code_rules_title)
    val userSummary = stringResource(id = R.string.user_code_rules_summary)
    val rules by repository.observeSmsCodeRulesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var officialSnapshot by remember { mutableStateOf<SmsCodeRuleCatalogSnapshot?>(null) }
    var officialLoading by remember { mutableStateOf(false) }
    val officialRules = officialSnapshot?.rules.orEmpty()
    val officialSummary = stringResource(
        id = R.string.official_code_rules_summary,
        officialRules.size,
        officialSnapshot?.sourceKind?.name?.lowercase().orEmpty().ifBlank { "-" },
    )

    fun loadOfficialRules(refresh: Boolean) {
        if (officialLoading) return
        scope.launch {
            officialLoading = true
            val result = withContext(Dispatchers.IO) {
                if (refresh) {
                    RelaySmsCodeUtils.refreshOfficialRules(context)
                } else {
                    null
                }
            }
            officialSnapshot = withContext(Dispatchers.IO) {
                result?.snapshot ?: RelaySmsCodeUtils.loadOfficialRuleSnapshot(context)
            }
            officialLoading = false
            if (refresh) {
                snackbarHostState.showLatestSnackbar(
                    if (result?.success == true) officialRefreshSuccess else officialRefreshFailed,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        loadOfficialRules(refresh = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.rule_list)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !officialLoading,
                        onClick = { loadOfficialRules(refresh = true) },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.action_refresh))
                    }
                },
            )
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
                Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.create_rule))
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 144.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RuleSectionHeader(
                    title = officialTitle,
                    summary = officialSummary,
                )
            }
            if (officialRules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = officialEmptyPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(officialRules, key = { index, rule -> "${rule.sourcePath}:${rule.id}:$index" }) { index, rule ->
                    OfficialSmsCodeRuleCard(
                        rule = rule,
                        ordinal = index + 1,
                    )
                }
            }
            item {
                RuleSectionHeader(
                    title = userTitle,
                    summary = userSummary,
                )
            }
            if (rules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    SmsCodeRuleCard(
                        rule = rule as SmsCodeRule,
                        ordinal = index + 1,
                        onEdit = { onEditClick(rule.id) },
                        onDelete = {
                            scope.launch {
                                repository.deleteSmsCodeRule(rule)
                                repository.checkpoint()
                                snackbarHostState.showLatestSnackbar("$removedLabel: ${rule.codeKeyword}")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSectionHeader(
    title: String,
    summary: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OfficialSmsCodeRuleCard(
    rule: OfficialSmsCodeRule,
    ordinal: Int,
) {
    val officialBadge = stringResource(id = R.string.official_rule_badge_format, ordinal)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = rule.company?.takeIf { it.isNotBlank() } ?: rule.setName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = officialBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = rule.codeKeyword,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = rule.codeRegex,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmsCodeRuleCard(
    rule: SmsCodeRule,
    ordinal: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val userBadge = stringResource(id = R.string.user_rule_badge_format, ordinal)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rule.company?.takeIf { it.isNotBlank() }?.let { company ->
                Text(
                    text = company,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = userBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = rule.codeKeyword,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = rule.codeRegex,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(id = R.string.edit))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(id = R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsCodeRuleEditorScreen(
    ruleId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val repository: AppConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val builtinRule = remember(ruleId) { builtinRuleByEditorId(ruleId) }
    val isBuiltinRule = builtinRule != null
    val loadFailedText = stringResource(id = R.string.load_failed)
    val saveFailedText = stringResource(id = R.string.save_failed)
    val keywordEmptyText = stringResource(id = R.string.rule_keyword_empty_hint)
    val regexEmptyText = stringResource(id = R.string.rule_code_regex_empty_hint)
    val duplicateText = stringResource(id = R.string.rule_duplicated_prompt)
    val confirmLabel = stringResource(id = R.string.confirm)
    val copyLabel = stringResource(id = R.string.action_copy)
    val rulesSummary = stringResource(id = R.string.pref_code_rules_summary)
    val builtinSummary = stringResource(id = R.string.builtin_code_rules_summary)
    val builtinKeywordSetting = stringResource(id = R.string.builtin_rule_keyword_setting)
    val builtinTitle = when (builtinRule?.id) {
        BuiltinSmsCodeRules.RULE_ID_ALPHANUMERIC -> stringResource(id = R.string.builtin_rule_alphanumeric_title)
        BuiltinSmsCodeRules.RULE_ID_DIGITS -> stringResource(id = R.string.builtin_rule_digits_title)
        else -> stringResource(id = R.string.builtin_rule_badge)
    }
    val companyLabel = stringResource(id = R.string.rule_company_hint)
    val keywordLabel = stringResource(id = R.string.rule_keyword_hint)
    val regexLabel = stringResource(id = R.string.rule_code_regex_hint)
    val testGuidance = stringResource(
        id = R.string.rule_test_guidance,
        stringResource(id = R.string.pref_relay_test_title),
    )
    var company by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var regex by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(ruleId != 0L) }

    LaunchedEffect(ruleId) {
        if (builtinRule != null) {
            company = ""
            keyword = builtinKeywordSetting
            regex = builtinRule.codeRegex
            loading = false
            return@LaunchedEffect
        }
        if (ruleId == 0L) {
            loading = false
            return@LaunchedEffect
        }
        val rule = repository.getSmsCodeRuleById(ruleId)
        if (rule == null) {
            snackbarHostState.showLatestSnackbar(loadFailedText)
            onBack()
            return@LaunchedEffect
        }
        company = rule.company.orEmpty()
        keyword = rule.codeKeyword
        regex = rule.codeRegex
        loading = false
    }

    fun saveRule() {
        if (isBuiltinRule) return
        scope.launch {
            val normalizedCompany = company.trim().ifBlank { null }
            val normalizedKeyword = keyword.trim()
            val normalizedRegex = regex.trim()
            when {
                normalizedKeyword.isEmpty() -> {
                    snackbarHostState.showLatestSnackbar(keywordEmptyText)
                    return@launch
                }
                normalizedRegex.isEmpty() -> {
                    snackbarHostState.showLatestSnackbar(regexEmptyText)
                    return@launch
                }
            }
            runCatching {
                Pattern.compile(normalizedRegex)
            }.onFailure {
                snackbarHostState.showLatestSnackbar(it.message ?: saveFailedText)
                return@launch
            }
            val duplicated = repository.getAllSmsCodeRules().firstOrNull { existing ->
                existing.id != ruleId &&
                    existing.company.orEmpty().trim() == normalizedCompany.orEmpty() &&
                    existing.codeKeyword.trim() == normalizedKeyword &&
                    existing.codeRegex.trim() == normalizedRegex
            }
            if (duplicated != null) {
                snackbarHostState.showLatestSnackbar(duplicateText)
                return@launch
            }
            repository.upsertSmsCodeRule(
                SmsCodeRule(
                    company = normalizedCompany,
                    codeKeyword = normalizedKeyword,
                    codeRegex = normalizedRegex,
                    id = ruleId,
                ),
            )
            repository.checkpoint()
            onBack()
        }
    }

    fun copyField(label: String, value: String) {
        if (value.isBlank()) return
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            snackbarHostState.showLatestSnackbar(context.getString(R.string.prompt_field_copied, label))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isBuiltinRule) builtinTitle else {
                            stringResource(
                                id = if (ruleId == 0L) R.string.create_rule else R.string.edit_rule,
                            )
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!isBuiltinRule) {
                        TextButton(
                            enabled = !loading,
                            onClick = ::saveRule,
                        ) {
                            Text(confirmLabel)
                        }
                    }
                },
            )
        },
        snackbarHost = {
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(companyLabel) },
                placeholder = { Text(stringResource(id = R.string.rule_company_placeholder)) },
                supportingText = { Text(if (isBuiltinRule) builtinSummary else rulesSummary) },
                readOnly = isBuiltinRule,
                enabled = !loading,
                trailingIcon = if (isBuiltinRule && company.isNotBlank()) {
                    {
                        IconButton(onClick = { copyField(companyLabel, company) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(keywordLabel) },
                readOnly = isBuiltinRule,
                enabled = !loading,
                trailingIcon = if (isBuiltinRule && keyword.isNotBlank()) {
                    {
                        IconButton(onClick = { copyField(keywordLabel, keyword) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )
            OutlinedTextField(
                value = regex,
                onValueChange = { regex = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(regexLabel) },
                readOnly = isBuiltinRule,
                enabled = !loading,
                trailingIcon = if (isBuiltinRule && regex.isNotBlank()) {
                    {
                        IconButton(onClick = { copyField(regexLabel, regex) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                        }
                    }
                } else {
                    null
                },
                minLines = 3,
                singleLine = false,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = testGuidance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
