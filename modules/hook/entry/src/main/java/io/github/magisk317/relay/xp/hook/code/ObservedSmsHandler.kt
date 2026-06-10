package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.XpSharedRuntimeGate
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.ObservedSmsHandler as SharedObservedSmsHandler
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ScheduledExecutorService

internal class ObservedSmsHandler(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val actionExecutor: ScheduledExecutorService? = null,
    private val runtimeRecordFacadeProvider: (() -> XpRecordFacade)? = null,
    private val settingsLoader: (Context) -> SmsCodePostParseCoordinator.Settings = SmsCodePlanFactory::loadSettings,
    private val planFactory: (
        SmsCodePostParseCoordinator.Settings,
    ) -> SmsCodePostParseCoordinator.ObservedSmsPlan = SmsCodePostParseCoordinator::createObservedSmsPlan,
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
    private val smsEnricher: (Context, ObservedInboxScanRecord) -> SmsMsg = { context, record ->
        XpDispatchCoordinator.enrichObservedSms(
            phoneContext = context,
            sender = record.sender,
            body = record.body,
            date = record.date,
            smsCode = record.code,
            simSlot = record.simSlot,
            subId = record.subId,
        )
    },
    private val dispatcher: (
        Context,
        Context,
        SmsMsg,
        String,
        SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) -> Unit = { pluginContext, phoneContext, smsMsg, eventId, plan ->
        SmsCodeActionDispatcher.dispatchObservedSmsActions(
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
        settingsLoader = settingsLoader,
        planFactory = planFactory,
        moduleEnabledReader = moduleEnabledReader,
        conflictSuppressor = conflictSuppressor,
        sharedGateClaimer = { context, fileName, key, windowMs, maxEntries ->
            sharedGateClaimer(context, fileName, key, windowMs, maxEntries).toShared()
        },
        roleStateLogger = roleStateLogger,
        duplicateChecker = { settings, sender, body, date ->
            (duplicateChecker ?: ::defaultDuplicateCheck)(settings, sender, body, date)
        },
        smsEnricher = smsEnricher,
        dispatcher = { pluginContext, phoneContext, smsMsg, eventId, plan ->
            dispatcher(pluginContext, phoneContext, smsMsg, eventId, plan)
        },
        currentTimeMillis = currentTimeMillis,
    )

    fun handle(record: ObservedInboxScanRecord): Outcome {
        repairRouting(record)
        val outcome = delegate.handle(record)
        return Outcome(
            eventId = outcome.eventId,
            decision = outcome.decision,
            dispatched = outcome.dispatched,
        )
    }

    fun repairRouting(record: ObservedInboxScanRecord): Boolean {
        if (record.simSlot < 0 && record.subId <= 0) {
            return false
        }
        val runtimeRecordFacade = runtimeRecordFacadeProvider?.invoke() ?: XpRecordFacade(pluginContext)
        val timestamp = if (record.date > 0) record.date else currentTimeMillis()
        return runBlocking {
            runCatching {
                runtimeRecordFacade.backfillSmsRouting(
                    sender = record.sender,
                    body = record.body,
                    date = timestamp,
                    simSlot = record.simSlot,
                    subId = record.subId,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                )
            }.getOrDefault(false)
        }
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
}
