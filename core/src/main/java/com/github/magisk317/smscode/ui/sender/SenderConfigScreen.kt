package com.github.magisk317.smscode.ui.sender

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.forwarder.utils.SenderType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.magisk317.smscode.ui.sender.forms.*
import io.github.magisk317.xinyi.relay.core.BuildConfig
import kotlinx.coroutines.flow.flowOf

@Composable
fun SenderConfigScreen(
    senderId: Long,
    senderTypeArg: Int,
    onBack: (Boolean) -> Unit,
    onOpenSenderNotifyScope: (Long) -> Unit = {},
    onOpenSenderForwardFilter: (Long) -> Unit = {},
    viewModel: SenderViewModel = viewModel()
) {
    var type by remember { mutableStateOf(senderTypeArg) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(senderId) {
        // Reset per-screen save state to avoid carrying stale status across entries.
        viewModel.clearLastSavedStatus()
        if (senderId != 0L) {
            val sender = viewModel.getSender(senderId)
            if (sender != null) {
                type = sender.type
            }
        }
        isLoaded = true
    }

    val lastSavedStatus by viewModel.lastSavedStatus.collectAsStateWithLifecycle()
    val notifyScopeSummaryFlow = remember(senderId) {
        if (senderId > 0L) {
            viewModel.senderNotifyScopeSummaryFlow(senderId)
        } else {
            flowOf("")
        }
    }
    val notifyScopeSummary by notifyScopeSummaryFlow.collectAsStateWithLifecycle(initialValue = "")
    val forwardFilterSummaryFlow = remember(senderId) {
        if (senderId > 0L) {
            viewModel.senderForwardFilterSummaryFlow(senderId)
        } else {
            flowOf("")
        }
    }
    val forwardFilterSummary by forwardFilterSummaryFlow.collectAsStateWithLifecycle(initialValue = "")
    val notifyScopeEntry = remember(senderId, notifyScopeSummary, onOpenSenderNotifyScope) {
        if (senderId > 0L) {
            SenderNotifyScopeEntry(
                senderId = senderId,
                summary = notifyScopeSummary.ifBlank { "白名单0 / 黑名单0" },
                onClick = onOpenSenderNotifyScope,
            )
        } else {
            null
        }
    }
    val forwardFilterEntry = remember(senderId, forwardFilterSummary, onOpenSenderForwardFilter) {
        if (senderId > 0L) {
            SenderForwardFilterEntry(
                senderId = senderId,
                summary = forwardFilterSummary.ifBlank { "短信 白0/黑0 · 通知 白0/黑0" },
                onClick = onOpenSenderForwardFilter,
            )
        } else {
            null
        }
    }

    val handleBack: () -> Unit = {
        // Only reopen type chooser when creating a brand new sender and nothing was saved.
        // If user already saved draft/status, return to sender list directly.
        val reopenTypeDialog = senderId == 0L && lastSavedStatus == null
        viewModel.clearLastSavedStatus()
        onBack(reopenTypeDialog)
    }

    if (!isLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    CompositionLocalProvider(
        LocalSenderNotifyScopeEntry provides notifyScopeEntry,
        LocalSenderForwardFilterEntry provides forwardFilterEntry,
    ) {
        when (type) {
            SenderType.DINGTALK_GROUP_ROBOT -> DingtalkConfigForm(senderId, handleBack, viewModel)
            SenderType.EMAIL -> EmailConfigForm(senderId, handleBack, viewModel)
            SenderType.BARK -> BarkConfigForm(senderId, handleBack, viewModel)
            SenderType.WEBHOOK -> WebhookConfigForm(senderId, handleBack, viewModel)
            SenderType.WEWORK_ROBOT -> WeworkRobotConfigForm(senderId, handleBack, viewModel)
            SenderType.WEWORK_AGENT -> WeworkAgentConfigForm(senderId, handleBack, viewModel)
            SenderType.SERVERCHAN -> ServerchanConfigForm(senderId, handleBack, viewModel)
            SenderType.PUSHPLUS -> PushplusConfigForm(senderId, handleBack, viewModel)
            SenderType.TELEGRAM -> TelegramConfigForm(senderId, handleBack, viewModel)
            SenderType.SMS -> {
                if (BuildConfig.ENABLE_SMS_CHANNEL) {
                    SmsConfigForm(senderId, handleBack, viewModel)
                } else {
                    SmsChannelDisabledScreen(onBack = handleBack)
                }
            }
            SenderType.FEISHU -> FeishuConfigForm(senderId, handleBack, viewModel)
            SenderType.GOTIFY -> GotifyConfigForm(senderId, handleBack, viewModel)
            SenderType.DINGTALK_INNER_ROBOT -> DingtalkInnerConfigForm(senderId, handleBack, viewModel)
            SenderType.FEISHU_APP -> FeishuAppConfigForm(senderId, handleBack, viewModel)
            SenderType.URL_SCHEME -> UrlSchemeConfigForm(senderId, handleBack, viewModel)
            SenderType.SOCKET -> SocketConfigForm(senderId, handleBack, viewModel)
            else -> DingtalkConfigForm(senderId, handleBack, viewModel)
        }
    }
}

@Composable
private fun SmsChannelDisabledScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "当前构建版本不支持短信通道",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "该能力仅在 GitHub 版提供。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(modifier = Modifier.padding(top = 16.dp)) {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("返回")
            }
        }
    }
}
