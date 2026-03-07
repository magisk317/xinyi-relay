package com.github.magisk317.smscode.forwarder.filter

import com.github.magisk317.smscode.forwarder.entity.ForwardFilterRule
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
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

    fun evaluatePreRoute(
        rules: List<ForwardFilterRule>,
        msgInfo: MsgInfo,
    ): ForwardFilterDecision {
        val matchText = buildMatchText(msgInfo)
        evaluateScope(
            rules = rules.filter { it.scopeType == ForwardFilterConst.SCOPE_GLOBAL },
            matchText = matchText,
            reasonPrefix = "global",
        ).takeIf { it.blocked }?.let { return it }

        if (msgInfo.type == ForwardFilterConst.MSG_TYPE_APP_NOTIFY) {
            val pkg = msgInfo.packageName.trim()
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

            val channelId = msgInfo.notifyChannelId.trim()
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

    fun evaluateSenderScope(
        rules: List<ForwardFilterRule>,
        msgInfo: MsgInfo,
        senderId: Long,
    ): ForwardFilterDecision {
        val matchText = buildMatchText(msgInfo)
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

    private fun buildMatchText(msgInfo: MsgInfo): String {
        return when (msgInfo.type) {
            ForwardFilterConst.MSG_TYPE_APP_NOTIFY -> {
                val title = msgInfo.title.ifBlank { msgInfo.from }
                val body = msgInfo.message.ifBlank { msgInfo.content }
                val appName = msgInfo.appName.ifBlank { msgInfo.simInfo }
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
                    append(msgInfo.from)
                    append('\n')
                    append(msgInfo.content)
                }
            }
        }
    }
}
