package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.sender.config.MatrixSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exposes the Matrix session-verification flow to UI modules without requiring
 * them to depend on the E2EE implementation classes.
 */
interface MatrixE2eeVerification {
    val state: StateFlow<MatrixE2eeVerificationState>
    suspend fun prepare(context: Context, setting: MatrixSetting)
    suspend fun requestVerification()
    suspend fun acceptRequest()
    suspend fun startSas()
    suspend fun approve()
    suspend fun decline()
    suspend fun cancel()
    suspend fun revokeDevice(context: android.content.Context, setting: io.github.magisk317.relay.sender.config.MatrixSetting)
    fun reset()
    fun stop() {}
}

data class MatrixE2eeVerificationState(
    val status: MatrixE2eeVerificationStatus = MatrixE2eeVerificationStatus.NOT_PREPARED,
    val userId: String = "",
    val deviceId: String = "",
    val verificationState: String = "",
    val hasDevicesToVerifyAgainst: Boolean = false,
    val requestUserId: String = "",
    val requestDeviceId: String = "",
    val requestDeviceDisplayName: String = "",
    val sasEmojis: List<MatrixE2eeVerificationEmoji> = emptyList(),
    val sasDecimals: List<String> = emptyList(),
    val message: String? = null,
    val cancelInfo: MatrixE2eeCancelInfo? = null,
) {
    val isBusy: Boolean
        get() = status == MatrixE2eeVerificationStatus.PREPARING ||
            status == MatrixE2eeVerificationStatus.REQUESTING
}

/**
 * Details of a cancelled session-verification flow, sourced from the Matrix SDK's
 * SessionVerificationCancelInfo (fork extension: code + initiator).
 */
data class MatrixE2eeCancelInfo(
    val reason: String,
    val code: String,
    val cancelledByUs: Boolean,
)

data class MatrixE2eeVerificationEmoji(
    val symbol: String,
    val description: String,
)

enum class MatrixE2eeVerificationStatus {
    NOT_PREPARED,
    PREPARING,
    READY,
    REQUESTING,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    ACCEPTED,
    SAS_STARTED,
    SAS_READY,
    VERIFIED,
    CANCELLED,
    FAILED,
    UNAVAILABLE,
    UNSUPPORTED_AUTH,
}

object MatrixE2eeVerificationProvider {
    @Volatile
    private var instance: MatrixE2eeVerification? = null

    fun install(verification: MatrixE2eeVerification) {
        instance = verification
    }

    fun get(): MatrixE2eeVerification = instance ?: DefaultUnavailable

    val isInstalled: Boolean get() = instance != null

    private object DefaultUnavailable : MatrixE2eeVerification {
        private val unavailableState = MutableStateFlow(
            MatrixE2eeVerificationState(
                status = MatrixE2eeVerificationStatus.UNAVAILABLE,
            ),
        )
        override val state: StateFlow<MatrixE2eeVerificationState> = unavailableState.asStateFlow()

        override suspend fun prepare(context: Context, setting: MatrixSetting) = Unit
        override suspend fun requestVerification() = Unit
        override suspend fun acceptRequest() = Unit
        override suspend fun startSas() = Unit
        override suspend fun approve() = Unit
        override suspend fun decline() = Unit
        override suspend fun cancel() = Unit
        override suspend fun revokeDevice(context: android.content.Context, setting: io.github.magisk317.relay.sender.config.MatrixSetting) = Unit
        override fun reset() = Unit
    }
}
