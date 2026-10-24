package io.github.magisk317.relay.xp.hook.keepalive

import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.xposed.keepalive.KeepAliveCapability
import io.github.magisk317.xposed.keepalive.KeepAliveConfig
import io.github.magisk317.xposed.keepalive.KeepAliveEventSink
import io.github.magisk317.xposed.keepalive.KeepAliveFlags
import io.github.magisk317.xposed.keepalive.KeepAliveFlagsProvider
import io.github.magisk317.xposed.keepalive.KeepAliveHook
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

/**
 * xinyi-relay keep-alive hook.
 *
 * Only the switch source and the protected package are project specific; the framework
 * descriptors, the policy and the hook bodies all live in the shared kit module.
 */
private object CorePrefsKeepAliveFlagsProvider : KeepAliveFlagsProvider {
    override fun current(): KeepAliveFlags {
        return runCatching {
            KeepAliveFlags(
                ready = true,
                oomAdj = CorePrefs.getBoolean(KeepAliveHookConst.KEY_KEEPALIVE_OOM_ADJ, false),
                antiKill = CorePrefs.getBoolean(KeepAliveHookConst.KEY_KEEPALIVE_ANTI_KILL, false),
                standbyBypass = CorePrefs.getBoolean(KeepAliveHookConst.KEY_KEEPALIVE_STANDBY_BYPASS, false),
            )
        }.getOrElse { error ->
            // A failed read must not leave the previous decision in place.
            XLog.w(message = "KeepAliveHook: pref read failed err=%s", error.message)
            KeepAliveFlags()
        }
    }
}

private object MagiskOtelKeepAliveEventSink : KeepAliveEventSink {
    override fun event(name: String, attributes: Map<String, String>) {
        MagiskOtel.event(name = name, attributes = attributes, statusOk = true)
    }
}

class RelayKeepAliveHook : KeepAliveHook(
    KeepAliveConfig(
        targetPackage = KeepAliveHookConst.TARGET_PACKAGE,
        flagsProvider = CorePrefsKeepAliveFlagsProvider,
        capabilities = setOf(
            KeepAliveCapability.OOM_APPLY,
            KeepAliveCapability.KILL_GUARD,
            KeepAliveCapability.STANDBY_BUCKET,
            KeepAliveCapability.STANDBY_IDLE,
            KeepAliveCapability.STANDBY_FORCE_IDLE,
        ),
        eventSink = MagiskOtelKeepAliveEventSink,
        // MIUI/HyperOS surface system_server under more than one package/process name.
        systemPackages = setOf("android", "system"),
        systemProcesses = setOf("system", "android", "system_server"),
        logTag = "xinyi.KeepAliveHook",
    ),
)
