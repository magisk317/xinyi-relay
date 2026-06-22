package io.github.magisk317.relay.analytics

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache

object AnalyticsTracker {
    @Volatile
    private var initialized = false

    @Volatile
    private var instance: Any? = null

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext ?: context
        instance = runCatching {
            val clazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val getInstance = clazz.getMethod("getInstance", Context::class.java)
            getInstance.invoke(null, appContext)
        }.onFailure { error ->
            XLog.w("FirebaseAnalytics init skipped: %s", error.message ?: error.javaClass.simpleName)
        }.getOrNull()
    }

    suspend fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        val context = appContext ?: return
        val deps = RuntimeDependencies.get()
        val analyticsEnabled = RuntimeSettingsCache.getBoolean(
            key = PrefConst.KEY_ENABLE_ANALYTICS,
            defaultValue = true,
        ) { key, defaultValue ->
            deps.preferenceDataSource.getBoolean(key, defaultValue)
        }
        if (!analyticsEnabled) return
        val tracker = instance ?: return
        runCatching {
            val bundle = Bundle()
            params.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Float -> bundle.putFloat(key, value)
                    else -> bundle.putString(key, value.toString())
                }
            }
            val method = tracker.javaClass.getMethod("logEvent", String::class.java, Bundle::class.java)
            method.invoke(tracker, name, bundle)
        }.onFailure { error ->
            XLog.w("FirebaseAnalytics logEvent failed: %s", error.message ?: error.javaClass.simpleName)
        }
    }
}
