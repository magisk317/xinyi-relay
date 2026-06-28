package io.github.magisk317.relay.android.prefs

import android.content.Context
import android.content.SharedPreferences
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.contract.prefs.PrefReadResult
import io.github.magisk317.relay.contract.prefs.PrefsSource
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local SharedPreferences fallback source for hook processes.
 *
 * When the remote LibXposed prefs IPC is unavailable or the key is missing,
 * this source reads from the host process's local SharedPreferences file
 * (created via [Context.getSharedPreferences]).
 *
 * This mirrors XposedSmsCode's `hookContext` + `getLocalPrefs()` pattern.
 */
internal class LocalPrefsSource(
    private val prefsName: String,
) : PrefsSource {

    override val sourceName: String = "local_hook_prefs"

    @Volatile
    private var hookContext: Context? = null

    private val failureLogged = AtomicBoolean(false)

    fun setHookContext(context: Context) {
        hookContext = context.applicationContext ?: context
        failureLogged.set(false)
    }

    private fun getLocalPrefs(): SharedPreferences? {
        val ctx = hookContext ?: return null
        return runCatching {
            ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        }.getOrElse { t ->
            if (failureLogged.compareAndSet(false, true)) {
                XLog.w("LocalPrefsSource: getSharedPreferences failed", t)
            }
            null
        }
    }

    override fun readBoolean(context: Context, key: String, defaultValue: Boolean): PrefReadResult<Boolean>? {
        val prefs = getLocalPrefs() ?: return null
        return runCatching {
            if (!prefs.contains(key)) return null
            PrefReadResult(prefs.getBoolean(key, defaultValue), sourceName)
        }.getOrNull()
    }

    override fun readString(context: Context, key: String, defaultValue: String): PrefReadResult<String>? {
        val prefs = getLocalPrefs() ?: return null
        return runCatching {
            if (!prefs.contains(key)) return null
            PrefReadResult(prefs.getString(key, defaultValue) ?: defaultValue, sourceName)
        }.getOrNull()
    }

    override fun readInt(context: Context, key: String, defaultValue: Int): PrefReadResult<Int>? {
        val prefs = getLocalPrefs() ?: return null
        return runCatching {
            if (!prefs.contains(key)) return null
            val value = when (val any = prefs.all[key]) {
                is Int -> any
                is Long -> any.toInt()
                is String -> any.toIntOrNull() ?: defaultValue
                else -> prefs.getInt(key, defaultValue)
            }
            PrefReadResult(value, sourceName)
        }.getOrNull()
    }
}
