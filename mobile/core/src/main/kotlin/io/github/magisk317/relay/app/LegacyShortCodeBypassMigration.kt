package io.github.magisk317.relay.app

import android.content.Context
import android.provider.Settings
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.xposed.logging.XLog
import java.util.concurrent.TimeUnit

internal enum class LegacyShortCodeBypassAction {
    NONE,
    ENABLE_PRIVATE_AND_RESTORE_GLOBAL,
    RESTORE_GLOBAL,
}

internal fun decideLegacyShortCodeBypassAction(
    privatePreference: PrefRead<Boolean>,
    legacyGlobalValue: String?,
): LegacyShortCodeBypassAction {
    if (legacyGlobalValue != LEGACY_BYPASS_VALUE) return LegacyShortCodeBypassAction.NONE
    return when (privatePreference) {
        is PrefRead.Hit -> LegacyShortCodeBypassAction.RESTORE_GLOBAL
        PrefRead.Miss -> LegacyShortCodeBypassAction.ENABLE_PRIVATE_AND_RESTORE_GLOBAL
        PrefRead.Unavailable -> LegacyShortCodeBypassAction.NONE
    }
}

/** Migrates the released system-wide switch to the xinyi-only remote preference. */
internal object LegacyShortCodeBypassMigration {
    suspend fun migrate(context: Context, preferenceDataSource: PreferenceDataSource): Boolean {
        val privatePreference = runCatching {
            preferenceDataSource.readBoolean(RelayPrefConst.KEY_SMS_SHORT_CODE_CONFIRMATION_BYPASS)
        }.getOrElse { error ->
            XLog.w(
                message = "Short-code bypass migration: private preference unavailable: %s",
                error.javaClass.simpleName,
            )
            return false
        }
        val legacyGlobalValue = runCatching {
            Settings.Global.getString(context.contentResolver, LEGACY_GLOBAL_SETTING)
        }.getOrElse { error ->
            XLog.w(
                message = "Short-code bypass migration: global setting unavailable: %s",
                error.javaClass.simpleName,
            )
            return false
        }
        val action = decideLegacyShortCodeBypassAction(privatePreference, legacyGlobalValue)
        if (action == LegacyShortCodeBypassAction.NONE) return false

        if (action == LegacyShortCodeBypassAction.ENABLE_PRIVATE_AND_RESTORE_GLOBAL) {
            val persisted = runCatching {
                preferenceDataSource.setBoolean(
                    RelayPrefConst.KEY_SMS_SHORT_CODE_CONFIRMATION_BYPASS,
                    true,
                )
            }.onFailure { error ->
                XLog.w(
                    message = "Short-code bypass migration: private preference write failed: %s",
                    error.javaClass.simpleName,
                )
            }.isSuccess
            if (!persisted) return false
        }

        if (!restoreNativeGlobalConfirmation()) {
            XLog.w(message = "Short-code bypass migration: failed to restore native global confirmation")
            return false
        }
        XLog.i(message = "Short-code bypass migration: restored native global confirmation")
        return true
    }

    private fun restoreNativeGlobalConfirmation(): Boolean {
        return runCatching {
            val process = ProcessBuilder(
                "su",
                "-c",
                "settings put global $LEGACY_GLOBAL_SETTING $NATIVE_CONFIRMATION_VALUE",
            ).redirectErrorStream(true).start()
            val completed = process.waitFor(ROOT_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
                return@runCatching false
            }
            process.inputStream.close()
            process.exitValue() == 0
        }.getOrDefault(false)
    }
}

private const val LEGACY_GLOBAL_SETTING = "sms_short_code_confirmation"
private const val LEGACY_BYPASS_VALUE = "0"
private const val NATIVE_CONFIRMATION_VALUE = "1"
private const val ROOT_COMMAND_TIMEOUT_SEC = 10L
