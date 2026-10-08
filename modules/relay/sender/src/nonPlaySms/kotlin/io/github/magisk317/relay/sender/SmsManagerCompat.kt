package io.github.magisk317.relay.sender

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * SmsManager gates for the standard send path: context-service lookups on S+,
 * the deprecated statics otherwise. Kept module-local because :relay:android
 * already depends on :relay:sender and cannot host it.
 */
internal object SmsManagerCompat {

    fun default(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: deprecatedDefault()
        } else {
            deprecatedDefault()
        }

    fun forSubscriptionId(context: Context, subscriptionId: Int): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

    @Suppress("DEPRECATION")
    private fun deprecatedDefault(): SmsManager = SmsManager.getDefault()
}
