package io.github.magisk317.relay.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.magisk317.relay.feature.reminder.BatteryReminderForegroundMonitor
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import timber.log.Timber

class LifecycleMonitorInitializer : AppInitializer {
    private var startedActivityCount: Int = 0

    override fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount += 1
                if (startedActivityCount == 1) {
                    RootDbCatchupScheduler.stopPeriodic(reason = "app_foreground")
                    BatteryReminderForegroundMonitor.start(application)
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
                    RootDbCatchupScheduler.startPeriodic(application, reason = "app_background")
                    BatteryReminderForegroundMonitor.stop(application)
                }
            }
            
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
