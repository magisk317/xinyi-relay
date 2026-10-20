package io.github.magisk317.relay.ui.home.settings

import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.uikit.common.showLatestSnackbar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.relay.mobilefeature.settings.BuildConfig
import io.github.magisk317.relay.ui.permission.PermissionRequirement
import io.github.magisk317.relay.ui.permission.StartupPermissionCollector
import io.github.magisk317.relay.ui.permission.StartupPromptStore
import io.github.magisk317.relay.ui.permission.runRootGrants
import io.github.magisk317.relay.ui.permission.startupPermissionConfig
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import kotlinx.coroutines.launch

/**
 * Legacy startup permission prompt, kept for users who accepted privacy before
 * the setup wizard existed. The collection logic itself lives in
 * [StartupPermissionCollector] (shared with the setup wizard), so both flows
 * ask for exactly the same set.
 */
@Composable
fun StartupPermissionPrompt(enabled: Boolean) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var promptStarted by remember { mutableStateOf(false) }
    var pendingSpecialPermissions by remember { mutableStateOf<List<PermissionRequirement>>(emptyList()) }
    val config = remember(context) {
        startupPermissionConfig(context, BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT)
    }
    val launchNextSpecialPermission = remember { arrayOf<() -> Unit>({}) }

    fun finishPrompt() {
        StartupPromptStore.markPromptedForCurrentVersion(context)
        val stillMissing = StartupPermissionCollector.collectMissing(context, config)
        if (stillMissing.isNotEmpty()) {
            scope.launch {
                snackbarHostState.showLatestSnackbar(
                    context.getString(
                        R.string.startup_permission_missing_warning,
                        stillMissing.joinToString(
                            context.getString(R.string.scheduled_task_permission_separator),
                        ) { it.label },
                    ),
                )
            }
        }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        launchNextSpecialPermission[0]()
    }

    val specialPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        launchNextSpecialPermission[0]()
    }

    launchNextSpecialPermission[0] = {
        val next = pendingSpecialPermissions.firstOrNull()
        if (next == null) {
            finishPrompt()
        } else {
            pendingSpecialPermissions = pendingSpecialPermissions.drop(1)
            val intent = StartupPermissionCollector.buildSystemIntent(context, next)
            if (intent == null) {
                launchNextSpecialPermission[0]()
            } else {
                runCatching {
                    specialPermissionLauncher.launch(intent)
                }.onFailure {
                    launchNextSpecialPermission[0]()
                }
            }
        }
    }

    fun startPromptIfNeeded() {
        if (promptStarted || !enabled || StartupPromptStore.hasPrompted(context)) {
            return
        }
        promptStarted = true
        val missing = StartupPermissionCollector.collectMissing(context, config)
        if (missing.isEmpty()) {
            StartupPromptStore.markPromptedForCurrentVersion(context)
            return
        }
        pendingSpecialPermissions = missing.filter(PermissionRequirement::isSpecial)
        val runtimePermissions = missing.filterNot(PermissionRequirement::isSpecial).map { it.key }
        if (runtimePermissions.isNotEmpty()) {
            runtimePermissionLauncher.launch(runtimePermissions.toTypedArray())
        } else {
            launchNextSpecialPermission[0]()
        }
    }

    LaunchedEffect(enabled) {
        runCatching {
            withContext(Dispatchers.IO) {
                runRootGrants(context, config) { message ->
                    XLog.w(message = "PermissionBridge", message)
                }
            }
        }
        startPromptIfNeeded()
    }
}
