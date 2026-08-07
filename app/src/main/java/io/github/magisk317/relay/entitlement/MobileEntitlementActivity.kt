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
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshStatus() {
        scope.launch {
            busy = true
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context)
                }
            }.onSuccess { evaluation = it }
                .onFailure { message = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { message = it.message ?: it.javaClass.simpleName }
    }

    fun createChallenge() {
        scope.launch {
            busy = true
            message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
            }.onSuccess {
                challenge = it
                openTelegram(it.botUrl)
            }.onFailure { message = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    fun activateWithGoogle() {
        scope.launch {
            busy = true
            message = null
            runCatching {
                val challenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createGoogleChallenge(context)
                }
                val idToken = googleSignIn.getIdToken(
                    activity = activity,
                    serverClientId = BuildConfig.MOBILE_ENTITLEMENT_GOOGLE_WEB_CLIENT_ID,
                    nonce = challenge.nonce,
                )
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateWithGoogleIdToken(
                        context = context,
                        challengeId = challenge.id,
                        idToken = idToken,
                    )
                }
            }.onSuccess { evaluation = it }
                .onFailure { message = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
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
            Button(
                onClick = ::createChallenge,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
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
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        }
                        Text(stringResource(R.string.mobile_entitlement_activate_google))
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
                enabled = !busy,
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

private fun formatEpoch(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
