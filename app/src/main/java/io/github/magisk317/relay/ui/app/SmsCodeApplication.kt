package io.github.magisk317.relay.ui.app

import android.app.Application
import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.di.appDependencyModule
import io.github.magisk317.relay.di.coreModule
import io.github.magisk317.relay.di.uiModule
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
            modules(coreModule, uiModule, appDependencyModule)
        }

        XposedServiceBridge.initialize(this, applicationScope)
        val initializers = getKoin().getAll<AppInitializer>()
        initializers.forEach { it.init(this) }
    }
}
