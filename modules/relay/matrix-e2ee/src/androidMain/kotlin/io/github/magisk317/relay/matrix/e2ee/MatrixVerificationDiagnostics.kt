package io.github.magisk317.relay.matrix.e2ee
import io.github.magisk317.forward.sender.MatrixE2eeVerificationEmoji
import io.github.magisk317.forward.sender.MatrixE2eeVerificationState
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails

/**
 * Pure summarizers for verification state, SDK sessions and SAS payloads.
 * No machine state; snapshot loggers stay on the runtime.
 */
internal object MatrixVerificationDiagnostics {

    private const val SYNC_BATCH_SUMMARY_LENGTH = 12
    private const val VALUE_SUMMARY_LENGTH = 16
    internal fun summarizeSas(data: SessionVerificationData): SasSummary {
        return when (data) {
            is SessionVerificationData.Emojis -> SasSummary(
                emojis = data.emojis.map { emoji ->
                    MatrixE2eeVerificationEmoji(
                        symbol = emoji.symbol(),
                        description = emoji.description(),
                    )
                },
                decimals = emptyList(),
            )
            is SessionVerificationData.Decimals -> SasSummary(
                emojis = emptyList(),
                decimals = data.values.map { it.toString() },
            )
        }
    }

    internal fun summarizeBatch(nextBatch: String): String {
        if (nextBatch.isBlank()) return "<blank>"
        return if (nextBatch.length <= SYNC_BATCH_SUMMARY_LENGTH) {
            nextBatch
        } else {
            "${nextBatch.take(SYNC_BATCH_SUMMARY_LENGTH)}..."
        }
    }

    internal fun summarizeClientSession(client: Client?): String {
        if (client == null) return "session=<none>"
        val session = runCatching { client.session() }.getOrNull() ?: return "session=<unavailable>"
        return "user=${summarizeValue(session.userId)} device=${summarizeValue(session.deviceId)}"
    }

    internal fun summarizeStateSession(state: MatrixE2eeVerificationState): String {
        return "user=${summarizeValue(state.userId)} device=${summarizeValue(state.deviceId)}"
    }

    internal fun summarizeVerificationState(state: MatrixE2eeVerificationState): String {
        return "status=${state.status} " +
            "trust=${state.verificationState.ifBlank { "<blank>" }} " +
            "hasDevices=${state.hasDevicesToVerifyAgainst} " +
            "requestUser=${summarizeValue(state.requestUserId)} " +
            "requestDevice=${summarizeValue(state.requestDeviceId)} " +
            "sasEmojis=${state.sasEmojis.size} " +
            "sasDecimals=${state.sasDecimals.size} " +
            "message=${summarizeValue(state.message)}"
    }

    internal fun summarizePendingRequest(pendingRequest: SessionVerificationRequestDetails?): String {
        return pendingRequest?.let(::summarizeRequestDetails) ?: "<none>"
    }

    internal fun summarizeRequestDetails(details: SessionVerificationRequestDetails): String {
        return "flow=${summarizeValue(details.flowId)} " +
            "sender=${summarizeValue(details.senderProfile.userId)} " +
            "device=${summarizeValue(details.deviceId)} " +
            "display=${summarizeValue(details.deviceDisplayName)}"
    }

    internal fun summarizeValue(value: String?): String {
        val normalized = value.orEmpty()
        if (normalized.isBlank()) return "<blank>"
        return if (normalized.length <= VALUE_SUMMARY_LENGTH) {
            normalized
        } else {
            "${normalized.take(VALUE_SUMMARY_LENGTH)}...(${normalized.length})"
        }
    }

    internal data class SasSummary(
        val emojis: List<MatrixE2eeVerificationEmoji>,
        val decimals: List<String>,
    )

}
