package io.github.magisk317.relay.ui.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.AppPreferencesDataStore
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import io.github.magisk317.relay.di.appModule
import io.github.magisk317.relay.forwarder.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.web.WebUiRuntimeConfig
import io.github.magisk317.relay.web.WebUiServer
import io.github.magisk317.relay.web.WebUiTlsManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class SmsCodeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webUiServer: WebUiServer? = null
    private var webUiServerConfigJob: Job? = null
    private var startedActivityCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        ensureIpcToken()
        RuntimeLogStore.initialize(this, enableDetailedLogs = false)
        if (io.github.magisk317.relay.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger()
            androidContext(this@SmsCodeApplication)
            modules(appModule)
        }
        syncPreferences()
        handlePhoneProcessRestartIfNeeded()
        registerLicenseActivityKiller()
        applicationScope.launch {
            ensureWebUiConfigInitialized()
            startWebUiServer()
        }
        RootDbCatchupScheduler.startPeriodic(this, reason = "app_create")
    }

    override fun onTerminate() {
        webUiServerConfigJob?.cancel()
        webUiServerConfigJob = null
        webUiServer?.stop()
        webUiServer = null
        RootDbCatchupScheduler.stopPeriodic(reason = "app_terminate")
        super.onTerminate()
    }

    private fun syncPreferences() {
        applicationScope.launch {
            AppPreferencesDataStore.migrateLegacyKeys(this@SmsCodeApplication)
            AppPreferencesDataStore.syncToSharedPrefs(this@SmsCodeApplication)
            AppPreferencesDataStore.ensureReadable(this@SmsCodeApplication)
            val verboseLog = AppPreferencesDataStore.getBoolean(
                this@SmsCodeApplication,
                PrefConst.KEY_VERBOSE_LOG_MODE,
                false,
            )
            RuntimeLogStore.setEnabled(verboseLog)
        }
    }

    private fun registerLicenseActivityKiller() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount += 1
                if (startedActivityCount == 1) {
                    RootDbCatchupScheduler.stopPeriodic(reason = "app_foreground")
                }
            }
            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name == "com.pairip.licensecheck.LicenseActivity") {
                    runCatching {
                        Timber.w("Detected com.pairip.licensecheck.LicenseActivity. Finishing it to prevent gray screen.")
                        activity.finish()
                    }
                        .onFailure { Timber.e(it, "Failed to finish LicenseActivity") }
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    RootDbCatchupScheduler.startPeriodic(
                        this@SmsCodeApplication,
                        reason = "app_background",
                    )
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun ensureIpcToken() {
        applicationScope.launch {
            val token = AppPreferencesDataStore.getString(this@SmsCodeApplication, PrefConst.KEY_IPC_TOKEN, "")
            if (token.isEmpty()) {
                val newToken = UUID.randomUUID().toString()
                AppPreferencesDataStore.setString(this@SmsCodeApplication, PrefConst.KEY_IPC_TOKEN, newToken)
                Timber.i("Generated new IPC Security Token via DataStore")
            }
            AppPreferencesDataStore.ensureReadable(this@SmsCodeApplication)
        }
    }

    private fun handlePhoneProcessRestartIfNeeded() {
        applicationScope.launch {
            val installToken = buildInstallToken() ?: return@launch
            val prefs = getSharedPreferences(INSTALL_GUARD_PREFS, MODE_PRIVATE)
            val lastHandledToken = prefs.getString(KEY_LAST_HANDLED_INSTALL_TOKEN, null)
            if (lastHandledToken == installToken) {
                return@launch
            }

            val now = System.currentTimeMillis()
            val lastAttemptAt = prefs.getLong(KEY_LAST_RESTART_ATTEMPT_AT, 0L)
            if (now - lastAttemptAt < RESTART_ATTEMPT_COOLDOWN_MS) {
                return@launch
            }
            prefs.edit().putLong(KEY_LAST_RESTART_ATTEMPT_AT, now).apply()

            if (isPhoneCallActive()) {
                return@launch
            }

            val hasRootAccess = canUseRoot()
            if (hasRootAccess) {
                restartPhoneProcessViaRoot()
            }

            // Mark token handled even when root is unavailable to avoid repeated noisy attempts.
            prefs.edit().putString(KEY_LAST_HANDLED_INSTALL_TOKEN, installToken).apply()
        }
    }

    private suspend fun ensureWebUiConfigInitialized() {
        val webUiEnabled = AppPreferencesDataStore.getBoolean(
            this@SmsCodeApplication,
            PrefConst.KEY_WEBUI_ENABLE,
            true,
        )
        AppPreferencesDataStore.setBoolean(
            this@SmsCodeApplication,
            PrefConst.KEY_WEBUI_ENABLE,
            webUiEnabled,
        )
        val port = AppPreferencesDataStore.getString(
            this@SmsCodeApplication,
            PrefConst.KEY_WEBUI_PORT,
            "",
        )
        if (port.isBlank()) {
            AppPreferencesDataStore.setString(
                this@SmsCodeApplication,
                PrefConst.KEY_WEBUI_PORT,
                PrefConst.KEY_WEBUI_PORT_DEFAULT,
            )
        }
        val username = AppPreferencesDataStore.getString(
            this@SmsCodeApplication,
            PrefConst.KEY_WEBUI_USERNAME,
            "",
        )
        if (username.isBlank()) {
            AppPreferencesDataStore.setString(
                this@SmsCodeApplication,
                PrefConst.KEY_WEBUI_USERNAME,
                PrefConst.KEY_WEBUI_USERNAME_DEFAULT,
            )
        }
        val password = AppPreferencesDataStore.getString(
            this@SmsCodeApplication,
            PrefConst.KEY_WEBUI_PASSWORD,
            "",
        )
        if (password.isBlank()) {
            AppPreferencesDataStore.setString(
                this@SmsCodeApplication,
                PrefConst.KEY_WEBUI_PASSWORD,
                WebUiTlsManager.generateRandomCredential(8),
            )
        }
    }

    private fun startWebUiServer() {
        webUiServerConfigJob?.cancel()
        webUiServerConfigJob = applicationScope.launch {
            combine(
                AppPreferencesDataStore.getBooleanFlow(
                    this@SmsCodeApplication,
                    PrefConst.KEY_WEBUI_ENABLE,
                    true,
                ),
                AppPreferencesDataStore.getBooleanFlow(
                    this@SmsCodeApplication,
                    PrefConst.KEY_WEBUI_LAN_ACCESS,
                    false,
                ),
                AppPreferencesDataStore.getStringFlow(
                    this@SmsCodeApplication,
                    PrefConst.KEY_WEBUI_PORT,
                    PrefConst.KEY_WEBUI_PORT_DEFAULT,
                ),
                AppPreferencesDataStore.getStringFlow(
                    this@SmsCodeApplication,
                    PrefConst.KEY_WEBUI_USERNAME,
                    PrefConst.KEY_WEBUI_USERNAME_DEFAULT,
                ),
                AppPreferencesDataStore.getStringFlow(
                    this@SmsCodeApplication,
                    PrefConst.KEY_WEBUI_PASSWORD,
                    "",
                ),
            ) { webUiEnabled, allowLanAccess, portString, username, password ->
                val port = portString.toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?: PrefConst.KEY_WEBUI_PORT_DEFAULT.toInt()
                WebUiConfigSnapshot(
                    enabled = webUiEnabled,
                    host = if (allowLanAccess) "0.0.0.0" else "127.0.0.1",
                    port = port,
                    username = username.ifBlank { PrefConst.KEY_WEBUI_USERNAME_DEFAULT },
                    password = password,
                    allowLanAccess = allowLanAccess,
                )
            }.distinctUntilChanged().collect { snapshot ->
                if (!snapshot.enabled) {
                    webUiServer?.stop()
                    webUiServer = null
                    return@collect
                }
                runCatching {
                    val tlsMaterial = WebUiTlsManager.loadOrCreate(this@SmsCodeApplication)
                    val runtimeConfig = WebUiRuntimeConfig(
                        host = snapshot.host,
                        port = snapshot.port,
                        username = snapshot.username,
                        password = snapshot.password,
                        allowLanAccess = snapshot.allowLanAccess,
                        tlsMaterial = tlsMaterial,
                    )
                    webUiServer?.stop()
                    WebUiServer(
                        context = this@SmsCodeApplication,
                        runtimeConfig = runtimeConfig,
                    ).also {
                        it.start()
                        webUiServer = it
                    }
                }.onFailure {
                    Timber.e(
                        it,
                        "Failed to start WebUI server (host=%s port=%s lan=%s)",
                        snapshot.host,
                        snapshot.port,
                        snapshot.allowLanAccess,
                    )
                }
            }
        }
    }

    private fun buildInstallToken(): String? {
        val packageInfo = runCatching { getSelfPackageInfo() }.getOrNull() ?: return null
        val apkFile = runCatching { File(applicationInfo.sourceDir) }.getOrNull() ?: return null
        val apkSize = runCatching { apkFile.length() }.getOrDefault(0L)
        val apkModified = runCatching { apkFile.lastModified() }.getOrDefault(0L)
        return listOf(
            packageInfo.firstInstallTime,
            packageInfo.lastUpdateTime,
            apkSize,
            apkModified,
        ).joinToString(separator = ":")
    }

    private fun getSelfPackageInfo(): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun canUseRoot(): Boolean {
        val result = runSuCommand("id -u")
        return result.exitCode == 0 && result.output.trim() == "0"
    }

    private fun isPhoneCallActive(): Boolean {
        val telephonyInCall = runCatching {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            val state = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE
            state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING
        }.getOrDefault(false)
        if (telephonyInCall) {
            return true
        }

        // Fallback without runtime permission dependency.
        return runCatching {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
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

    private data class SuCommandResult(
        val exitCode: Int,
        val output: String,
    )

    private data class WebUiConfigSnapshot(
        val enabled: Boolean,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val allowLanAccess: Boolean,
    )

    companion object {
        private const val INSTALL_GUARD_PREFS = "install_guard_prefs"
        private const val KEY_LAST_HANDLED_INSTALL_TOKEN = "last_handled_install_token"
        private const val KEY_LAST_RESTART_ATTEMPT_AT = "last_restart_attempt_at"
        private const val RESTART_ATTEMPT_COOLDOWN_MS = 60_000L
    }
}
