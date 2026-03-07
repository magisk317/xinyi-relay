package com.github.magisk317.smscode.forwarder.filter

import com.github.magisk317.smscode.forwarder.entity.ForwardFilterRule
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class ForwardFilterEngineTest {

    @Test
    fun preRoute_globalAllowMatch_passes() {
        val msgInfo = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "code",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, msgInfo)
        assertFalse(decision.blocked)
    }

    @Test
    fun preRoute_globalAllowMiss_blocks() {
        val msgInfo = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_CONTAINS,
                pattern = "otp-only",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, msgInfo)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_allowAndDenyConflict_blocks() {
        val msgInfo = sms("Bank", "Code is 1234")
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

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, msgInfo)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_appScopeAndChannelScope_appliedInOrder() {
        val msgInfo = appNotify(
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

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, msgInfo)
        assertTrue(decision.blocked)
    }

    @Test
    fun preRoute_androidChannel_legacyScopeKey_stillWorks() {
        val msgInfo = appNotify(
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

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, msgInfo)
        assertTrue(decision.blocked)
    }

    @Test
    fun senderScope_whitelistMiss_blocksSingleSender() {
        val msgInfo = sms("Bank", "Code is 1234")
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

        val decision = ForwardFilterEngine.evaluateSenderScope(rules, msgInfo, senderId = 2L)
        assertTrue(decision.blocked)
    }

    @Test
    fun invalidRegex_doesNotCrashAndFallsBackToNoMatch() {
        val msgInfo = sms("Bank", "Code is 1234")
        val rules = listOf(
            rule(
                msgType = ForwardFilterConst.MSG_TYPE_SMS,
                scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                policy = ForwardFilterConst.POLICY_ALLOW,
                matchMode = ForwardFilterConst.MATCH_REGEX,
                pattern = "[unclosed",
            ),
        )

        val decision = ForwardFilterEngine.evaluatePreRoute(rules, msgInfo)
        assertTrue(decision.blocked)
    }

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

    private fun sms(sender: String, body: String): MsgInfo = MsgInfo(
        type = ForwardFilterConst.MSG_TYPE_SMS,
        from = sender,
        content = body,
        date = Date(),
        simInfo = "SIM1",
    )

    private fun appNotify(
        title: String,
        body: String,
        appName: String,
        packageName: String,
        notifyChannelId: String,
    ): MsgInfo = MsgInfo(
        type = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
        from = title,
        content = body,
        date = Date(),
        simInfo = appName,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        appName = appName,
        title = title,
        message = body,
    )
}
