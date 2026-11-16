package io.github.magisk317.relay.xp.hook.helper

import android.content.Context
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.smscode.xposed.hook.vendor.MiuiBugleReadStateHost

/**
 * xinyi wiring for the shared MIUI bugle read-state hook (smscode-core vendor package).
 */
internal object XinyiBugleReadStateHost : MiuiBugleReadStateHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override fun isModuleEnabled(context: Context): Boolean = XpPrefs.isEnabled(context)

    override fun isMobileAutomationAllowed(context: Context): Boolean =
        XpPrefs.mobileAutomationAllowed(context)

    override fun isMarkAsReadEnabled(context: Context): Boolean = XpPrefs.markAsReadEnabled(context)

    override fun shouldSuppressByRelay(context: Context, tag: String): Boolean =
        ModuleConflictArbiter.shouldSuppressByRelay(context, tag)

    override suspend fun parseVerificationCode(context: Context, body: String): String? =
        XpSmsCodeParser.parseSmsCodeIfExists(context, body).takeIf { it.isNotBlank() }
}
