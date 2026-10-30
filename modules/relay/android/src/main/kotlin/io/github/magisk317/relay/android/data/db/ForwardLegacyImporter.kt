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
import io.github.magisk317.smscode.db.entity.ForwardFilterRule
import io.github.magisk317.smscode.db.entity.NotifyRouteRule
import io.github.magisk317.smscode.db.entity.Sender
import io.github.magisk317.smscode.db.entity.SmsMsg
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
        val byLegacyId = imported.associateBy { it.id }
        val firstEventMismatch = legacy.firstNotNullOfOrNull { msg ->
            val projected = projectEvent(msg)
            val actual = msg.id?.let { byLegacyId[it] }
            when {
                actual == null -> "missing event for legacy id=${msg.id}"
                actual.msgType != projected.msgType -> "msgType ${actual.msgType} != ${projected.msgType} id=${msg.id}"
                actual.date != projected.date -> "date ${actual.date} != ${projected.date} id=${msg.id}"
                actual.forwardStatus != projected.forwardStatus -> "forwardStatus ${actual.forwardStatus} != ${projected.forwardStatus} id=${msg.id}"
                actual.sender != projected.sender -> "sender mismatch id=${msg.id}"
                actual.body != projected.body -> "body mismatch id=${msg.id}"
                else -> null
            }
        }
        val importedBySignature = importedLogs.groupBy { Triple(it.senderId, it.createdAt, it.success) }
        val firstDispatchLogMismatch = legacyLogs.firstNotNullOfOrNull { log ->
            val expectedRecordId = log.recordId?.let { recordIdMap[it] }
            val candidates = importedBySignature[Triple(log.senderId, log.createdAt, log.success)].orEmpty()
            if (candidates.none { it.recordId == expectedRecordId }) {
                "dispatch log senderId=${log.senderId} createdAt=${log.createdAt} recordId=$expectedRecordId not imported"
            } else {
                null
            }
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

    fun projectScheduledTask(task: io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity): ForwardScheduledTaskEntity = ForwardScheduledTaskEntity(
        id = task.id, name = task.name, taskType = task.taskType, cronExpression = task.cronExpression,
        simSlot = task.simSlot, mobiles = task.mobiles, content = task.content,
        status = task.status, lastRunTime = task.lastRunTime, nextRunTime = task.nextRunTime,
        createdAt = task.createdAt,
    )

    fun projectAppInfo(info: io.github.magisk317.smscode.db.entity.AppInfo): ForwardAppInfoEntity = ForwardAppInfoEntity(
        packageName = info.packageName, label = info.label, blocked = info.blocked,
        forwarding = info.forwarding, forwardingConfigured = info.forwardingConfigured,
        notifyTemplate = info.notifyTemplate,
    )

    fun projectRule(rule: io.github.magisk317.smscode.db.entity.Rule): ForwardRuleEntity = ForwardRuleEntity(
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
    suspend fun importConfigIfNeeded(legacy: AppDatabase, target: ForwardDatabase): ConfigImportReport {
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val mismatches = mutableListOf<String>()

        suspend fun <T> applyCategory(
            name: String,
            rows: List<T>,
            targetCount: suspend () -> Int,
            write: suspend (List<T>) -> Unit,
        ) {
            when {
                rows.isEmpty() -> Unit
                targetCount() > 0 -> skipped += name
                else -> {
                    write(rows)
                    val after = targetCount()
                    if (after == rows.size) {
                        imported += name
                    } else {
                        mismatches += "$name rows ${rows.size}!=$after"
                    }
                }
            }
        }

        val senders = legacy.senderDao().getAll().map { projectSender(it) }
        applyCategory("senders", senders, { target.forwardSenderDao().count() }) {
            target.forwardSenderDao().upsertAll(it)
        }
        val routes = legacy.notifyRouteRuleDao().getAll().map { projectRouteRule(it) }
        applyCategory("routes", routes, { target.forwardRouteRuleDao().count() }) {
            target.forwardRouteRuleDao().upsertAll(it)
        }
        val filters = legacy.forwardFilterRuleDao().getAll().map { projectFilterRule(it) }
        applyCategory("filters", filters, { target.forwardFilterRuleDao().count() }) {
            target.forwardFilterRuleDao().upsertAll(it)
        }
        val tasks = legacy.scheduledTaskDao().getAll().map { projectScheduledTask(it) }
        applyCategory("scheduled", tasks, { target.forwardScheduledTaskDao().count() }) {
            target.forwardScheduledTaskDao().upsertAll(it)
        }
        val appInfos = legacy.appInfoDao().getAll().map { projectAppInfo(it) }
        applyCategory("appInfo", appInfos, { target.forwardAppInfoDao().count() }) {
            target.forwardAppInfoDao().upsertAll(it)
        }
        val rules = legacy.ruleDao().getAll().map { projectRule(it) }
        applyCategory("rules", rules, { target.forwardRuleDao().count() }) {
            target.forwardRuleDao().upsertAll(it)
        }

        val missingSenderIds = senders.map { it.id }.toSet() - target.forwardSenderDao().all().map { it.id }.toSet()
        if (missingSenderIds.isNotEmpty()) mismatches += "sender missing ${missingSenderIds.size}"
        val missingRouteIds = routes.map { it.id }.toSet() - target.forwardRouteRuleDao().all().map { it.id }.toSet()
        if (missingRouteIds.isNotEmpty()) mismatches += "route missing ${missingRouteIds.size}"
        val missingFilterIds = filters.map { it.id }.toSet() - target.forwardFilterRuleDao().all().map { it.id }.toSet()
        if (missingFilterIds.isNotEmpty()) mismatches += "filter missing ${missingFilterIds.size}"
        val missingTaskIds = tasks.map { it.id }.toSet() - target.forwardScheduledTaskDao().all().map { it.id }.toSet()
        if (missingTaskIds.isNotEmpty()) mismatches += "scheduled missing ${missingTaskIds.size}"
        val missingPackages = appInfos.map { it.packageName }.toSet() -
            target.forwardAppInfoDao().all().map { it.packageName }.toSet()
        if (missingPackages.isNotEmpty()) mismatches += "app info missing ${missingPackages.size}"
        val missingRuleIds = rules.map { it.id }.toSet() - target.forwardRuleDao().all().map { it.id }.toSet()
        if (missingRuleIds.isNotEmpty()) mismatches += "rule missing ${missingRuleIds.size}"

        return ConfigImportReport(imported = imported, skipped = skipped, mismatches = mismatches)
    }

    /** Room I/O shell: import everything and prove parity. Run once per empty target. */
    suspend fun importAll(legacy: AppDatabase, target: ForwardDatabase): ImportReport {
        val msgs = legacy.smsMsgDao().getAll()
        val logs = legacy.senderDispatchLogDao().getAllForImport()
        val events = msgs.map { projectEvent(it) }
        val legacyIds = msgs.mapNotNull { it.id }
        val insertedIds = target.forwardEventDao().insertAll(events)
        val recordIdMap = legacyIds.zip(insertedIds).toMap()
        val dispatchLogs = logs.map { projectDispatchLog(it, recordIdMap) }
        target.senderDispatchLogDao().insertAll(dispatchLogs)
        val importedEvents = insertedIds.mapNotNull { target.forwardEventDao().byId(it) }
        val importedLogs = recordIdMap.values.flatMap { target.senderDispatchLogDao().byRecordId(it) }
        val parity = checkParity(msgs, importedEvents, logs, importedLogs, recordIdMap)
        return ImportReport(
            eventsImported = events.size,
            dispatchLogsImported = dispatchLogs.size,
            parity = parity,
        )
    }
}
