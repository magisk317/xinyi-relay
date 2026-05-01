package io.github.magisk317.relay.sms

import android.content.Context
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.smscode.domain.model.SmsBlacklistConfig
import io.github.magisk317.smscode.runtime.common.sms.RuntimeSmsBlacklistAdapter
import io.github.magisk317.smscode.runtime.common.sms.SmsBlacklistConfigProvider

// Runtime/Xposed only. Do not use from UI/app-side business logic.
object SmsBlacklistUtils {

    data class MatchResult(
        val matched: Boolean,
        val matchType: String? = null,
        val pattern: String? = null,
        val actionDelete: Boolean = false,
        val actionBlock: Boolean = false,
    )

    private val adapter = RuntimeSmsBlacklistAdapter(
        configProvider = SmsBlacklistConfigProvider { context ->
            SmsBlacklistConfig(
                enabled = PrefsReader.smsBlacklistEnabled(context),
                actionDelete = PrefsReader.smsBlacklistActionDelete(context),
                actionBlock = PrefsReader.smsBlacklistActionBlock(context),
                numbers = PrefsReader.smsBlacklistNumbers(context),
                prefixes = PrefsReader.smsBlacklistPrefixes(context),
                content = PrefsReader.smsBlacklistContent(context),
                regex = PrefsReader.smsBlacklistRegex(context),
            )
        },
    )

    @JvmStatic
    fun match(context: Context, sender: String?, body: String?): MatchResult {
        val sharedResult = adapter.match(context, sender, body)
        return MatchResult(
            matched = sharedResult.matched,
            matchType = sharedResult.matchType,
            pattern = sharedResult.pattern,
            actionDelete = sharedResult.actionDelete,
            actionBlock = sharedResult.actionBlock,
        )
    }
}
