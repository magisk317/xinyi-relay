package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CustomMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ordered = isOrderedBroadcast
        val pendingResult = goAsync()
        val rawEventId = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_EVENT_ID).orEmpty()
        val payload = CustomMessageBroadcastPayload.fromIntent(intent)
        val traceId = CustomMessageReceiverPolicy.resolveEventId(
            eventId = rawEventId,
            message = payload.message,
            title = payload.title,
            packageName = payload.packageName,
        )

        RECEIVER_SCOPE.launch {
            fun finish(code: Int, reason: String) {
                if (ordered) {
                    runCatching {
                        pendingResult.setResultCode(code)
                        pendingResult.setResultData(
                            "reason=$reason;${CustomMessageBroadcastContract.EXTRA_EVENT_ID}=${traceId.ifBlank { "<none>" }}",
                        )
                    }
                }
                pendingResult.finish()
            }

            runCatching {
                if (intent.action != PrefConst.ACTION_INGEST_CUSTOM_MESSAGE) {
                    finish(RESULT_REJECT_ACTION, "invalid_action")
                    return@runCatching
                }

                if (payload.message.isBlank()) {
                    finish(RESULT_REJECT_PAYLOAD, "message_missing")
                    return@runCatching
                }

                val expectedToken = RuntimeSettingsCache.getString(
                    key = PrefConst.KEY_IPC_TOKEN,
                    defaultValue = "",
                ) { key, defaultValue ->
                    RuntimeDependencies.get().preferenceDataSource.getString(key, defaultValue)
                }
                val receivedToken = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_IPC_TOKEN)
                if (!CustomMessageReceiverPolicy.isTokenAccepted(receivedToken, expectedToken)) {
                    finish(RESULT_REJECT_TOKEN, "token_mismatch")
                    return@runCatching
                }

                val deps = RuntimeDependencies.get()
                val event = payload.toRelayEvent(sentFromPackage = resolveSentFromPackageCompat())
                val result = deps.eventPipeline.process(event = event, traceId = traceId)
                if (result.dispatchError != null) {
                    finish(RESULT_DISPATCH_FAILED, "dispatch_error")
                    return@runCatching
                }
                finish(RESULT_OK, "accepted")
            }.onFailure { error ->
                XLog.e("CustomMessageReceiver failed", error)
                finish(RESULT_DISPATCH_FAILED, "receiver_error")
            }
        }
    }

    private fun resolveSentFromPackageCompat(): String? {
        if (Build.VERSION.SDK_INT < ForwardReceiverPolicy.API_LEVEL_34) return null
        return runCatching { getSentFromPackage() }.getOrNull()
    }

    companion object {
        private const val RESULT_OK = 0
        private const val RESULT_REJECT_ACTION = -201
        private const val RESULT_REJECT_TOKEN = -202
        private const val RESULT_REJECT_PAYLOAD = -203
        private const val RESULT_DISPATCH_FAILED = -204
        private val RECEIVER_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
