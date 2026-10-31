package io.github.magisk317.relay.android.data.db

import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardFilterRuleEntity
import io.github.magisk317.forward.db.ForwardRouteRuleEntity
import io.github.magisk317.forward.db.ForwardScheduledTaskEntity
import io.github.magisk317.forward.db.ForwardSenderEntity
import io.github.magisk317.forward.db.ForwardEventEntity
import io.github.magisk317.forward.db.ForwardAppInfoEntity
import io.github.magisk317.forward.db.ForwardRuleEntity
import io.github.magisk317.forward.db.SenderDispatchLogEntity
import io.github.magisk317.relay.android.data.db.legacy.LegacyConfigRows
import io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyForwardFilterRule as ForwardFilterRule
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyNotifyRouteRule as NotifyRouteRule
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySender as Sender
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySmsMsg as SmsMsg
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.forward.contract.json.RelayJson
import io.github.magisk317.forward.engine.model.Sender as ForwardSender
import kotlinx.serialization.builtins.ListSerializer

/**
 * One-shot import of the legacy mixed schema into [ForwardDatabase].
 *
 * The pure projection/parity functions are separated from the Room I/O so the
 * mapping can be unit tested on the JVM. The old `sms_msg` rows keep living
 * until the product ships the field strip; the import only fills the new
 * database and proves parity.
 */
/** Outcome of the idempotent configuration import. */
data class ConfigImportReport(
    val imported: List<String>,
    val skipped: List<String>,
    val mismatches: List<String>,
) {
    val ok: Boolean get() = mismatches.isEmpty()

    fun summary(): String = buildString {
        append("ok=").append(ok)
        if (imported.isNotEmpty()) append(" imported=").append(imported.joinToString(","))
        if (skipped.isNotEmpty()) append(" skipped=").append(skipped.joinToString(","))
        if (mismatches.isNotEmpty()) append(" mismatches=").append(mismatches.joinToString(";"))
    }
}

object ForwardLegacyImporter {

    data class ImportReport(
        val eventsImported: Int,
        val dispatchLogsImported: Int,
        val parity: ParityReport,
    )

    data class ParityReport(
        val eventCountMatches: Boolean,
        val dispatchLogCountMatches: Boolean,
        val firstEventMismatch: String? = null,
        val firstDispatchLogMismatch: String? = null,
    ) {
        val ok: Boolean
            get() = eventCountMatches && dispatchLogCountMatches &&
                firstEventMismatch == null && firstDispatchLogMismatch == null
    }

    /** Projects one legacy mixed row onto the forward-domain event record. */
    fun projectEvent(msg: SmsMsg): ForwardEventEntity = ForwardEventEntity(
        id = msg.id ?: 0,
        eventId = "",
        msgType = msg.msgType,
        sender = msg.sender,
        body = msg.body,
        date = msg.date,
        processedTime = msg.processedTime,
        company = msg.company,
        smsCode = msg.smsCode,
        packageName = msg.packageName,
        notifyChannelId = msg.notifyChannelId,
        callType = msg.callType,
        simSlot = msg.simSlot,
        subId = msg.subId,
        contactName = msg.contactName,
        phoneArea = msg.phoneArea,
        sessionKey = msg.sessionKey,
        forwardStatus = msg.forwardStatus,
        forwardTarget = msg.forwardTarget,
        forwardMessage = msg.forwardMessage,
        forwardTime = msg.forwardTime,
    )

    /**
     * Projects a legacy dispatch log row; [recordIdMap] translates the legacy
     * sms_msg row id to the forward_event id written by the event import.
     */
    fun projectDispatchLog(
        log: io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog,
        recordIdMap: Map<Long, Long>,
    ): SenderDispatchLogEntity = SenderDispatchLogEntity(
        recordId = log.recordId?.let { recordIdMap[it] },
        senderId = log.senderId,
        senderType = log.senderType,
        msgType = log.msgType,
        success = log.success,
        createdAt = log.createdAt,
    )

    fun checkParity(
        legacy: List<SmsMsg>,
        imported: List<ForwardEventEntity>,
        legacyLogs: List<io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog>,
        importedLogs: List<SenderDispatchLogEntity>,
        recordIdMap: Map<Long, Long>,
    ): ParityReport {
        val eventCountMatches = legacy.size == imported.size
        val logCountMatches = legacyLogs.size == importedLogs.size
        val importedById = imported.associateBy { it.id }
        val firstEventMismatch = legacy.firstNotNullOfOrNull { msg ->
            val expected = projectEvent(msg)
            val expectedId = msg.id?.let { recordIdMap[it] } ?: expected.id
            val actual = importedById[expectedId]
            when {
                actual == null -> "missing event for legacy id=${msg.id}"
                actual.copy(id = expected.id) != expected -> "event mismatch id=${msg.id}"
                else -> null
            }
        } ?: if (!eventCountMatches) {
            "event count ${legacy.size}!=${imported.size}"
        } else {
            null
        }
        val firstDispatchLogMismatch = legacyLogs.firstNotNullOfOrNull { log ->
            val expected = projectDispatchLog(log, recordIdMap)
            val actual = importedLogs.firstOrNull {
                it.recordId == expected.recordId &&
                    it.senderId == expected.senderId &&
                    it.senderType == expected.senderType &&
                    it.msgType == expected.msgType &&
                    it.success == expected.success &&
                    it.createdAt == expected.createdAt
            }
            when {
                actual == null ->
                    "dispatch log senderId=${log.senderId} createdAt=${log.createdAt} " +
                        "recordId=${expected.recordId} not imported"
                actual.copy(id = expected.id) != expected ->
                    "dispatch log mismatch recordId=${expected.recordId} senderId=${expected.senderId}"
                else -> null
            }
        } ?: if (!logCountMatches) {
            "dispatch log count ${legacyLogs.size}!=${importedLogs.size}"
        } else {
            null
        }
        return ParityReport(
            eventCountMatches = eventCountMatches,
            dispatchLogCountMatches = logCountMatches,
            firstEventMismatch = firstEventMismatch,
            firstDispatchLogMismatch = firstDispatchLogMismatch,
        )
    }


    fun projectSender(sender: Sender): ForwardSenderEntity = ForwardSenderEntity(
        id = sender.id, type = sender.type, name = sender.name, jsonSetting = sender.jsonSetting,
        status = sender.status, time = sender.time, receiveCode = sender.receiveCode,
        receiveNonCode = sender.receiveNonCode, receiveAppNotify = sender.receiveAppNotify,
        receiveCallNotify = sender.receiveCallNotify, activeScheduleJson = sender.activeScheduleJson,
        priority = sender.priority, customTemplate = sender.customTemplate,
    )

    fun projectRouteRule(rule: NotifyRouteRule): ForwardRouteRuleEntity = ForwardRouteRuleEntity(
        id = rule.id, scope = rule.scope, packageName = rule.packageName,
        senderId = rule.senderId, updateTime = rule.updateTime,
    )

    fun projectFilterRule(rule: ForwardFilterRule): ForwardFilterRuleEntity = ForwardFilterRuleEntity(
        id = rule.id, msgType = rule.msgType, scopeType = rule.scopeType, scopeKey = rule.scopeKey,
        senderId = rule.senderId, policy = rule.policy, matchMode = rule.matchMode,
        pattern = rule.pattern, enabled = rule.enabled, updateTime = rule.updateTime,
    )

    fun projectScheduledTask(
        task: io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity,
    ): ForwardScheduledTaskEntity = ForwardScheduledTaskEntity(
        id = task.id, name = task.name, taskType = task.taskType, cronExpression = task.cronExpression,
        simSlot = task.simSlot, mobiles = task.mobiles, content = task.content,
        status = task.status, lastRunTime = task.lastRunTime, nextRunTime = task.nextRunTime,
        createdAt = task.createdAt,
    )

    fun projectAppInfo(info: io.github.magisk317.relay.android.data.db.legacy.entity.LegacyAppInfo): ForwardAppInfoEntity = ForwardAppInfoEntity(
        packageName = info.packageName, label = info.label, blocked = info.blocked,
        forwarding = info.forwarding, forwardingConfigured = info.forwardingConfigured,
        notifyTemplate = info.notifyTemplate,
    )

    fun projectRule(rule: io.github.magisk317.relay.android.data.db.legacy.entity.LegacyRule): ForwardRuleEntity = ForwardRuleEntity(
        id = rule.id, type = rule.type, filed = rule.filed, check = rule.check, value = rule.value,
        senderId = rule.senderId, smsTemplate = rule.smsTemplate, regexReplace = rule.regexReplace,
        simSlot = rule.simSlot, status = rule.status, time = rule.time,
        senderListJson = RelayJson.encode(ListSerializer(ForwardSender.serializer()), rule.senderList.map { it.toDomain() }),
        senderLogic = rule.senderLogic, silentPeriodStart = rule.silentPeriodStart,
        silentPeriodEnd = rule.silentPeriodEnd, silentDayOfWeek = rule.silentDayOfWeek, title = rule.title,
    )

    /**
     * Imports each configuration table only while its forward counterpart is
     * still empty, so a partial or interrupted import can be retried without
     * overwriting newer writes. Parity is proven per table by row count and by
     * legacy identity (primary key, or package name for app info).
     */
    suspend fun importConfigIfNeeded(rows: LegacyConfigRows, target: ForwardDatabase): ConfigImportReport {
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val mismatches = mutableListOf<String>()

        // Spelled out per category with no local lambdas: K2 2.5.0-Beta2 fails to
        // infer through generic/suspend local helpers here (CANNOT_INFER_PARAMETER_TYPE
        // even with explicit type arguments). The semantics are unchanged: import
        // only while the forward table is still empty, then prove parity by row
        // count and content.
        val senders: List<ForwardSenderEntity> = rows.senders.map(::projectSender)
        if (senders.isEmpty()) {
            // nothing to import
        } else if (target.forwardSenderDao().all().isNotEmpty()) {
            skipped += "senders"
        } else {
            target.forwardSenderDao().upsertAll(senders)
            if (senders.toSet() == target.forwardSenderDao().all().toSet()) {
                imported += "senders"
            } else {
                mismatches += "senders parity expected=${senders.size} actual=${target.forwardSenderDao().all().size}"
            }
        }

        val routes: List<ForwardRouteRuleEntity> = rows.routes.map(::projectRouteRule)
        if (routes.isEmpty()) {
            // nothing to import
        } else if (target.forwardRouteRuleDao().all().isNotEmpty()) {
            skipped += "routes"
        } else {
            target.forwardRouteRuleDao().upsertAll(routes)
            if (routes.toSet() == target.forwardRouteRuleDao().all().toSet()) {
                imported += "routes"
            } else {
                mismatches += "routes parity expected=${routes.size} actual=${target.forwardRouteRuleDao().all().size}"
            }
        }

        val filters: List<ForwardFilterRuleEntity> = rows.filters.map(::projectFilterRule)
        if (filters.isEmpty()) {
            // nothing to import
        } else if (target.forwardFilterRuleDao().all().isNotEmpty()) {
            skipped += "filters"
        } else {
            target.forwardFilterRuleDao().upsertAll(filters)
            if (filters.toSet() == target.forwardFilterRuleDao().all().toSet()) {
                imported += "filters"
            } else {
                mismatches += "filters parity expected=${filters.size} actual=${target.forwardFilterRuleDao().all().size}"
            }
        }

        val tasks: List<ForwardScheduledTaskEntity> = rows.tasks.map(::projectScheduledTask)
        if (tasks.isEmpty()) {
            // nothing to import
        } else if (target.forwardScheduledTaskDao().all().isNotEmpty()) {
            skipped += "scheduled"
        } else {
            target.forwardScheduledTaskDao().upsertAll(tasks)
            if (tasks.toSet() == target.forwardScheduledTaskDao().all().toSet()) {
                imported += "scheduled"
            } else {
                mismatches += "scheduled parity expected=${tasks.size} actual=${target.forwardScheduledTaskDao().all().size}"
            }
        }

        val appInfos: List<ForwardAppInfoEntity> = rows.appInfos.map(::projectAppInfo)
        if (appInfos.isEmpty()) {
            // nothing to import
        } else if (target.forwardAppInfoDao().all().isNotEmpty()) {
            skipped += "appInfo"
        } else {
            target.forwardAppInfoDao().upsertAll(appInfos)
            if (appInfos.toSet() == target.forwardAppInfoDao().all().toSet()) {
                imported += "appInfo"
            } else {
                mismatches += "appInfo parity expected=${appInfos.size} actual=${target.forwardAppInfoDao().all().size}"
            }
        }

        val rules: List<ForwardRuleEntity> = rows.rules.map(::projectRule)
        if (rules.isEmpty()) {
            // nothing to import
        } else if (target.forwardRuleDao().all().isNotEmpty()) {
            skipped += "rules"
        } else {
            target.forwardRuleDao().upsertAll(rules)
            if (rules.toSet() == target.forwardRuleDao().all().toSet()) {
                imported += "rules"
            } else {
                mismatches += "rules parity expected=${rules.size} actual=${target.forwardRuleDao().all().size}"
            }
        }

        return ConfigImportReport(imported = imported, skipped = skipped, mismatches = mismatches)
    }

    /** Room I/O shell: import everything and prove parity. Run once per empty target. */
    suspend fun importAll(msgs: List<SmsMsg>, logs: List<SenderDispatchLog>, target: ForwardDatabase): ImportReport {
        val events = msgs.map { projectEvent(it) }
        val legacyIds = msgs.mapNotNull { it.id }
        val insertedIds = target.forwardEventDao().insertAll(events)
        val recordIdMap = legacyIds.zip(insertedIds).toMap()
        val dispatchLogs = logs.map { projectDispatchLog(it, recordIdMap) }
        target.senderDispatchLogDao().insertAll(dispatchLogs)
        val importedEvents = insertedIds.mapNotNull { target.forwardEventDao().byId(it) }
        val importedLogs = target.senderDispatchLogDao().all()
        val parity = checkParity(msgs, importedEvents, logs, importedLogs, recordIdMap)
        return ImportReport(
            eventsImported = events.size,
            dispatchLogsImported = dispatchLogs.size,
            parity = parity,
        )
    }
}
