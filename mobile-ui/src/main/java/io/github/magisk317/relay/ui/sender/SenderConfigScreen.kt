package io.github.magisk317.relay.ui.sender

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderActiveSchedule
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.ui.sender.forms.*
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SenderConfigScreen(
    senderId: Long,
    senderTypeArg: Int,
    onBack: (Boolean) -> Unit,
    onOpenSenderNotifyScope: (Long) -> Unit = {},
    onOpenSenderForwardFilter: (Long) -> Unit = {},
    viewModel: SenderViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(senderTypeArg) }
    var senderName by remember { mutableStateOf("") }
    var activeSchedule by remember { mutableStateOf(SenderActiveSchedule()) }
    var showForwardFilterDialog by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(senderId) {
        // Reset per-screen save state to avoid carrying stale status across entries.
        viewModel.clearLastSavedStatus()
        if (senderId != 0L) {
            val sender = viewModel.getSender(senderId)
            if (sender != null) {
                senderName = sender.name
                type = sender.type
                activeSchedule = sender.activeSchedule
            }
        } else {
            senderName = ""
            activeSchedule = SenderActiveSchedule()
        }
        isLoaded = true
    }

    val lastSavedStatus by viewModel.lastSavedStatus.collectAsStateWithLifecycle()
    val forwardFilterSummaryFlow = remember(senderId) {
        if (senderId > 0L) {
            viewModel.senderForwardFilterSummaryFlow(senderId)
        } else {
            flowOf("")
        }
    }
    val forwardFilterSummary by forwardFilterSummaryFlow.collectAsStateWithLifecycle(initialValue = "")
    val notifyScopeEntry = remember(senderId, onOpenSenderNotifyScope) {
        if (senderId > 0L) {
            SenderNotifyScopeEntry(
                senderId = senderId,
                summary = context.getString(R.string.subtitle_notification_rules),
                onClick = onOpenSenderNotifyScope,
            )
        } else {
            null
        }
    }
    val forwardFilterEntry = remember(senderId, forwardFilterSummary) {
        if (senderId > 0L) {
            SenderForwardFilterEntry(
                senderId = senderId,
                summary = forwardFilterSummary.ifBlank { context.getString(R.string.sender_filter_summary_default) },
                onClick = { showForwardFilterDialog = true },
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
        LocalSenderActiveScheduleEntry provides SenderActiveScheduleEntry(
            schedule = activeSchedule,
            onChange = { nextSchedule -> activeSchedule = nextSchedule },
        ),
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
            SenderType.NTFY -> NtfyConfigForm(senderId, handleBack, viewModel)
            SenderType.DINGTALK_INNER_ROBOT -> DingtalkInnerConfigForm(senderId, handleBack, viewModel)
            SenderType.FEISHU_APP -> FeishuAppConfigForm(senderId, handleBack, viewModel)
            SenderType.URL_SCHEME -> UrlSchemeConfigForm(senderId, handleBack, viewModel)
            SenderType.SOCKET -> SocketConfigForm(senderId, handleBack, viewModel)
            else -> DingtalkConfigForm(senderId, handleBack, viewModel)
        }
    }
    if (showForwardFilterDialog && senderId > 0L) {
        SenderForwardFilterDialog(
            senderId = senderId,
            senderName = senderName,
            onDismiss = { showForwardFilterDialog = false },
            viewModel = viewModel,
        )
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
            text = stringResource(R.string.sender_channel_disabled_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.sender_channel_disabled_summary),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(modifier = Modifier.padding(top = 16.dp)) {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}
