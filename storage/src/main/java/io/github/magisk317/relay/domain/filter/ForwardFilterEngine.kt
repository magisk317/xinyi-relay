package io.github.magisk317.relay.domain.filter

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.model.ForwardFilterRule
import java.util.concurrent.ConcurrentHashMap

data class ForwardFilterDecision(
    val blocked: Boolean,
    val reason: String? = null,
    val allowConfiguredCount: Int = 0,
    val allowMatchedCount: Int = 0,
    val denyMatchedCount: Int = 0,
)

object ForwardFilterEngine {
    private val regexCache = ConcurrentHashMap<String, Regex?>()

    /**
     * 路由前置过滤：基于 [RelayEvent] 做全局/包名/渠道范围的规则评估。
     * 仅对短信和应用通知有效；电话提醒直接放行。
     */
    fun evaluatePreRoute(
        rules: List<ForwardFilterRule>,
        event: RelayEvent,
    ): ForwardFilterDecision {
        val matchText = buildMatchText(event)
        evaluateScope(
            rules = rules.filter { it.scopeType == ForwardFilterConst.SCOPE_GLOBAL },
            matchText = matchText,
            reasonPrefix = "global",
        ).takeIf { it.blocked }?.let { return it }

        if (event.messageType == MessageType.APP_NOTIFY) {
            val pkg = event.packageName.trim()
            if (pkg.isNotEmpty()) {
                evaluateScope(
                    rules = rules.filter {
                        it.scopeType == ForwardFilterConst.SCOPE_PACKAGE &&
                            it.scopeKey.trim() == pkg
                    },
                    matchText = matchText,
                    reasonPrefix = "package:$pkg",
                ).takeIf { it.blocked }?.let { return it }
            }

            val channelId = event.notifyChannelId.trim()
            if (channelId.isNotEmpty()) {
                val compositeKey = ForwardFilterConst.buildAndroidChannelScopeKey(pkg, channelId)
                evaluateScope(
                    rules = rules.filter {
                        it.scopeType == ForwardFilterConst.SCOPE_ANDROID_CHANNEL &&
                            (it.scopeKey.trim() == compositeKey || it.scopeKey.trim() == channelId)
                    },
                    matchText = matchText,
                    reasonPrefix = "android_channel:${if (compositeKey.isNotBlank()) compositeKey else channelId}",
                ).takeIf { it.blocked }?.let { return it }
            }
        }

        return ForwardFilterDecision(blocked = false)
    }

    /**
     * 发件人范围过滤：评估特定 senderId 是否被规则拦截。
     */
    fun evaluateSenderScope(
        rules: List<ForwardFilterRule>,
        event: RelayEvent,
        senderId: Long,
    ): ForwardFilterDecision {
        val matchText = buildMatchText(event)
        return evaluateScope(
            rules = rules.filter {
                it.scopeType == ForwardFilterConst.SCOPE_SENDER &&
                    it.senderId == senderId
            },
            matchText = matchText,
            reasonPrefix = "sender:$senderId",
        )
    }

    private fun evaluateScope(
        rules: List<ForwardFilterRule>,
        matchText: String,
        reasonPrefix: String,
    ): ForwardFilterDecision {
        if (rules.isEmpty()) return ForwardFilterDecision(blocked = false)
        val allowRules = rules.filter { it.policy == ForwardFilterConst.POLICY_ALLOW }
        val denyRules = rules.filter { it.policy == ForwardFilterConst.POLICY_DENY }

        val matchedAllow = allowRules.filter { matches(it, matchText) }
        val matchedDeny = denyRules.filter { matches(it, matchText) }

        if (allowRules.isNotEmpty() && matchedAllow.isEmpty()) {
            return ForwardFilterDecision(
                blocked = true,
                reason = "$reasonPrefix:allow_miss",
                allowConfiguredCount = allowRules.size,
                allowMatchedCount = 0,
                denyMatchedCount = matchedDeny.size,
            )
        }
        if (matchedDeny.isNotEmpty()) {
            val reason = if (matchedAllow.isNotEmpty()) {
                "$reasonPrefix:allow_deny_conflict"
            } else {
                "$reasonPrefix:deny_match"
            }
            return ForwardFilterDecision(
                blocked = true,
                reason = reason,
                allowConfiguredCount = allowRules.size,
                allowMatchedCount = matchedAllow.size,
                denyMatchedCount = matchedDeny.size,
            )
        }
        return ForwardFilterDecision(
            blocked = false,
            allowConfiguredCount = allowRules.size,
            allowMatchedCount = matchedAllow.size,
            denyMatchedCount = 0,
        )
    }

    private fun matches(rule: ForwardFilterRule, text: String): Boolean {
        if (text.isBlank()) return false
        val pattern = rule.pattern.trim()
        if (pattern.isEmpty()) return false
        return when (rule.matchMode) {
            ForwardFilterConst.MATCH_REGEX -> {
                val regex = regexCache.computeIfAbsent(pattern) {
                    runCatching { Regex(pattern, setOf(RegexOption.IGNORE_CASE)) }.getOrNull()
                } ?: return false
                regex.containsMatchIn(text)
            }

            else -> text.contains(pattern, ignoreCase = true)
        }
    }

    private fun buildMatchText(event: RelayEvent): String {
        return when (event.messageType) {
            MessageType.APP_NOTIFY -> {
                val title = event.sender
                val body = event.body
                val appName = event.companyOrAppName
                buildString {
                    append(title)
                    append('\n')
                    append(body)
                    append('\n')
                    append(appName)
                }
            }

            else -> {
                buildString {
                    append(event.sender)
                    append('\n')
                    append(event.body)
                }
            }
        }
    }
}
