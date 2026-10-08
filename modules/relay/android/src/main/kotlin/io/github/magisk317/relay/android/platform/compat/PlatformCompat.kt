package io.github.magisk317.relay.android.platform.compat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.telephony.SmsManager

/**
 * Central gates for platform APIs whose shape changed across the supported
 * Android range (minSdk 28 vs TIRAMISU/S). Call sites stay suppression-free;
 * the deprecated legacy paths live only inside this object.
 */
object PlatformCompat {

    /** PackageManager.getPackageInfo with the TIRAMISU flags-object split. */
    fun getPackageInfo(pm: PackageManager, packageName: String, extraFlags: Long = 0): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(extraFlags))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, extraFlags.toInt())
        }

    /** PackageManager.getApplicationInfo with the TIRAMISU flags-object split. */
    fun getApplicationInfo(pm: PackageManager, packageName: String, extraFlags: Long = 0): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(extraFlags))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, extraFlags.toInt())
        }

    /** PackageManager.queryIntentActivities with the TIRAMISU flags-object split. */
    fun queryIntentActivities(pm: PackageManager, intent: Intent, flags: Int): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }

    /** Registers a receiver as NOT_EXPORTED on TIRAMISU+, plain otherwise. */
    fun registerReceiverNotExported(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
    ): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

    /** Default SmsManager: context service on S+, the deprecated static otherwise. */
    fun defaultSmsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: deprecatedDefaultSmsManager()
        } else {
            deprecatedDefaultSmsManager()
        }

    /** Per-subscription SmsManager: context service on S+, the deprecated static otherwise. */
    fun smsManagerForSubscriptionId(context: Context, subscriptionId: Int): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

    @Suppress("DEPRECATION")
    private fun deprecatedDefaultSmsManager(): SmsManager = SmsManager.getDefault()
}
