package io.github.magisk317.relay.ui.app

import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeCrashLogs
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.uikit.entitlement.MobileGate
import io.github.magisk317.relay.android.prefs.RelayPreferenceHooks
import io.github.magisk317.forward.security.CiUpdateGateVault
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import android.app.Application
import android.app.Activity
import android.os.Bundle
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.app.InfrastructureInitializer
import io.github.magisk317.relay.di.appDependencyModule
import io.github.magisk317.relay.di.billingModule
import io.github.magisk317.relay.di.coreModule
import io.github.magisk317.relay.di.uiModule
import com.magisk317.mobile.entitlement.MobileEntitlementBridge
import com.magisk317.mobile.entitlement.MobileEntitlementConfig
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementPublishedState
import com.magisk317.mobile.entitlement.MobileEntitlementRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SmsCodeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivityCount: Int = 0
    private var entitlementForegroundPrimed = false

    override fun onCreate() {
        super.onCreate()
        // Before anything logs or crashes: the policy decides both redaction and how much a
        // bundle collects, and the crash writer has to be in place to catch the rest of startup.
        MagiskLogPolicy.install(
            // -PminifyDebug makes the debug variant non-debuggable, which clears
            // BuildConfig.DEBUG; the build type still identifies the capture channel.
            isDebugBuild = BuildConfig.BUILD_TYPE == "debug",
            allowPlaintextLogs = BuildConfig.ALLOW_PLAINTEXT_LOGS,
        )
        RuntimeCrashLogs.install(this)
        AppPreferencesDataStore.install(RelayPreferenceHooks)
        if (BuildConfig.ENABLE_MOBILE_ENTITLEMENT) {
            configureMobileEntitlement()
        } else {
            // Play distribution ships without the activation gate; publish an
            // always-allowed snapshot so every gate reader (app, hook, pipeline)
            // sees the open state, including installs cached as unactivated.
            MobileGate.publishAlwaysAllowed(
                persist = {
                    AppPreferencesDataStore.batchEdit(applicationContext) {
                        setBoolean(
                            io.github.magisk317.relay.contract.constant.RelayPrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                            true,
                        )
                    }
                },
                mirror = {
                    io.github.magisk317.relay.android.prefs.HookPreferenceMirror.publish(applicationContext)
                },
            )
        }

        startKoin {
            androidLogger()
            androidContext(this@SmsCodeApplication)
            modules(coreModule, billingModule, uiModule, appDependencyModule)
        }

        val koin = getKoin()
        // Register the Xposed listener first so the work mode can settle before the remaining initializers run.
        XposedServiceBridge.initialize(this, applicationScope)
        koin.get<InfrastructureInitializer>().init(this)
        val initializers = koin.getAll<AppInitializer>().filterNot { it is InfrastructureInitializer }
        initializers.forEach { it.init(this) }
        if (BuildConfig.ENABLE_MOBILE_ENTITLEMENT) {
            // entitlement stays initialized on every distribution flavor
            MobileEntitlementCoordinator.initialize(this, applicationScope)
            registerEntitlementForegroundRefresh()
        }
        // A backup is only ever spent on a build that actually moved and that comes
        // up without an activation, so release reads it too: nothing here can take
        // an activation away, and a lost one would otherwise need the server again.
        CiUpdateGateVault.restoreIfEligible(this)
    }


    private fun configureMobileEntitlement() {
        MobileEntitlementRuntime.configure(
            MobileEntitlementConfig(
                apiOrigin = BuildConfig.MOBILE_ENTITLEMENT_API_ORIGIN,
                signingPublicJwk = BuildConfig.MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK,
                appId = "xinyi-relay",
                channel = BuildConfig.MOBILE_ENTITLEMENT_CHANNEL,
                enforced = BuildConfig.MOBILE_ENTITLEMENT_ENFORCED,
            ),
            bridge = object : MobileEntitlementBridge {
                override fun publish(context: android.content.Context, state: MobileEntitlementPublishedState): Boolean =
                    kotlinx.coroutines.runBlocking {
                        io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore.batchEdit(context) {
                            setBoolean(
                                io.github.magisk317.relay.contract.constant.RelayPrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                                state.automationAllowed,
                            )
                            setString(
                                io.github.magisk317.relay.contract.constant.RelayPrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN,
                                state.entitlementToken.orEmpty(),
                            )
                        }
                        io.github.magisk317.relay.android.prefs.HookPreferenceMirror.publish(context)
                    }

                override fun log(message: String, vararg args: Any?) {
                    io.github.magisk317.xposed.logging.XLog.i(message, *args)
                }
            },
        )
    }

    private fun registerEntitlementForegroundRefresh() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                if (startedActivityCount == 0) {
                    if (entitlementForegroundPrimed) {
                        applicationScope.launch {
                            runCatching { MobileEntitlementCoordinator.refresh(this@SmsCodeApplication) }
                                .onFailure { error ->
                                    io.github.magisk317.xposed.logging.XLog.w(
                                        message = "entitlement refresh on foreground failed: ${error.message}",
                                        error,
                                    )
                                }
                        }
                    } else {
                        // initialize() covers the first process start.
                        entitlementForegroundPrimed = true
                    }
                }
                startedActivityCount += 1
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
