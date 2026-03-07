package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.xinyi.relay.core.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    onInterceptClick: () -> Unit,
    onForwardClick: () -> Unit,
    onGlobalForwardFilterClick: () -> Unit,
    onWebUiConfigClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.tab_advanced)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdvancedEntryCard(
                title = stringResource(id = R.string.tab_intercept),
                subtitle = stringResource(id = R.string.pref_enable_sms_blacklist_summary),
                icon = { Icon(Icons.Default.GppGood, contentDescription = null) },
                onClick = onInterceptClick,
            )
            AdvancedEntryCard(
                title = stringResource(id = R.string.tab_senders),
                subtitle = stringResource(id = R.string.pref_enable_forward_summary),
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                onClick = onForwardClick,
            )
            AdvancedEntryCard(
                title = "关键词过滤（全局）",
                subtitle = "配置短信/应用通知的全局黑白名单规则",
                icon = { Icon(Icons.Default.FilterAlt, contentDescription = null) },
                onClick = onGlobalForwardFilterClick,
            )
            AdvancedEntryCard(
                title = stringResource(id = R.string.pref_webui_config_title),
                subtitle = stringResource(id = R.string.pref_webui_config_summary_short),
                icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                onClick = onWebUiConfigClick,
            )
        }
    }
}

@Composable
private fun AdvancedEntryCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().let { base ->
            if (onClick != null) {
                base.clickable(onClick = onClick)
            } else {
                base
            }
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    trailingContent?.invoke()
                }
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showChevron) {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}
