package io.github.magisk317.relay.xpbridge

import android.content.Context

object XpSmsBlacklist {
    data class MatchResult(
        val matched: Boolean,
        val matchType: String? = null,
        val pattern: String? = null,
        val actionDelete: Boolean = false,
        val actionBlock: Boolean = false,
    )

    fun match(context: Context, sender: String?, body: String?): MatchResult {
        val result = XpSmsRuntimeBridge.matchSmsBlacklist(context, sender, body)
        return MatchResult(
            matched = result.matched,
            matchType = result.matchType,
            pattern = result.pattern,
            actionDelete = result.actionDelete,
            actionBlock = result.actionBlock,
        )
    }
}
