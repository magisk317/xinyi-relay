package io.github.magisk317.relay.entitlement

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementStatus
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.home.settings.SettingsViewModel
import io.github.magisk317.relay.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                MobileEntitlementScreen(
                    activity = this@MobileEntitlementActivity,
                    onBack = ::finish,
                )
            }
        }
    }
}

@Composable
private fun MobileEntitlementScreen(
    activity: Activity,
    onBack: () -> Unit,
) {
    val context: Context = activity
    val scope = rememberCoroutineScope()
    var evaluation by remember {
        mutableStateOf(MobileEntitlementCoordinator.readCachedEvaluation())
    }
    var busyAction by remember { mutableStateOf<ActivationAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var activationTokenInput by remember { mutableStateOf("") }

    fun refreshStatus(
        force: Boolean = true,
        showProgress: Boolean = true,
    ) {
        scope.launch {
            if (showProgress) busyAction = ActivationAction.REFRESH
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context, force = force)
                }
            }.onSuccess {
                evaluation = it
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            if (showProgress) busyAction = null
        }
    }

    fun activateWithToken() {
        val trimmed = activationTokenInput.trim()
        if (trimmed.length != 32) {
            message = context.getString(R.string.mobile_entitlement_activation_token_invalid)
            return
        }
        scope.launch {
            busyAction = ActivationAction.TOKEN
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateByToken(context, trimmed)
                }
            }.onSuccess {
                evaluation = it
                activationTokenInput = ""
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { message = it.message ?: it.javaClass.simpleName }
    }

    fun openTelegramBot() {
        scope.launch {
            busyAction = ActivationAction.TELEGRAM
            message = null
            runCatching {
                val challenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
                openTelegram(challenge.botUrl)
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    LaunchedEffect(Unit) {
        val cached = MobileEntitlementCoordinator.readCachedEvaluation()
        if (cached != null) evaluation = cached
        refreshStatus(force = false, showProgress = cached == null)
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.mobile_entitlement_title),
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.mobile_entitlement_status,
                            stringResource(mobileEntitlementStatusStringRes(evaluation?.status)),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.mobile_entitlement_automation,
                            if (evaluation?.automationAllowed == true) {
                                stringResource(R.string.mobile_entitlement_allowed)
                            } else {
                                stringResource(R.string.mobile_entitlement_paused)
                            },
                        ),
                    )
                    evaluation?.claims?.issuedAt?.takeIf { it > 0 }?.let { issuedAt ->
                        Text(stringResource(R.string.mobile_entitlement_issued_at, formatEpoch(issuedAt)))
                    }
                    evaluation?.claims?.deviceId?.takeIf { it.isNotBlank() }?.let { deviceId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.mobile_entitlement_device_id, deviceId),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as? android.content.ClipboardManager
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("device_id", deviceId))
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.mobile_entitlement_copied),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.mobile_entitlement_copy),
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            val isActivated = isMobileEntitlementActivated(evaluation?.status)

            if (!isActivated) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.mobile_entitlement_activation_token_label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedTextField(
                            value = activationTokenInput,
                            onValueChange = { activationTokenInput = it.trim().uppercase() },
                            label = { Text(stringResource(R.string.mobile_entitlement_activation_token_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = busyAction == null,
                        )
                        Button(
                            onClick = ::activateWithToken,
                            enabled = busyAction == null && activationTokenInput.trim().length == 32,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busyAction == ActivationAction.TOKEN) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.mobile_entitlement_activation_token_confirm))
                        }
                        Text(
                            text = stringResource(R.string.mobile_entitlement_activation_token_get_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val currentEvaluation = evaluation
            val showActivationActions = currentEvaluation == null ||
                !isMobileEntitlementActivated(currentEvaluation.status) ||
                currentEvaluation.renewDue
            if (showActivationActions) {
                OutlinedButton(
                    onClick = ::openTelegramBot,
                    enabled = busyAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busyAction == ActivationAction.TELEGRAM) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.mobile_entitlement_activate_telegram))
                }
            }
            OutlinedButton(
                onClick = { refreshStatus(force = true) },
                enabled = busyAction == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mobile_entitlement_refresh))
            }
            message?.let {
                Text(
                    text = stringResource(R.string.mobile_entitlement_error, it),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private enum class ActivationAction {
    REFRESH,
    TOKEN,
    TELEGRAM,
}

internal fun isMobileEntitlementActivated(status: MobileEntitlementStatus?): Boolean =
    status == MobileEntitlementStatus.ACTIVE || status == MobileEntitlementStatus.GRACE

@StringRes
internal fun mobileEntitlementStatusStringRes(status: MobileEntitlementStatus?): Int = when {
    status == null -> R.string.mobile_entitlement_not_loaded
    isMobileEntitlementActivated(status) -> R.string.module_status_active
    else -> R.string.module_status_inactive
}

private fun formatEpoch(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
