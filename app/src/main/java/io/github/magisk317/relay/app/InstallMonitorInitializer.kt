package io.github.magisk317.relay.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class InstallMonitorInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        scope.launch {
            handlePhoneProcessRestartIfNeeded(application)
        }
    }

    private suspend fun handlePhoneProcessRestartIfNeeded(context: Context) {
        val installToken = buildInstallToken(context) ?: return
        val prefs = context.getSharedPreferences(INSTALL_GUARD_PREFS, Context.MODE_PRIVATE)
        val lastHandledToken = prefs.getString(KEY_LAST_HANDLED_INSTALL_TOKEN, null)
        if (lastHandledToken == installToken) {
            return
        }

        val now = System.currentTimeMillis()
        val lastAttemptAt = prefs.getLong(KEY_LAST_RESTART_ATTEMPT_AT, 0L)
        if (now - lastAttemptAt < RESTART_ATTEMPT_COOLDOWN_MS) {
            return
        }
        prefs.edit().putLong(KEY_LAST_RESTART_ATTEMPT_AT, now).apply()

        if (isPhoneCallActive(context)) {
            return
        }

        if (canUseRoot()) {
            restartPhoneProcessViaRoot()
        }

        prefs.edit().putString(KEY_LAST_HANDLED_INSTALL_TOKEN, installToken).apply()
    }

    private fun buildInstallToken(context: Context): String? {
        val packageInfo = runCatching { getSelfPackageInfo(context) }.getOrNull() ?: return null
        val apkFile = runCatching { File(context.applicationInfo.sourceDir) }.getOrNull() ?: return null
        val apkSize = runCatching { apkFile.length() }.getOrDefault(0L)
        val apkModified = runCatching { apkFile.lastModified() }.getOrDefault(0L)
        return listOf(
            packageInfo.firstInstallTime,
            packageInfo.lastUpdateTime,
            apkSize,
            apkModified,
        ).joinToString(separator = ":")
    }

    private fun getSelfPackageInfo(context: Context): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    private fun canUseRoot(): Boolean {
        val result = runSuCommand("id -u")
        return result.exitCode == 0 && result.output.trim() == "0"
    }

    private fun isPhoneCallActive(context: Context): Boolean {
        val telephonyInCall = runCatching {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            val state = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE
            state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING
        }.getOrDefault(false)
        if (telephonyInCall) {
            return true
        }

        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        }.getOrDefault(false)
    }

    private fun restartPhoneProcessViaRoot() {
        val command =
            "PIDS=\$(pidof com.android.phone 2>/dev/null); " +
                "if [ -n \"${'$'}PIDS\" ]; then kill -9 ${'$'}PIDS; exit 0; fi; " +
                "pkill -f com.android.phone >/dev/null 2>&1 && exit 0; " +
                "exit 1"
        val result = runSuCommand(command)
        if (result.exitCode == 0) {
            Timber.i("Phone process restart requested after install/update change.")
        }
    }

    private fun runSuCommand(command: String): SuCommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            SuCommandResult(exitCode = exitCode, output = output)
        } catch (_: Throwable) {
            SuCommandResult(exitCode = -1, output = "")
        }
    }

    private data class SuCommandResult(val exitCode: Int, val output: String)

    companion object {
        private const val INSTALL_GUARD_PREFS = "install_guard_prefs"
        private const val KEY_LAST_HANDLED_INSTALL_TOKEN = "last_handled_install_token"
        private const val KEY_LAST_RESTART_ATTEMPT_AT = "last_restart_attempt_at"
        private const val RESTART_ATTEMPT_COOLDOWN_MS = 60_000L
    }
}
