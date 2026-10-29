package io.github.magisk317.relay.entitlement

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magisk317.mobile.entitlement.MobileEntitlementChallenge
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementStatus
import io.github.magisk317.relay.contract.constant.RelayAppConst
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.relay.ui.home.settings.SettingsViewModel
import io.github.magisk317.relay.ui.theme.AppTheme
import io.github.magisk317.uikit.entitlement.MobileEntitlementBusyAction
import io.github.magisk317.uikit.entitlement.MobileEntitlementContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

class MobileEntitlementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Theme must follow the user's saved appearance preferences (MainActivity parity):
            // a hard-coded themeMode = 0 pinned this page to the system default and it read as
            // dark in a light app (or vice versa).
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
            AppTheme(
                themeMode = themeState.mode,
                uiKitStyle = themeState.uiKitStyle,
                layoutScale = themeState.layoutScale,
                paletteStyle = themeState.paletteStyle,
                colorSpec = themeState.colorSpec,
                monetEnabled = themeState.monetEnabled,
                surfaceBlur = themeState.surfaceBlur,
                dynamicColor = themeState.dynamicColor,
                accentColor = themeState.accentColor,
            ) {
                MobileEntitlementRoot(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun MobileEntitlementRoot(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var evaluation by remember {
        mutableStateOf(MobileEntitlementCoordinator.readCachedEvaluation())
    }
    var busyAction by remember { mutableStateOf<MobileEntitlementBusyAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var qqChallenge by remember { mutableStateOf<MobileEntitlementChallenge?>(null) }

    fun refreshStatus(force: Boolean = false) {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.REFRESH
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context, force = force)
                }
            }.onSuccess {
                evaluation = it
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    // A cold process has no in-memory evaluation (readCachedEvaluation() is a TTL cache), while
    // the overview tab refreshes on its own. Without this load the two screens contradict each
    // other: 概览 reports 服务鉴权：已通过 and this page claims 激活状态：未加载 / 功能状态：已暂停.
    LaunchedEffect(Unit) {
        if (evaluation == null) {
            refreshStatus()
        }
    }

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error ->
            // ACTION_VIEW 失败（Telegram 未安装等）时把链接复制到剪贴板兜底。
            copyPlainText(context, "bot_url", url)
            message = error.message ?: error.javaClass.simpleName
        }
    }

    fun openTelegramBot() {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.TELEGRAM
            message = null
            runCatching {
                val challenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
                val url = challenge.botUrl
                if (url.isNullOrBlank()) {
                    message = context.getString(io.github.magisk317.uikit.R.string.mobile_entitlement_qq_entry_missing)
                } else {
                    openTelegram(url)
                }
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    fun startQQActivation() {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.CODE
            message = null
            runCatching {
                qqChallenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createQQChallenge(context)
                }
            }.onFailure {
                qqChallenge = null
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun activateWithToken(token: String) {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.TOKEN
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateByToken(context, token)
                }
            }.onSuccess {
                evaluation = it
                qqChallenge = null
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    MobileEntitlementContent(
        statusLabel = stringResource(mobileEntitlementStatusStringRes(evaluation?.status)),
        activated = isMobileEntitlementActivated(evaluation?.status),
        automationAllowed = evaluation?.automationAllowed == true,
        renewDue = evaluation?.renewDue == true,
        issuedAtSeconds = evaluation?.claims?.issuedAt ?: 0L,
        deviceId = evaluation?.claims?.deviceId?.takeIf { it.isNotBlank() },
        activationCode = qqChallenge?.activationCode,
        busy = busyAction,
        message = message,
        channelUrl = RelayAppConst.QQ_CHANNEL_URL,
        onBack = onBack,
        onRefresh = { refreshStatus(force = true) },
        onGetActivationCode = { startQQActivation() },
        onOpenTelegram = { openTelegramBot() },
        onActivateToken = { activateWithToken(it) },
        onCodeSheetDismissed = { qqChallenge = null },
    )
}

private fun copyPlainText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun isMobileEntitlementActivated(status: MobileEntitlementStatus?): Boolean =
    status == MobileEntitlementStatus.ACTIVE || status == MobileEntitlementStatus.GRACE

@androidx.annotation.StringRes
internal fun mobileEntitlementStatusStringRes(status: MobileEntitlementStatus?): Int = when {
    status == null -> R.string.mobile_entitlement_not_loaded
    isMobileEntitlementActivated(status) -> R.string.module_status_active
    else -> R.string.module_status_inactive
}
