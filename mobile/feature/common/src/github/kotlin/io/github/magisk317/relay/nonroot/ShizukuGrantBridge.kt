package io.github.magisk317.relay.nonroot

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.magisk317.relay.ui.permission.StartupPermissionCollector
import io.github.magisk317.xposed.permission.StartupPermissionConfig
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.permission.PermissionBridge
import rikka.shizuku.Shizuku

/**
 * GitHub-only Shizuku channel: grants the same idempotent command set as the
 * root bridge, but through a Shizuku user service running as the shell uid.
 *
 * Shizuku cannot grant ROLE_SMS or replace Xposed hooks; this bridge only
 * removes the manual-settings hop for ordinary runtime and special-access
 * permissions.
 */
object ShizukuGrantBridge {

    private const val REQUEST_CODE = 0x5348
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val WARMUP_DELAY_MS = 400L
    private const val USAGE_LOOKBACK_MS = 10L * 60L * 1000L
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null

    fun isSupported(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    fun isAuthorized(): Boolean {
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermission(context: Context, onResult: (Boolean) -> Unit) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            onResult(false)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                try {
                    Shizuku.removeRequestPermissionResultListener(this)
                } catch (_: Exception) {
                    // Best-effort cleanup; the result callback already owns the outcome.
                }
                if (permissionListener === this) permissionListener = null
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        permissionListener?.let { listener ->
            try {
                Shizuku.removeRequestPermissionResultListener(listener)
            } catch (_: Exception) {
                // Best-effort cleanup of a stale listener.
            }
        }
        permissionListener = listener

        // Only pull the manager forward when it is not already alive in the
        // background. UsageStats is the process probe; the process list is a
        // fallback for builds without usage access.
        val appContext = context.applicationContext
        if (!isManagerInBackground(appContext)) {
            launchManager(appContext)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(REQUEST_CODE)
            }.onFailure {
                permissionListener = null
                try {
                    Shizuku.removeRequestPermissionResultListener(listener)
                } catch (_: Exception) {
                    // Best-effort cleanup after a failed request.
                }
                onResult(false)
            }
        }, WARMUP_DELAY_MS)
    }

    private fun isManagerInBackground(context: Context): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val processAlive = activityManager?.runningAppProcesses
            ?.any { it.processName == SHIZUKU_PACKAGE } == true
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usageStatsManager?.queryEvents(now - USAGE_LOOKBACK_MS, now)
        var lastForeground: Boolean? = null
        while (events?.hasNextEvent() == true) {
            val event = UsageEvents.Event()
            if (!events.getNextEvent(event)) break
            if (event.packageName != SHIZUKU_PACKAGE) continue
            val foreground = readForegroundState(event)
            if (foreground != null) lastForeground = foreground
        }
        return when {
            lastForeground == false -> true
            lastForeground == true -> false
            processAlive -> true
            else -> runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        }
    }

    @Suppress("DEPRECATION")
    private fun readForegroundState(event: UsageEvents.Event): Boolean? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> true
                UsageEvents.Event.ACTIVITY_PAUSED -> false
                else -> null
            }
        } else {
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                UsageEvents.Event.MOVE_TO_BACKGROUND -> false
                else -> null
            }
        }
    }

    private fun launchManager(context: Context) {
        val launched = runCatching {
            context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                context.startActivity(intent)
                true
            } ?: false
        }.getOrDefault(false)
        if (!launched) {
            // Best effort: pulling the manager forward only speeds the prompt up, but a
            // silent failure here is the difference between a prompt and a dead end.
            XLog.w(message = "Shizuku manager launch failed: package=%s", SHIZUKU_PACKAGE)
        }
    }

    suspend fun grant(
        context: Context,
        config: StartupPermissionConfig,
        log: (String) -> Unit,
    ): Boolean {
        if (!isAuthorized()) {
            log("Shizuku permission is not granted")
            return false
        }
        val commands = PermissionBridge.bridgeCommands(
            context = context,
            notificationListenerComponent = config.notificationListenerComponent,
            notificationListenerEnabled = StartupPermissionCollector.isNotificationListenerEnabled(context),
            accessibilityServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ),
            accessibilityComponent = config.autoInputAccessibilityComponent,
        )
        if (commands.isEmpty()) {
            log("No bridgeable permissions are missing")
            return true
        }
        return ShizukuShellClient.run(context, commands, log)
    }
}
