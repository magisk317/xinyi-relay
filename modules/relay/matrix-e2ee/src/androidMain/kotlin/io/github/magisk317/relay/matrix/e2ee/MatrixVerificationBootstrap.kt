package io.github.magisk317.relay.matrix.e2ee
import io.github.magisk317.forward.sender.MatrixE2eeVerification
import io.github.magisk317.forward.sender.config.MatrixSetting
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.SyncSettingsV2

/**
 * Client-side prerequisites for the verification machine: E2EE init wait,
 * own identity availability, device-key refresh and the initial sync.
 * Pure client operations; the machine state stays in the runtime.
 */
internal object MatrixVerificationBootstrap {
    private const val TAG = "MatrixE2eeVerification"

    private const val VERIFICATION_INIT_TIMEOUT_MS = 15_000L
    private const val VERIFICATION_KEY_QUERY_FLUSH_DELAY_MS = 3_000L
    internal suspend fun waitForE2eeInitialization(client: Client) {
        try {
            SLog.d(TAG, "Matrix verification waiting for E2EE init: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
            withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.encryption().waitForE2eeInitializationTasks()
            }
            SLog.d(TAG, "Matrix verification E2EE init completed")
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Matrix verification E2EE init timed out: ${e.message}",
                e,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Matrix verification E2EE init failed: " +
                    "${e.javaClass.simpleName}: ${e.message}",
                e,
            )
        }
    }

    internal suspend fun ensureOwnIdentityAvailable(client: Client) {
        try {
            val session = client.session()
            val identity = withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.encryption().userIdentity(session.userId, true)
            }
            SLog.d(
                TAG,
                "Matrix verification own identity checked: " +
                    "present=${identity != null} verified=${identity?.isVerified()} " +
                    "user=${MatrixVerificationDiagnostics.summarizeValue(session.userId)}",
            )
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Matrix verification own identity check timed out: ${e.message}",
                e,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Matrix verification own identity check failed: " +
                    "${e.javaClass.simpleName}: ${e.message}",
                e,
            )
        }
    }

    internal suspend fun refreshDeviceKeysForVerification(client: Client, setting: MatrixSetting) {
        if (setting.roomId.isBlank()) {
            SLog.d(TAG, "Matrix verification device key refresh skipped: roomId missing")
            return
        }

        val room = runCatching { client.getRoom(setting.roomId) }.getOrElse { error ->
            throw IllegalStateException(
                "Matrix verification room lookup failed before key refresh: " +
                    "${error.javaClass.simpleName}: ${error.message}",
                error,
            )
        } ?: throw IllegalStateException(
            "Matrix verification device key refresh failed: room not found: ${setting.roomId}",
        )

        try {
            val memberCount = withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                room.members().use { members -> members.len() }
            }
            SLog.d(TAG, "Matrix verification room members fetched for key refresh: count=$memberCount")
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Matrix verification member fetch timed out before key refresh: ${e.message}",
                e,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Matrix verification member fetch failed before key refresh: " +
                    "${e.javaClass.simpleName}: ${e.message}",
                e,
            )
        }

        try {
            SLog.d(TAG, "Matrix verification running device key refresh sync")
            val response = withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
            }
            SLog.d(
                TAG,
                "Matrix verification device key refresh sync completed: " +
                    "nextBatch=${MatrixVerificationDiagnostics.summarizeBatch(response.nextBatch)}",
            )
            delay(VERIFICATION_KEY_QUERY_FLUSH_DELAY_MS)
            val encryption = client.encryption()
            val verificationState = runCatching { encryption.verificationState().name }.getOrDefault("UNKNOWN")
            val hasDevices = runCatching { encryption.hasDevicesToVerifyAgainst() }.getOrDefault(false)
            SLog.d(
                TAG,
                "Matrix verification device key refresh state: " +
                    "verificationState=$verificationState hasDevices=$hasDevices",
            )
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Matrix verification device key refresh sync timed out: ${e.message}",
                e,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Matrix verification device key refresh sync failed: " +
                    "${e.javaClass.simpleName}: ${e.message}",
                e,
            )
        }
    }

    internal suspend fun runInitialVerificationSync(client: Client) {
        try {
            SLog.d(TAG, "Matrix verification running initial syncOnce with controller installed")
            withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
            }
            SLog.d(TAG, "Matrix verification initial sync completed")
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Matrix verification initial sync timed out: ${e.message}",
                e,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Matrix verification initial sync failed: " +
                    "${e.javaClass.simpleName}: ${e.message}",
                e,
            )
        }
    }

}
