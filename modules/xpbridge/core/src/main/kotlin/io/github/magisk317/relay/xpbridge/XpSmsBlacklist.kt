package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.smscode.verification.BlacklistMatchResult

/**
 * Blacklist lookup across the runtime bridge.
 *
 * The bridge already returns the shared match type, so this used to re-wrap it in a
 * local copy of the same five fields. Pass it through instead.
 */
object XpSmsBlacklist {

    fun match(context: Context, sender: String?, body: String?): BlacklistMatchResult =
        XpSmsRuntimeBridge.matchSmsBlacklist(context, sender, body)
}
