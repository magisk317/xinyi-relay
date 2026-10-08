package io.github.magisk317.relay.android.sms

import android.content.Context
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.smscode.rule.model.SmsBlacklistConfig
import io.github.magisk317.smscode.runtime.common.sms.RuntimeSmsBlacklistAdapter
import io.github.magisk317.smscode.runtime.common.sms.SmsBlacklistConfigProvider
import io.github.magisk317.smscode.verification.BlacklistMatchResult

/**
 * Blacklist lookup for relay.
 *
 * Matching lives in core; this only supplies the pref-backed config and returns the
 * shared result type, so there is one MatchResult definition instead of two.
 */
// Runtime/Xposed only. Do not use from UI/app-side business logic.
object SmsBlacklistUtils {

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
    fun match(context: Context, sender: String?, body: String?): BlacklistMatchResult =
        adapter.match(context, sender, body)
}
