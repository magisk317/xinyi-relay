package io.github.magisk317.relay.forwarder.utils

import android.content.Context
import io.github.magisk317.relay.forwarder.entity.MsgInfo
import io.github.magisk317.relay.forwarder.entity.Sender
import io.github.magisk317.relay.forwarder.filter.ForwardFilterConst
import io.github.magisk317.relay.forwarder.filter.ForwardFilterEngine
import io.github.magisk317.relay.forwarder.entity.setting.BarkSetting
import io.github.magisk317.relay.forwarder.entity.setting.DingtalkGroupRobotSetting
import io.github.magisk317.relay.forwarder.entity.setting.DingtalkInnerRobotSetting
import io.github.magisk317.relay.forwarder.entity.setting.EmailSetting
import io.github.magisk317.relay.forwarder.entity.setting.FeishuAppSetting
import io.github.magisk317.relay.forwarder.entity.setting.FeishuSetting
import io.github.magisk317.relay.forwarder.entity.setting.GotifySetting
import io.github.magisk317.relay.forwarder.entity.setting.PushplusSetting
import io.github.magisk317.relay.forwarder.entity.setting.ServerchanSetting
import io.github.magisk317.relay.forwarder.entity.setting.SmsSetting
import io.github.magisk317.relay.forwarder.entity.setting.SocketSetting
import io.github.magisk317.relay.forwarder.entity.setting.TelegramSetting
import io.github.magisk317.relay.forwarder.entity.setting.UrlSchemeSetting
import io.github.magisk317.relay.forwarder.entity.setting.WebhookSetting
import io.github.magisk317.relay.forwarder.entity.setting.WeworkAgentSetting
import io.github.magisk317.relay.forwarder.entity.setting.WeworkRobotSetting
import io.github.magisk317.relay.forwarder.routing.NotifyRoutingResolver
import io.github.magisk317.relay.forwarder.routing.NotifyRoutingResult
import io.github.magisk317.relay.forwarder.utils.sender.BarkUtils
import io.github.magisk317.relay.forwarder.utils.sender.DingtalkGroupRobotUtils
import io.github.magisk317.relay.forwarder.utils.sender.DingtalkInnerRobotUtils
import io.github.magisk317.relay.forwarder.utils.sender.EmailUtils
import io.github.magisk317.relay.forwarder.utils.sender.FeishuAppUtils
import io.github.magisk317.relay.forwarder.utils.sender.FeishuUtils
import io.github.magisk317.relay.forwarder.utils.sender.GotifyUtils
import io.github.magisk317.relay.forwarder.utils.sender.PushplusUtils
import io.github.magisk317.relay.forwarder.utils.sender.ServerchanUtils
import io.github.magisk317.relay.forwarder.utils.sender.SmsUtils
import io.github.magisk317.relay.forwarder.utils.sender.SocketUtils
import io.github.magisk317.relay.forwarder.utils.sender.TelegramUtils
import io.github.magisk317.relay.forwarder.utils.sender.UrlSchemeUtils
import io.github.magisk317.relay.forwarder.utils.sender.WebhookUtils
import io.github.magisk317.relay.forwarder.utils.sender.WeworkAgentUtils
import io.github.magisk317.relay.forwarder.utils.sender.WeworkRobotUtils
import com.google.gson.Gson
import io.github.magisk317.relay.storage.BuildConfig
import io.github.magisk317.relay.common.utils.ForwardFlowLog
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.entity.SmsMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

object SendUtils {
    private const val TAG = "SendUtils"
    private const val MAX_FORWARD_MESSAGE_LEN = 2000
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class SenderDispatchResult(
        val senderName: String,
        val success: Boolean,
        val message: String,
    )

    /**
     * Entry point called from [ForwardReceiver] after receiving IPC broadcast from Xposed layer.
     * Queries all enabled Senders from Room DB and dispatches the message to each channel.
     */
    fun sendMsg(
        context: Context,
        msgInfo: MsgInfo,
        isCodeSms: Boolean = true,
        recordId: Long? = null,
        traceId: String? = null,
    ) {
        XLog.i("Dispatching MsgInfo to enabled senders: isCodeSms=%s msg=%s", isCodeSms, msgInfo)
        ForwardFlowLog.i(
            traceId,
            "SendUtils enter type=${msgInfo.type} pkg=${msgInfo.packageName.ifBlank { "<none>" }} isCodeSms=$isCodeSms recordId=${recordId ?: -1}",
        )
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val allSenders = db.senderDao().getAll().map { sender ->
                    SenderSettingSanitizer.sanitizeSenderLenient(sender)
                }
                val enabledSenders = allSenders.filter { it.status == 1 }
                val baseSenders = enabledSenders.filter { sender ->
                    if (msgInfo.type == "app_notify") {
                        sender.receiveAppNotify == 1
                    } else if (msgInfo.type == "call_notify") {
                        sender.receiveCallNotify == 1
                    } else {
                        if (isCodeSms) sender.receiveCode == 1 else sender.receiveNonCode == 1
                    }
                }
                val forwardFilterRules = if (
                    msgInfo.type == ForwardFilterConst.MSG_TYPE_SMS ||
                    msgInfo.type == ForwardFilterConst.MSG_TYPE_APP_NOTIFY
                ) {
                    db.forwardFilterRuleDao().getEnabledByMsgType(msgInfo.type)
                } else {
                    emptyList()
                }
                if (forwardFilterRules.isNotEmpty()) {
                    val preRouteDecision = ForwardFilterEngine.evaluatePreRoute(
                        rules = forwardFilterRules,
                        msgInfo = msgInfo,
                    )
                    if (preRouteDecision.blocked) {
                        val reason = "消息命中过滤规则，已拦截（${preRouteDecision.reason ?: "unknown"}）"
                        ForwardFlowLog.w(
                            traceId,
                            "Forward filter pre-route blocked type=${msgInfo.type} pkg=${msgInfo.packageName} reason=${preRouteDecision.reason}",
                        )
                        persistForwardResult(
                            db = db,
                            recordId = recordId,
                            results = emptyList(),
                            defaultMessage = reason,
                            forcedStatus = SmsMsg.FORWARD_STATUS_BLOCKED,
                        )
                        return@launch
                    }
                }
                var routingResult: NotifyRoutingResult? = null
                val routedSenders = if (msgInfo.type == "app_notify" && msgInfo.packageName.isNotBlank()) {
                    runCatching {
                        NotifyRoutingResolver.resolveAppNotifySenders(
                            candidates = baseSenders,
                            packageName = msgInfo.packageName,
                            dao = db.notifyRouteRuleDao(),
                            traceId = traceId,
                        )
                    }.onFailure { error ->
                        XLog.e("Notify routing resolve failed, fallback to legacy sender set", error)
                        ForwardFlowLog.e(traceId, "Notify routing resolve failed, fallback to legacy sender set", error)
                    }.getOrNull()?.also { result ->
                        routingResult = result
                        ForwardFlowLog.i(
                            traceId,
                            "Notify routing pkg=${msgInfo.packageName} base=${baseSenders.size} result=${result.senders.size} " +
                                "appBound=${result.appBoundSenderCount} senderAllowConfigured=${result.senderAllowConfiguredCount} " +
                                "senderAllowMatched=${result.senderAllowMatchedCount} senderDenyMatched=${result.senderDenyMatchedCount} " +
                                "conflict=${result.conflictSenderIds.size}",
                        )
                    }?.senders ?: baseSenders
                } else {
                    baseSenders
                }
                val senderFilteredReasonParts = mutableListOf<String>()
                val senders = if (forwardFilterRules.isEmpty()) {
                    routedSenders
                } else {
                    routedSenders.filter { sender ->
                        val decision = ForwardFilterEngine.evaluateSenderScope(
                            rules = forwardFilterRules,
                            msgInfo = msgInfo,
                            senderId = sender.id,
                        )
                        if (decision.blocked) {
                            senderFilteredReasonParts += "${sender.name.ifBlank { senderTypeName(sender.type) }}:${decision.reason ?: "blocked"}"
                            false
                        } else {
                            true
                        }
                    }
                }
                if (senders.isEmpty()) {
                    val filteredByRule = senderFilteredReasonParts.isNotEmpty()
                    val reason = if (filteredByRule) {
                        "可用通道命中过滤规则，已拦截（${senderFilteredReasonParts.joinToString(" | ")}）"
                    } else {
                        routingResult?.noEligibleReason
                            ?: buildNoEligibleReason(allSenders, enabledSenders, msgInfo.type, isCodeSms)
                    }
                    XLog.w(
                        "No eligible senders found (isCodeSms=%s, type=%s), skipping dispatch. reason=%s",
                        isCodeSms,
                        msgInfo.type,
                        reason,
                    )
                    persistForwardResult(
                        db = db,
                        recordId = recordId,
                        results = emptyList(),
                        defaultMessage = reason,
                        forcedStatus = if (filteredByRule) SmsMsg.FORWARD_STATUS_BLOCKED else null,
                    )
                    ForwardFlowLog.w(traceId, "No eligible senders: $reason")
                    return@launch
                }
                ForwardFlowLog.i(
                    traceId,
                    "Eligible senders=${senders.size}: ${
                        senders.joinToString(",") { it.name.ifBlank { senderTypeName(it.type) } }
                    }",
                )
                val commonConfig = ForwardCommonConfigStore.load(context)
                val effectiveConfig = when {
                    msgInfo.type == "app_notify" && msgInfo.packageName.isNotBlank() -> {
                        val appConfig = db.appInfoDao().getByPackageName(msgInfo.packageName)
                        if (appConfig?.notifyTemplate?.isNotBlank() == true) {
                            commonConfig.copy(messageTemplate = appConfig.notifyTemplate)
                        } else {
                            val appNotifyTemplate = ForwardCommonConfigStore.loadAppNotifyTemplate(context)
                            if (appNotifyTemplate.isNotBlank()) {
                                commonConfig.copy(messageTemplate = appNotifyTemplate)
                            } else {
                                commonConfig
                            }
                        }
                    }

                    msgInfo.type == "call_notify" -> {
                        val callNotifyTemplate = ForwardCommonConfigStore.loadCallNotifyTemplate(context)
                        if (callNotifyTemplate.isNotBlank()) {
                            commonConfig.copy(messageTemplate = callNotifyTemplate)
                        } else {
                            commonConfig
                        }
                    }

                    else -> commonConfig
                }
                val msgForSend = ForwardCommonConfigStore.applyToMessage(context, msgInfo, effectiveConfig)
                val results = mutableListOf<SenderDispatchResult>()
                for (sender in senders) {
                    results += dispatchToSender(context, sender, msgForSend, traceId)
                }
                persistForwardResult(
                    db = db,
                    recordId = recordId,
                    results = results,
                    defaultMessage = "未启用任何转发通道",
                )
                val success = results.count { it.success }
                val failed = results.size - success
                ForwardFlowLog.i(
                    traceId,
                    "Dispatch done success=$success failed=$failed detail=${
                        results.joinToString(" | ") { "${it.senderName}:${if (it.success) "OK" else it.message}" }
                    }",
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                XLog.e("Dispatch failed", e)
                ForwardFlowLog.e(traceId, "SendUtils dispatch failed", e)
                persistForwardResult(
                    db = AppDatabase.getInstance(context),
                    recordId = recordId,
                    results = emptyList(),
                    defaultMessage = "转发异常: ${e.message ?: e.javaClass.simpleName}",
                    forceFailed = true,
                )
            }
        }
    }

    private fun buildNoEligibleReason(
        allSenders: List<Sender>,
        enabledSenders: List<Sender>,
        msgType: String,
        isCodeSms: Boolean,
    ): String {
        if (allSenders.isEmpty()) {
            return "未配置任何转发通道"
        }
        if (enabledSenders.isEmpty()) {
            val allNames = allSenders.joinToString(",") { it.name.ifBlank { senderTypeName(it.type) } }
            return "所有转发通道均未启用（$allNames）"
        }
        return if (msgType == "app_notify") {
            val allowed = enabledSenders.filter { it.receiveAppNotify == 1 }
            val blockedNames = enabledSenders
                .filter { it.receiveAppNotify != 1 }
                .joinToString(",") { it.name.ifBlank { senderTypeName(it.type) } }
            "已启用通道均关闭了“转发应用通知”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
        } else if (msgType == "call_notify") {
            val allowed = enabledSenders.filter { it.receiveCallNotify == 1 }
            val blockedNames = enabledSenders
                .filter { it.receiveCallNotify != 1 }
                .joinToString(",") { it.name.ifBlank { senderTypeName(it.type) } }
            "已启用通道均关闭了“转发通话通知”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
        } else if (isCodeSms) {
            val allowed = enabledSenders.filter { it.receiveCode == 1 }
            val blockedNames = enabledSenders
                .filter { it.receiveCode != 1 }
                .joinToString(",") { it.name.ifBlank { senderTypeName(it.type) } }
            "已启用通道均关闭了“转发验证码短信”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
        } else {
            val allowed = enabledSenders.filter { it.receiveNonCode == 1 }
            val blockedNames = enabledSenders
                .filter { it.receiveNonCode != 1 }
                .joinToString(",") { it.name.ifBlank { senderTypeName(it.type) } }
            "已启用通道均关闭了“转发非验证码短信”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
        }
    }

    private suspend fun dispatchToSender(
        context: Context,
        sender: Sender,
        msgInfo: MsgInfo,
        traceId: String? = null,
    ): SenderDispatchResult {
        val senderName = sender.name.ifBlank { senderTypeName(sender.type) }
        XLog.d("Dispatching to sender: id=%d, type=%d, name=%s", sender.id, sender.type, sender.name)
        ForwardFlowLog.d(traceId, "Dispatch sender start name=$senderName type=${sender.type}")
        try {
            when (sender.type) {
                SenderType.DINGTALK_GROUP_ROBOT -> {
                    val setting = gson.fromJson(sender.jsonSetting, DingtalkGroupRobotSetting::class.java)
                    DingtalkGroupRobotUtils.sendMsg(setting, msgInfo)
                }
                SenderType.EMAIL -> {
                    val setting = gson.fromJson(sender.jsonSetting, EmailSetting::class.java)
                    EmailUtils.sendMsg(setting, msgInfo, traceId)
                }
                SenderType.BARK -> {
                    val setting = gson.fromJson(sender.jsonSetting, BarkSetting::class.java)
                    BarkUtils.sendMsg(setting, msgInfo)
                }
                SenderType.WEBHOOK -> {
                    val setting = gson.fromJson(sender.jsonSetting, WebhookSetting::class.java)
                    WebhookUtils.sendMsg(setting, msgInfo, traceId)
                }
                SenderType.WEWORK_ROBOT -> {
                    val setting = gson.fromJson(sender.jsonSetting, WeworkRobotSetting::class.java)
                    WeworkRobotUtils.sendMsg(setting, msgInfo)
                }
                SenderType.WEWORK_AGENT -> {
                    val setting = gson.fromJson(sender.jsonSetting, WeworkAgentSetting::class.java)
                    WeworkAgentUtils.sendMsg(setting, msgInfo)
                }
                SenderType.SERVERCHAN -> {
                    val setting = gson.fromJson(sender.jsonSetting, ServerchanSetting::class.java)
                    ServerchanUtils.sendMsg(setting, msgInfo)
                }
                SenderType.PUSHPLUS -> {
                    val setting = gson.fromJson(sender.jsonSetting, PushplusSetting::class.java)
                    PushplusUtils.sendMsg(setting, msgInfo)
                }
                SenderType.TELEGRAM -> {
                    val setting = gson.fromJson(sender.jsonSetting, TelegramSetting::class.java)
                    TelegramUtils.sendMsg(setting, msgInfo)
                }
                SenderType.SMS -> {
                    if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                        XLog.w("SMS sender disabled in current distribution, skipping sender [%s]", sender.name)
                    } else {
                        val setting = gson.fromJson(sender.jsonSetting, SmsSetting::class.java)
                        SmsUtils.sendMsg(context, setting, msgInfo)
                    }
                }
                SenderType.FEISHU -> {
                    val setting = gson.fromJson(sender.jsonSetting, FeishuSetting::class.java)
                    FeishuUtils.sendMsg(setting, msgInfo)
                }
                SenderType.GOTIFY -> {
                    val setting = gson.fromJson(sender.jsonSetting, GotifySetting::class.java)
                    GotifyUtils.sendMsg(setting, msgInfo)
                }
                SenderType.DINGTALK_INNER_ROBOT -> {
                    val setting = gson.fromJson(sender.jsonSetting, DingtalkInnerRobotSetting::class.java)
                    DingtalkInnerRobotUtils.sendMsg(setting, msgInfo)
                }
                SenderType.FEISHU_APP -> {
                    val setting = gson.fromJson(sender.jsonSetting, FeishuAppSetting::class.java)
                    FeishuAppUtils.sendMsg(setting, msgInfo)
                }
                SenderType.URL_SCHEME -> {
                    val setting = gson.fromJson(sender.jsonSetting, UrlSchemeSetting::class.java)
                    UrlSchemeUtils.sendMsg(context, setting, msgInfo)
                }
                SenderType.SOCKET -> {
                    val setting = gson.fromJson(sender.jsonSetting, SocketSetting::class.java)
                    SocketUtils.sendMsg(setting, msgInfo)
                }
                else -> {
                    val message = "Unsupported sender type: ${sender.type}"
                    XLog.w(message)
                    return SenderDispatchResult(senderName = senderName, success = false, message = message)
                }
            }
            XLog.i("Dispatched to sender [%s] type=%d", sender.name, sender.type)
            ForwardFlowLog.i(traceId, "Dispatch sender success name=$senderName")
            return SenderDispatchResult(senderName = senderName, success = true, message = "OK")
        } catch (e: com.google.gson.JsonSyntaxException) {
            XLog.e("Failed to parse sender setting for [%s]", sender.name, e)
            ForwardFlowLog.e(traceId, "Dispatch sender json parse failed name=$senderName", e)
            return SenderDispatchResult(
                senderName = senderName,
                success = false,
                message = "配置解析失败: ${e.message ?: "JsonSyntaxException"}",
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            XLog.e("Failed to dispatch to sender [%s] type=%d", sender.name, sender.type, e)
            ForwardFlowLog.e(traceId, "Dispatch sender failed name=$senderName", e)
            return SenderDispatchResult(
                senderName = senderName,
                success = false,
                message = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    private fun persistForwardResult(
        db: AppDatabase,
        recordId: Long?,
        results: List<SenderDispatchResult>,
        defaultMessage: String,
        forceFailed: Boolean = false,
        forcedStatus: Int? = null,
    ) {
        if (recordId == null) return
        runCatching {
            val msgDao = db.smsMsgDao()
            val existing = msgDao.getById(recordId) ?: return
            val successResults = results.filter { it.success }
            val failedResults = results.filterNot { it.success }
            val computedStatus = when {
                forcedStatus != null -> forcedStatus
                forceFailed -> SmsMsg.FORWARD_STATUS_FAILED
                successResults.isNotEmpty() && failedResults.isNotEmpty() -> SmsMsg.FORWARD_STATUS_PARTIAL
                successResults.isNotEmpty() -> SmsMsg.FORWARD_STATUS_SUCCESS
                else -> SmsMsg.FORWARD_STATUS_FAILED
            }
            val target = results.joinToString(", ") { it.senderName }.ifBlank { null }
            val message = when {
                forceFailed -> defaultMessage
                results.isNotEmpty() -> results.joinToString("\n") { result ->
                    if (result.success) {
                        "${result.senderName}通道转发成功"
                    } else {
                        "${result.senderName}通道转发失败，原因：${result.message}"
                    }
                }
                else -> defaultMessage
            }.take(MAX_FORWARD_MESSAGE_LEN)

            msgDao.update(
                existing.copy(
                    forwardStatus = computedStatus,
                    forwardTarget = target,
                    forwardMessage = message,
                    forwardTime = Date().time,
                ),
            )
        }.onFailure { t ->
            XLog.w("Persist forward result failed: %s", t.message ?: t.javaClass.simpleName)
        }
    }

    private fun senderTypeName(type: Int): String {
        return when (type) {
            SenderType.DINGTALK_GROUP_ROBOT -> "钉钉群机器人"
            SenderType.EMAIL -> "邮件"
            SenderType.BARK -> "Bark"
            SenderType.WEBHOOK -> "Webhook"
            SenderType.WEWORK_ROBOT -> "企微群机器人"
            SenderType.WEWORK_AGENT -> "企微应用"
            SenderType.SERVERCHAN -> "Server酱"
            SenderType.TELEGRAM -> "Telegram"
            SenderType.SMS -> "短信"
            SenderType.FEISHU -> "飞书机器人"
            SenderType.PUSHPLUS -> "PushPlus"
            SenderType.GOTIFY -> "Gotify"
            SenderType.DINGTALK_INNER_ROBOT -> "钉钉内部机器人"
            SenderType.FEISHU_APP -> "飞书应用"
            SenderType.URL_SCHEME -> "Url Scheme"
            SenderType.SOCKET -> "Socket"
            else -> "通道$type"
        }
    }
}
