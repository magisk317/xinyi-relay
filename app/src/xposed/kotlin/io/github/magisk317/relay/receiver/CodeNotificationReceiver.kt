package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.xp.hook.code.AutoCancelReceiver
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.runtime.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.runtime.verification.CodeNotificationReceiverHandler

class CodeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        CodeNotificationReceiverHandler.handleBroadcast(
            receiver = this,
            context = context,
            intent = intent,
            config = receiverConfig(context.applicationContext),
        )
    }

    private fun receiverConfig(context: Context): CodeNotificationReceiverHandler.Config {
        return CodeNotificationReceiverHandler.Config(
            source = "CodeNotificationReceiver",
            expectedAction = CodeNotificationBroadcastContract.ACTION_SHOW_CODE_NOTIFICATION,
            expectedTokenProvider = XpPrefs::getIpcToken,
            sentFromUidProvider = {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    getSentFromUid()
                } else {
                    -1
                }
            },
            channelName = context.getString(R.string.channel_name_relay_notification),
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
                groupKey = XpNotificationBridge.GROUP_KEY_RELAY_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            fallbackTitleProvider = { appContext ->
                appContext.getString(R.string.app_name)
            },
            contentTextProvider = { appContext, code ->
                appContext.getString(R.string.code_notification_content, code)
            },
            createNotificationChannel = XpNotificationBridge::createNotificationChannel,
            createCopyIntent = CopyCodeReceiver::createIntent,
            createAutoCancelIntent = AutoCancelReceiver::createIntent,
            inspectDelivery = { appContext, channelId ->
                val diagnostics = XpNotificationBridge.inspectDelivery(appContext, channelId)
                CodeNotificationReceiverHandler.DeliveryDiagnostics(
                    canPost = diagnostics.canPost,
                    summary = diagnostics.summary(),
                )
            },
        )
    }
}
