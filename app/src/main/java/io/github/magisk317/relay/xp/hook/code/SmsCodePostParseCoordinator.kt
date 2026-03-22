package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.impl.AutoInputAction
import io.github.magisk317.relay.xp.hook.code.action.impl.ForwardAction
import io.github.magisk317.relay.xp.hook.code.action.impl.RecordSmsAction
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal object SmsCodePostParseCoordinator {
    fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
    ) {
        AutoInputAction(pluginContext, phoneContext, smsMsg).call()
    }

    fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
    ) {
        executor.schedule(
            AutoInputAction(pluginContext, phoneContext, smsMsg),
            delayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
    ) {
        RecordSmsAction(pluginContext, phoneContext, smsMsg, eventId).call()
    }

    fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
    ) {
        executor.schedule(
            RecordSmsAction(pluginContext, phoneContext, smsMsg, eventId),
            0,
            TimeUnit.MILLISECONDS,
        )
    }

    fun scheduleForward(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        smsIntent: Intent?,
        eventId: String,
        delayMs: Long,
    ) {
        executor.schedule(
            ForwardAction(
                pluginContext,
                phoneContext,
                smsMsg,
                smsIntent,
                eventId,
            ),
            delayMs,
            TimeUnit.MILLISECONDS,
        )
    }
}
