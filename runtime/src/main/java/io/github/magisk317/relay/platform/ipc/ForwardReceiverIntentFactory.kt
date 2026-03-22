package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.constant.PrefConst

object ForwardReceiverIntentFactory {
    fun newHostIntent(context: Context): Intent = Intent(PrefConst.ACTION_FORWARD_SMS).apply {
        setClassName(context.packageName, ForwardReceiver::class.java.name)
        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
}
