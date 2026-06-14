package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.sender.config.MatrixSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MatrixE2eeVerificationManager : MatrixE2eeVerification {
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
    override fun reset() = Unit
}
