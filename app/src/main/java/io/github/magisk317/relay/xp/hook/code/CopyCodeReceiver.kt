package io.github.magisk317.relay.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.common.utils.ClipboardUtils
import io.github.magisk317.smscode.core.utils.XLog

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver private constructor() : BroadcastReceiver() {

    private var mPluginContext: Context? = null

    override fun onReceive(phoneContext: Context, intent: Intent) {
        val action = intent.action
        if (ACTION_COPY_CODE == action) {
            val smsCode = intent.getStringExtra(EXTRA_KEY_CODE)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            // cancel notification
            if (notificationId != -1) {
                val manager = phoneContext.getSystemService(
                    Context.NOTIFICATION_SERVICE,
                ) as android.app.NotificationManager?
                manager?.cancel(notificationId)
            }
            // copy to clipboard
            smsCode?.let {
                ClipboardUtils.copyToClipboard(phoneContext, it)
                // show feedback via log (no in-app snackbar in xposed runtime)
                val pluginContext = createSmsCodeAppContext(phoneContext)
                logCopy(pluginContext, it)
            }
        }
    }

    private fun createSmsCodeAppContext(phoneContext: Context): Context? {
        if (mPluginContext == null) {
            try {
                mPluginContext = phoneContext.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            } catch (ignored: Exception) {
                // ignore
            }
        }
        return mPluginContext
    }

    private fun logCopy(pluginContext: Context?, smsCode: String) {
        val message = pluginContext?.getString(R.string.prompt_sms_code_copied, smsCode)
            ?: "SMS code copied: $smsCode"
        XLog.i(message)
    }

    companion object {
        private const val ACTION_COPY_CODE = "${BuildConfig.APPLICATION_ID}.ACTION_COPY_CODE"
        private const val EXTRA_KEY_CODE = "extra_key_code"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private val instance: CopyCodeReceiver by lazy { CopyCodeReceiver() }

        @JvmStatic
        fun createIntent(smsCode: String?, notificationId: Int): Intent {
            val intent = Intent(ACTION_COPY_CODE)
            intent.putExtra(EXTRA_KEY_CODE, smsCode)
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return intent
        }

        @JvmStatic
        fun registerMe(context: Context) {
            val filter = IntentFilter()
            filter.addAction(ACTION_COPY_CODE)
            ContextCompat.registerReceiver(context, instance, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }
}
