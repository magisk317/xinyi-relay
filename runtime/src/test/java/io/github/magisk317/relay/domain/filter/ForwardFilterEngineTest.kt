package io.github.magisk317.relay.domain.filter

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.model.ForwardFilterRule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class ForwardFilterEngineTest {

    @Test
    fun preRoute_globalAllowMatch_passes() {
        val event = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "code",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertFalse(decision.blocked)
    }

    @Test
    fun preRoute_globalAllowMiss_blocks() {
        val event = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "otp-only",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_allowAndDenyConflict_blocks() {
        val event = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "code",
            ),
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_DENY,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "1234",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_appScopeAndChannelScope_appliedInOrder() {
        val event = appNotify(
            title = "WeChat Pay",
            body = "Transfer from Alice 99.00",
            appName = "WeChat",
            packageName = "com.tencent.mm",
            notifyChannelId = "pay",
        )
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
                scopeType = ForwardFilterConst.SCOPE_PACKAGE,
                scopeKey = "com.tencent.mm",
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "transfer",
            ),
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
                scopeType = ForwardFilterConst.SCOPE_ANDROID_CHANNEL,
                scopeKey = ForwardFilterConst.buildAndroidChannelScopeKey("com.tencent.mm", "pay"),
                policy = ForwardFilterConst.POLICY_DENY,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "alice",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_androidChannel_previousScopeKey_stillWorks() {
        val event = appNotify(
            title = "WeChat Pay",
            body = "Transfer from Alice 99.00",
            appName = "WeChat",
            packageName = "com.tencent.mm",
            notifyChannelId = "pay",
        )
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
                scopeType = ForwardFilterConst.SCOPE_ANDROID_CHANNEL,
                scopeKey = "pay",
                policy = ForwardFilterConst.POLICY_DENY,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "alice",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertTrue(decision.blocked)
    }

    @Test
    fun senderScope_whitelistMiss_blocksSingleSender() {
        val event = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_SENDER,
                senderId = 2L,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "otp-only",
            ),
        )

        val decision = ForwardFilterEngine.evaluateSenderScope(rules, event, senderId = 2L)
        assertTrue(decision.blocked)
    }

    @Test
    fun invalidRegex_doesNotCrashAndFallsBackToNoMatch() {
        val event = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_REGEX,
                pattern = "[unclosed",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_smsCode_matchesSenderAndBody() {
        val event = smsCode("Bank", "Your code is 9988")
        val rules = listOf(
            rule(
                msgType = MessageType.SMS_CODE.runtimeType,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_DENY,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "9988",
            ),
        )
        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_smsPlain_matchesSenderAndBody() {
        val event = smsPlain("Carrier", "Your bill is ready")
        val rules = listOf(
            rule(
                msgType = MessageType.SMS_PLAIN.runtimeType,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "bill",
            ),
        )
        val decision = ForwardFilterEngine.evaluatePreRoute(rules, event)
        assertFalse(decision.blocked)
    }

    @Test
    fun preRoute_callNotify_alwaysPasses_filterNotEvaluated() {
        // CALL_NOTIFY 不应走过滤引擎（调用方 RoutingResolver 不会传入规则）
        val event = callNotify("13800138000")
        val decision = ForwardFilterEngine.evaluatePreRoute(emptyList(), event)
        assertFalse(decision.blocked)
    }

    // --- helpers ---

    private fun rule(
        msgType: String,
        scopeType: String,
        scopeKey: String = "",
        senderId: Long = 0L,
        policy: String,
        matchMode: String,
        pattern: String,
    ): ForwardFilterRule = ForwardFilterRule(
        msgType = msgType,
        scopeType = scopeType,
        scopeKey = scopeKey,
        senderId = senderId,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = 1,
        updateTime = 0L,
    )

    private fun baseEvent(
        messageType: MessageType,
        sender: String,
        body: String,
        packageName: String = "",
        notifyChannelId: String = "",
        companyOrAppName: String = "SIM1",
    ): RelayEvent = RelayEvent(
        messageType = messageType,
        sourceType = "test",
        sender = sender,
        body = body,
        timestamp = Date().time,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        companyOrAppName = companyOrAppName,
        smsCode = null,
        callType = 0,
        callStage = "",
        simSlot = 0,
        subId = 0,
    )

    private fun sms(sender: String, body: String): RelayEvent =
        baseEvent(MessageType.SMS_CODE, sender, body)

    private fun smsCode(sender: String, body: String): RelayEvent =
        baseEvent(MessageType.SMS_CODE, sender, body)

    private fun smsPlain(sender: String, body: String): RelayEvent =
        baseEvent(MessageType.SMS_PLAIN, sender, body)

    private fun callNotify(sender: String): RelayEvent =
        baseEvent(MessageType.CALL_NOTIFY, sender, "").copy(callType = 1, callStage = "ringing")

    private fun appNotify(
        title: String,
        body: String,
        appName: String,
        packageName: String,
        notifyChannelId: String,
    ): RelayEvent = RelayEvent(
        messageType = MessageType.APP_NOTIFY,
        sourceType = "test",
        sender = title,
        body = body,
        timestamp = Date().time,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        companyOrAppName = appName,
        smsCode = null,
        callType = 0,
        callStage = "",
        simSlot = -1,
        subId = 0,
    )
}
