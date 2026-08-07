package io.github.magisk317.relay.ui.app

import android.app.Application
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.app.InfrastructureInitializer
import io.github.magisk317.relay.di.appDependencyModule
import io.github.magisk317.relay.di.billingModule
import io.github.magisk317.relay.di.coreModule
import io.github.magisk317.relay.di.uiModule
import io.github.magisk317.relay.entitlement.MobileEntitlementCoordinator
import io.github.magisk317.relay.entitlement.mobileEntitlementGoogleSignInModule
import com.magisk317.mobile.entitlement.MobileEntitlementBridge
import com.magisk317.mobile.entitlement.MobileEntitlementConfig
import com.magisk317.mobile.entitlement.MobileEntitlementRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SmsCodeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@SmsCodeApplication)
            modules(coreModule, billingModule, uiModule, appDependencyModule, mobileEntitlementGoogleSignInModule)
        }

        val koin = getKoin()
        // Register Xposed listener first so Enhanced can settle before any Standard FGS start.
        XposedServiceBridge.initialize(this, applicationScope)
        koin.get<InfrastructureInitializer>().init(this)
        val initializers = koin.getAll<AppInitializer>().filterNot { it is InfrastructureInitializer }
        initializers.forEach { it.init(this) }
        MobileEntitlementRuntime.configure(
            MobileEntitlementConfig(
                apiOrigin = BuildConfig.MOBILE_ENTITLEMENT_API_ORIGIN,
                signingPublicJwk = BuildConfig.MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK,
                appId = "xinyi-relay",
                channel = BuildConfig.MOBILE_ENTITLEMENT_CHANNEL,
                enforced = BuildConfig.MOBILE_ENTITLEMENT_ENFORCED,
            ),
            bridge = object : MobileEntitlementBridge {
                override fun publish(context: android.content.Context, allowed: Boolean) {
                    kotlinx.coroutines.runBlocking {
                        io.github.magisk317.relay.android.prefs.AppPreferencesDataStore.setBoolean(
                            context,
                            io.github.magisk317.relay.contract.constant.RelayPrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                            allowed,
                        )
                        io.github.magisk317.relay.android.prefs.HookPreferenceMirror.publish(context)
                    }
                }

                override fun log(message: String, vararg args: Any?) {
                    io.github.magisk317.relay.android.common.utils.XLog.i(message, *args)
                }
            },
        )
        MobileEntitlementCoordinator.initialize(this, applicationScope)
    }
}
