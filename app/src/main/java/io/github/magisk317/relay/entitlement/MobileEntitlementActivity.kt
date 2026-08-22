package io.github.magisk317.relay.entitlement

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MobileEntitlementActivity : ComponentActivity() {
    private val googleSignIn: MobileEntitlementGoogleSignIn by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(themeMode = 0) {
                MobileEntitlementScreen(
                    activity = this@MobileEntitlementActivity,
                    googleSignIn = googleSignIn,
                    onBack = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileEntitlementScreen(
    activity: Activity,
    googleSignIn: MobileEntitlementGoogleSignIn,
    onBack: () -> Unit,
) {
    val context: Context = activity
    val scope = rememberCoroutineScope()
    var evaluation by remember { mutableStateOf<MobileEntitlementEvaluation?>(null) }
    var challenge by remember { mutableStateOf<MobileEntitlementChallenge?>(null) }
    var busyAction by remember { mutableStateOf<ActivationAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var licenseCodeInput by remember { mutableStateOf("") }
    var savedLicenseCode by remember { mutableStateOf<String?>(null) }

    fun maskLicenseCode(code: String): String {
        val trimmed = code.trim()
        if (trimmed.length != 32) return trimmed
        return trimmed.take(4) + "*".repeat(24) + trimmed.takeLast(4)
    }

    fun refreshStatus() {
        scope.launch {
            busyAction = ActivationAction.REFRESH
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context)
                }
            }.onSuccess {
                evaluation = it
                savedLicenseCode = MobileEntitlementCoordinator.readSavedLicenseCode(context)
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    fun activateWithCode() {
        val trimmed = licenseCodeInput.trim()
        if (trimmed.length != 32) {
            message = context.getString(R.string.mobile_entitlement_license_code_invalid)
            return
        }
        scope.launch {
            busyAction = ActivationAction.LICENSE_CODE
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateWithLicenseCode(context, trimmed)
                }
            }.onSuccess {
                evaluation = it
                savedLicenseCode = MobileEntitlementCoordinator.readSavedLicenseCode(context)
                licenseCodeInput = ""
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { message = it.message ?: it.javaClass.simpleName }
    }

    fun createChallenge() {
        scope.launch {
            busyAction = ActivationAction.TELEGRAM
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
            }.onSuccess {
                challenge = it
                openTelegram(it.botUrl)
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    fun activateWithGoogle() {
        scope.launch {
            busyAction = ActivationAction.GOOGLE
            message = null
            var googleBotUrl: String? = null
            runCatching {
                val currentChallenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createGoogleChallenge(context)
                }
                googleBotUrl = currentChallenge.botUrl
                val idToken = googleSignIn.getIdToken(
                    activity = activity,
                    serverClientId = BuildConfig.MOBILE_ENTITLEMENT_GOOGLE_WEB_CLIENT_ID,
                    nonce = currentChallenge.nonce,
                )
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateWithGoogleIdToken(
                        context = context,
                        challengeId = currentChallenge.id,
                        idToken = idToken,
                    )
                }
            }.onSuccess { state ->
                if (state.status == MobileEntitlementActivationStatus.APPROVED) {
                    evaluation = state.evaluation
                    challenge = null
                } else {
                    val botUrl = googleBotUrl ?: state.botUrl
                    if (botUrl.isNullOrBlank()) {
                        message = context.getString(R.string.mobile_entitlement_telegram_required)
                    } else {
                        challenge = MobileEntitlementChallenge(
                            id = state.challengeId,
                            expiresAt = state.expiresAt ?: 0L,
                            botUrl = botUrl,
                        )
                        openTelegram(botUrl)
                    }
                }
            }
                .onFailure { message = it.message ?: it.javaClass.simpleName }
            busyAction = null
        }
    }

    LaunchedEffect(Unit) {
        savedLicenseCode = withContext(Dispatchers.IO) {
            MobileEntitlementCoordinator.readSavedLicenseCode(context)
        }
        val pendingChallengeId = withContext(Dispatchers.IO) {
            MobileEntitlementCoordinator.readPendingChallenge(context)
        }
        if (!pendingChallengeId.isNullOrBlank()) {
            challenge = MobileEntitlementChallenge(
                id = pendingChallengeId,
                expiresAt = 0L,
                botUrl = "",
            )
        }
        refreshStatus()
    }

    LaunchedEffect(challenge?.id) {
        val activeChallenge = challenge ?: return@LaunchedEffect
        while (isActive) {
            delay(3_000)
            val state = runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.pollTelegramChallenge(context, activeChallenge.id)
                }
            }.getOrNull()
            if (state == null) {
                message = context.getString(R.string.mobile_entitlement_poll_failed)
                continue
            }
            when (state.status) {
                MobileEntitlementActivationStatus.PENDING -> Unit
                MobileEntitlementActivationStatus.APPROVED -> {
                    evaluation = state.evaluation
                    MobileEntitlementCoordinator.clearPendingChallenge(context)
                    challenge = null
                    break
                }
                MobileEntitlementActivationStatus.EXPIRED -> {
                    message = context.getString(R.string.mobile_entitlement_challenge_expired)
                    MobileEntitlementCoordinator.clearPendingChallenge(context)
                    challenge = null
                    break
                }
                MobileEntitlementActivationStatus.CLAIMED -> {
                    message = "授权请求已领取，请重新发起激活。"
                    MobileEntitlementCoordinator.clearPendingChallenge(context)
                    challenge = null
                    break
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mobile_entitlement_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                            evaluation?.status?.name ?: stringResource(R.string.mobile_entitlement_not_loaded),
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
                    evaluation?.claims?.expiresAt?.let { expiresAt ->
                        Text(stringResource(R.string.mobile_entitlement_expires, formatEpoch(expiresAt)))
                    }
                    if (evaluation?.renewDue == true) {
                        Text(
                            text = stringResource(R.string.mobile_entitlement_renew_due),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            val isActivated = evaluation?.status == MobileEntitlementStatus.ACTIVE
            val displayCode = savedLicenseCode ?: ""

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mobile_entitlement_license_code_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isActivated && displayCode.isNotBlank()) {
                        OutlinedTextField(
                            value = maskLicenseCode(displayCode),
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text(stringResource(R.string.mobile_entitlement_license_code_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    } else {
                        OutlinedTextField(
                            value = licenseCodeInput,
                            onValueChange = { licenseCodeInput = it.trim().uppercase() },
                            label = { Text(stringResource(R.string.mobile_entitlement_license_code_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = busyAction == null,
                        )
                        Button(
                            onClick = ::activateWithCode,
                            enabled = busyAction == null && licenseCodeInput.trim().length == 32,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busyAction == ActivationAction.LICENSE_CODE) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.mobile_entitlement_license_code_confirm))
                        }
                        Text(
                            text = stringResource(R.string.mobile_entitlement_license_code_get_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val currentEvaluation = evaluation
            val showActivationActions = currentEvaluation == null ||
                currentEvaluation.status != MobileEntitlementStatus.ACTIVE ||
                currentEvaluation.renewDue
            if (showActivationActions) {
                Button(
                    onClick = ::createChallenge,
                    enabled = busyAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busyAction == ActivationAction.TELEGRAM) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.mobile_entitlement_activate_telegram))
                }
                if (BuildConfig.MOBILE_ENTITLEMENT_CHANNEL == "play") {
                    if (BuildConfig.MOBILE_ENTITLEMENT_GOOGLE_WEB_CLIENT_ID.isBlank()) {
                        Text(
                            text = stringResource(R.string.mobile_entitlement_play_flow),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Button(
                            onClick = ::activateWithGoogle,
                            enabled = busyAction == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busyAction == ActivationAction.GOOGLE) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.mobile_entitlement_activate_google))
                        }
                    }
                }
            }
            challenge?.let { pendingChallenge ->
                Text(stringResource(R.string.mobile_entitlement_pending))
                if (pendingChallenge.botUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = { openTelegram(pendingChallenge.botUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.mobile_entitlement_open_telegram))
                    }
                }
            }
            OutlinedButton(
                onClick = ::refreshStatus,
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
    LICENSE_CODE,
    TELEGRAM,
    GOOGLE,
}

private fun formatEpoch(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
