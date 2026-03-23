package io.github.magisk317.relay.xp.hook.code

import io.github.magisk317.relay.xpbridge.XpSmsBlacklist

internal object SmsHandlerDispatchDecision {
    enum class BlockReason(val wireValue: String) {
        BLACKLIST("blacklist_block"),
        PREF_BLOCK("pref_block_sms"),
    }

    data class Decision(
        val shouldDeleteByBlacklist: Boolean,
        val blockReason: BlockReason? = null,
        val shouldAllowSystemPersist: Boolean = false,
    )

    fun evaluate(
        blacklistResult: XpSmsBlacklist.MatchResult,
        smsMsgAvailable: Boolean,
        parseResult: ParseResult?,
    ): Decision {
        if (blacklistResult.matched) {
            if (blacklistResult.actionBlock) {
                return Decision(
                    shouldDeleteByBlacklist = false,
                    blockReason = BlockReason.BLACKLIST,
                )
            }
            if (blacklistResult.actionDelete && smsMsgAvailable) {
                return Decision(
                    shouldDeleteByBlacklist = true,
                    blockReason = null,
                )
            }
        }

        if (parseResult?.isBlockSms == true) {
            return Decision(
                shouldDeleteByBlacklist = false,
                blockReason = BlockReason.PREF_BLOCK,
            )
        }

        return Decision(
            shouldDeleteByBlacklist = false,
            blockReason = null,
            shouldAllowSystemPersist = parseResult != null,
        )
    }
}
