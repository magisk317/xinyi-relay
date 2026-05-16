package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.XpSharedRuntimeGate
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.verification.ObservedSmsHandler as SharedObservedSmsHandler
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator as SharedSmsCodePostParseCoordinator
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ScheduledExecutorService

internal class ObservedSmsHandler(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val actionExecutor: ScheduledExecutorService? = null,
    private val runtimeRecordFacadeProvider: (() -> XpRecordFacade)? = null,
    private val settingsLoader: (Context) -> SmsCodePostParseCoordinator.Settings = SmsCodePostParseCoordinator::loadSettings,
    private val planFactory: (SmsCodePostParseCoordinator.Settings) -> SmsCodePostParseCoordinator.ObservedSmsPlan =
        SmsCodePostParseCoordinator::createObservedSmsPlan,
    private val moduleEnabledReader: (Context) -> Boolean = XpPrefs::isEnabled,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val sharedGateClaimer: (Context, String, String, Long, Int) -> XpSharedRuntimeGate.ClaimResult =
        { context, fileName, key, windowMs, maxEntries ->
            XpSharedRuntimeGate.claimWithinWindow(
                context = context,
                fileName = fileName,
                key = key,
                windowMs = windowMs,
                maxEntries = maxEntries,
            )
        },
    private val roleStateLogger: (String) -> Unit = {},
    private val duplicateChecker: ((SmsCodePostParseCoordinator.Settings, String, String, Long) -> Boolean)? = null,
    private val smsEnricher: (Context, String, String, Long, String) -> SmsMsg = { context, sender, body, date, code ->
        XpDispatchCoordinator.enrichObservedSms(
            phoneContext = context,
            sender = sender,
            body = body,
            date = date,
            smsCode = code,
        )
    },
    private val dispatcher: (
        Context,
        Context,
        SmsMsg,
        String,
        SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) -> Unit = { pluginContext, phoneContext, smsMsg, eventId, plan ->
        SmsCodePostParseCoordinator.dispatchObservedSmsActions(
            executor = actionExecutor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
        )
    },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    data class Outcome(
        val eventId: String,
        val decision: SmsInboxObserverDecision.Decision,
        val dispatched: Boolean,
    )

    private val delegate = SharedObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        settingsLoader = { context -> settingsLoader(context).toShared() },
        planFactory = SharedSmsCodePostParseCoordinator::createObservedSmsPlan,
        moduleEnabledReader = moduleEnabledReader,
        conflictSuppressor = conflictSuppressor,
        sharedGateClaimer = { context, fileName, key, windowMs, maxEntries ->
            sharedGateClaimer(context, fileName, key, windowMs, maxEntries).toShared()
        },
        roleStateLogger = roleStateLogger,
        duplicateChecker = { settings, sender, body, date ->
            (duplicateChecker ?: ::defaultDuplicateCheck)(settings.toLocal(), sender, body, date)
        },
        smsEnricher = smsEnricher,
        dispatcher = { pluginContext, phoneContext, smsMsg, eventId, plan ->
            dispatcher(pluginContext, phoneContext, smsMsg, eventId, plan.toLocal())
        },
        currentTimeMillis = currentTimeMillis,
    )

    fun handle(record: ObservedInboxScanRecord): Outcome {
        val outcome = delegate.handle(record)
        return Outcome(
            eventId = outcome.eventId,
            decision = outcome.decision,
            dispatched = outcome.dispatched,
        )
    }

    private fun defaultDuplicateCheck(
        settings: SmsCodePostParseCoordinator.Settings,
        sender: String,
        body: String,
        date: Long,
    ): Boolean {
        if (!settings.deduplicateSmsEnabled) {
            return false
        }
        val runtimeRecordFacade = runtimeRecordFacadeProvider?.invoke() ?: XpRecordFacade(pluginContext)
        val timestamp = if (date > 0) date else currentTimeMillis()
        return runBlocking {
            runCatching {
                runtimeRecordFacade.isDuplicateSms(
                    sender = sender,
                    body = body,
                    date = timestamp,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                )
            }.getOrDefault(false)
        }
    }

    private fun XpSharedRuntimeGate.ClaimResult.toShared(): SharedObservedSmsHandler.ClaimResult {
        return SharedObservedSmsHandler.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
        )
    }

    private fun SmsCodePostParseCoordinator.Settings.toShared(): SharedSmsCodePostParseCoordinator.Settings {
        return SharedSmsCodePostParseCoordinator.Settings(
            showNotification = showNotification,
            autoCancelNotification = autoCancelNotification,
            notificationRetentionMs = notificationRetentionMs,
            autoInputEnabled = autoInputEnabled,
            autoInputDelayMs = autoInputDelayMs,
            copyToClipboardEnabled = copyToClipboardEnabled,
            showToast = showToast,
            recordSmsEnabled = recordSmsEnabled,
            blockSmsEnabled = blockSmsEnabled,
            markAsReadEnabled = markAsReadEnabled,
            deleteSmsEnabled = deleteSmsEnabled,
            deduplicateSmsEnabled = deduplicateSmsEnabled,
        )
    }

    private fun SharedSmsCodePostParseCoordinator.Settings.toLocal(): SmsCodePostParseCoordinator.Settings {
        return SmsCodePostParseCoordinator.Settings(
            showNotification = showNotification,
            autoCancelNotification = autoCancelNotification,
            notificationRetentionMs = notificationRetentionMs,
            autoInputEnabled = autoInputEnabled,
            autoInputDelayMs = autoInputDelayMs,
            copyToClipboardEnabled = copyToClipboardEnabled,
            showToast = showToast,
            recordSmsEnabled = recordSmsEnabled,
            blockSmsEnabled = blockSmsEnabled,
            markAsReadEnabled = markAsReadEnabled,
            deleteSmsEnabled = deleteSmsEnabled,
            deduplicateSmsEnabled = deduplicateSmsEnabled,
        )
    }

    private fun SharedSmsCodePostParseCoordinator.ObservedSmsPlan.toLocal(): SmsCodePostParseCoordinator.ObservedSmsPlan {
        return SmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = deduplicateSmsEnabled,
            autoInputEnabled = autoInputEnabled,
            autoInputDelayMs = autoInputDelayMs,
            shouldRecord = shouldRecord,
        )
    }
}
