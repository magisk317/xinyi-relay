package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.android.data.store.EntityStoreManager
import io.github.magisk317.relay.android.data.store.EntityType
import io.github.magisk317.relay.xpbridge.XpAppConfigFacade
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpSharedRuntimeGate
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.relay.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.verification.AutoInputActionHelper
import io.github.magisk317.smscode.verification.AutoInputBlockedPackageHelper
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.nio.charset.StandardCharsets

/**
 * 自动输入验证码
 */
class AutoInputAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val deduplicateEnabled: Boolean? = null,
    private val dispatchDelayMs: Long = 0L,
    private val attemptId: Long? = null,
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {
    private val runtimeAppConfigFacade = XpAppConfigFacade(pluginContext)
    private val runtimeRecordFacade = XpRecordFacade(pluginContext)

    override fun action(): Bundle? {
        AutoInputActionHelper(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsMsg = mSmsMsg,
            deduplicateEnabled = deduplicateEnabled,
            dispatchDelayMs = dispatchDelayMs,
            deduplicateReader = XpPrefs::deduplicateSms,
            sharedGateClaimer = { context, fileName, keys, windowMs, maxEntries ->
                XpSharedRuntimeGate.claimAllWithinWindow(
                    context = context,
                    fileName = fileName,
                    keys = keys,
                    windowMs = windowMs,
                    maxEntries = maxEntries,
                ).toShared()
            },
            packageBlockedChecker = ::isPackageBlocked,
            autoEnterReader = XpPrefs::autoEnterCodeEnabled,
            inputIntervalReader = XpPrefs::getAutoInputCodeIntervalMs,
            attemptRecorder = { smsMsg, foregroundPackage ->
                runCatching {
                    runBlocking {
                        val recordId = runtimeRecordFacade.findSmsRecordIdByFingerprint(
                            sender = smsMsg.sender,
                            body = smsMsg.body,
                            date = smsMsg.date,
                        )
                        runtimeRecordFacade.insertAutoInputAttempt(
                            recordId = recordId,
                            packageName = foregroundPackage,
                            codeLength = smsMsg.smsCode?.length ?: 0,
                        )
                    }
                }.onFailure { error ->
                    XLog.w(
                        "Insert auto input attempt failed: %s",
                        error.message ?: error.javaClass.simpleName,
                    )
                }.getOrNull()
            },
            inputSender = InputHelper::sendText,
        ).run()
        return null
    }

    private fun isPackageBlocked(packageName: String): Boolean {
        return AutoInputBlockedPackageHelper.resolveBlockedState(
            packageName = packageName,
            primaryChecker = ::queryBlockedStateByFacade,
            fallbackChecker = ::isPackageBlockedInConfigFile,
            fallbackLogger = { pkg, blocked ->
                XLog.d("AutoInput fallback file check: pkg=%s blocked=%s", pkg, blocked)
            },
        )
    }

    private fun isPackageBlockedInConfigFile(packageName: String): Boolean {
        return runCatching {
            val storeFile = EntityStoreManager.getStoreFile(mPluginContext, EntityType.APP_CONFIG)
            if (!storeFile.exists() || storeFile.length() == 0L) {
                return@runCatching false
            }

            val entries = JSONArray(storeFile.readText(StandardCharsets.UTF_8))
            var blocked = false
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                if (entry.optString("packageName") == packageName) {
                    blocked = entry.optBoolean("blocked", false)
                    break
                }
            }
            blocked
        }.onFailure { error ->
            XLog.w(
                "AutoInput fallback file check failed: pkg=%s err=%s",
                packageName,
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrDefault(false)
    }

    private fun queryBlockedStateByFacade(packageName: String): Boolean? {
        return runCatching {
            runBlocking { runtimeAppConfigFacade.isPackageBlocked(packageName) }
        }.onSuccess {
            XLog.d("AutoInput facade check: pkg=%s blocked=%s", packageName, it)
        }.onFailure { error ->
            XLog.w(
                "AutoInput facade check failed: pkg=%s err=%s",
                packageName,
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrNull()
    }

    private fun XpSharedRuntimeGate.ClaimResult.toShared(): AutoInputActionHelper.ClaimResult {
        return AutoInputActionHelper.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
            key = key,
        )
    }
}
