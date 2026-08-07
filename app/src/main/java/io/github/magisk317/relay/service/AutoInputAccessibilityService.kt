package io.github.magisk317.relay.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.receiver.AutoInputActions
import io.github.magisk317.smscode.verification.AutoInputFallbackPolicy
import io.github.magisk317.smscode.verification.AutoInputAccessibilityNodeHelper
import io.github.magisk317.smscode.verification.AutoInputAccessibilityNodeHelper.Result as AutoInputResult
import io.github.magisk317.smscode.verification.AutoInputAccessibilityRequestHandler
import io.github.magisk317.xposed.logging.MagiskOtel

class AutoInputAccessibilityService : AccessibilityService() {

    private var receiverRegistered = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunning = false

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!heartbeatRunning) return
            runCatching {
                if (!isKeepAliveHeartbeatEnabled()) {
                    stopHeartbeat()
                    return
                }
                if (!isMainProcessAlive()) {
                    XLog.w("Accessibility heartbeat: main process not alive, waking up")
                    wakeMainProcess()
                }
            }
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private val autoInputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!PrefsReader.mobileAutomationAllowed(this@AutoInputAccessibilityService)) {
                XLog.i("Mobile entitlement gate skipped accessibility auto-input")
                return
            }
            AutoInputAccessibilityRequestHandler.handle(
                serviceContext = this@AutoInputAccessibilityService,
                receiver = this,
                intent = intent,
                expectedAction = AutoInputActions.requestAction,
                resultAction = AutoInputActions.resultAction,
                packageName = packageName,
                performAutoInput = { request -> handleAutoInput(request.code, request.autoEnter) },
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerAutoInputReceiver()
        startHeartbeat()
        XLog.w("Accessibility auto input service connected")
        emitA11y(stage = "connected")
    }

    override fun onDestroy() {
        stopHeartbeat()
        unregisterAutoInputReceiver()
        XLog.w("Accessibility auto input service destroyed")
        emitA11y(stage = "destroy")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun registerAutoInputReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(AutoInputActions.requestAction).apply {
            priority = RECEIVER_PRIORITY_ACCESSIBILITY
        }
        ContextCompat.registerReceiver(
            this,
            autoInputReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterAutoInputReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(autoInputReceiver) }
        receiverRegistered = false
    }

    private fun handleAutoInput(
        code: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        if (!PrefsReader.mobileAutomationAllowed(this)) {
            XLog.i("Mobile entitlement gate skipped accessibility execution")
            return AutoInputResult(false, "none", "mobile_entitlement", packageName)
        }
        val homePackages = resolveHomePackages()
        val targetPackageHint = resolveTargetPackageHint()
        return AutoInputFallbackPolicy.runWithRetries(
            maxAttempts = MAX_WINDOW_SETTLE_ATTEMPTS,
            delayMs = WINDOW_SETTLE_RETRY_DELAY_MS,
            perform = { attempt ->
                val result = performAutoInput(code, autoEnter)
                if (attempt < MAX_WINDOW_SETTLE_ATTEMPTS - 1 &&
                    AutoInputFallbackPolicy.shouldRetryAccessibilityResult(
                        success = result.success,
                        reason = result.reason,
                        windowPackage = result.windowPackage,
                        modulePackage = packageName,
                        homePackages = homePackages,
                    )
                ) {
                    XLog.w(
                        "Accessibility auto input retry: attempt=%d reason=%s windowPkg=%s targetPkg=%s",
                        attempt + 2,
                        result.reason,
                        result.windowPackage.ifBlank { "<none>" },
                        targetPackageHint.orEmpty().ifBlank { "<none>" },
                    )
                }
                result
            },
            shouldRetry = { result ->
                shouldWaitForTargetWindow(
                    result = result,
                    targetPackageHint = targetPackageHint,
                ) ||
                AutoInputFallbackPolicy.shouldRetryAccessibilityResult(
                    success = result.success,
                    reason = result.reason,
                    windowPackage = result.windowPackage,
                    modulePackage = packageName,
                    homePackages = homePackages,
                )
            },
            sleeper = SystemClock::sleep,
        )
    }

    private fun performAutoInput(
        code: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        return AutoInputAccessibilityNodeHelper.performAutoInput(rootInActiveWindow, code, autoEnter)
    }

    private fun shouldWaitForTargetWindow(
        result: AutoInputResult,
        targetPackageHint: String?,
    ): Boolean {
        if (result.success) return false
        val target = targetPackageHint.orEmpty()
        if (target.isBlank()) return false
        val current = result.windowPackage
        if (current.isBlank()) return false
        if (current == target) return false
        return current == packageName
    }

    private fun resolveTargetPackageHint(): String? {
        return runCatching {
            packageManager.getLaunchIntentForPackage(packageName)
            val recents = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            recents?.runningAppProcesses
                ?.firstOrNull { it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                ?.pkgList
                ?.firstOrNull { it.isNotBlank() && it != packageName }
        }.getOrNull()
    }

    private fun resolveHomePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            infos.mapNotNull { it.activityInfo?.packageName?.takeIf(String::isNotBlank) }.toSet()
        }.getOrDefault(emptySet())
    }

    // ── Keep-alive heartbeat ──────────────────────────────────────────────────

    private fun startHeartbeat() {
        if (heartbeatRunning) return
        heartbeatRunning = true
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        heartbeatRunning = false
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    private fun isKeepAliveHeartbeatEnabled(): Boolean {
        return runCatching {
            val prefs = getSharedPreferences("xposed_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("pref_keepalive_accessibility_heartbeat", false)
        }.getOrDefault(false)
    }

    private fun isMainProcessAlive(): Boolean {
        return runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = am.runningAppProcesses ?: return false
            runningProcesses.any {
                it.processName == packageName && it.pid != android.os.Process.myPid()
            }
        }.getOrDefault(false)
    }

    private fun wakeMainProcess() {
        runCatching {
            val intent = Intent().apply {
                component = ComponentName(packageName, "$packageName.ui.home.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            XLog.w("Accessibility heartbeat: launched main activity to wake process")
        }.onFailure { t ->
            XLog.w("Accessibility heartbeat: wake failed: %s", t.message)
        }
    }


    private fun emitA11y(stage: String, result: String = "ok", statusOk: Boolean = true) {
        MagiskOtel.event(
            name = "a11y.service",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "app",
                "stage" to stage,
            ),
            statusOk = statusOk,
        )
    }

    private companion object {
        private const val RECEIVER_PRIORITY_ACCESSIBILITY = -500
        private const val MAX_WINDOW_SETTLE_ATTEMPTS = 6
        private const val WINDOW_SETTLE_RETRY_DELAY_MS = 200L
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
    }
}
