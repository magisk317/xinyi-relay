package io.github.magisk317.relay.matrix.e2ee

import java.security.MessageDigest
import kotlinx.coroutines.TimeoutCancellationException

/**
 * E2EE 发送链路的纯决策逻辑。
 *
 * 这些函数只有输入输出，不碰 Android `Context`、Matrix Rust SDK 或 OkHttp，所以单独留在
 * commonMain 里，让 `MatrixE2eeRuntime`（androidMain，绑 Context 与 SDK）只做装配与副作用。
 * 原先它们藏在 `MatrixE2eeRuntime` 内部，导致 [RoomCryptoState] 之外的所有决策都无法在
 * 不起 Android 测试编译的情况下被验证。
 */
internal object MatrixE2eeSendPolicy {

    /** 中继固定使用的 Matrix 设备名，登录缓存只在同一设备名下复用。 */
    const val LOGIN_DEVICE_ID = "XINYI_RELAY_E2EE"

    const val OP_SYNC_TO_DEVICE = "sync_to_device"
    const val OP_ENCRYPT_AND_SEND = "encrypt_and_send"
    const val OP_ACQUIRE_SEND_LOCK = "acquire_send_lock"
    const val OP_POST_SEND_SYNC = "post_send_sync"
    const val OP_RELEASE_SEND_LOCK = "release_send_lock"

    const val FAILURE_ENCRYPTION_TIMEOUT = "encryption_timeout"
    const val FAILURE_ENCRYPTION_ERROR = "encryption_error"

    /**
     * 是否走 E2EE 通道。
     *
     * 只有当 E2EE 模块可用（[e2eeAvailable]）且目标房间已加密（[roomEncrypted]）时才加密发送，
     * 任一不满足都退回明文路径——避免在明文房间上误加密，或在模块缺失时误判为不可发送。
     */
    fun shouldUseE2ee(e2eeAvailable: Boolean, roomEncrypted: Boolean): Boolean =
        e2eeAvailable && roomEncrypted

    /**
     * 登录会话缓存是否可复用：设备名必须是中继固定设备，且 homeserver 归一化后一致。
     *
     * homeserver 末尾斜杠与首尾空白不参与比较，用户改配置时不会因为书写差异丢掉可用缓存。
     */
    fun shouldReuseLoginSession(
        cachedDeviceId: String,
        cachedHomeserverUrl: String,
        expectedHomeserverUrl: String,
    ): Boolean = cachedDeviceId == LOGIN_DEVICE_ID &&
        normalizeHomeserverForSession(cachedHomeserverUrl) ==
        normalizeHomeserverForSession(expectedHomeserverUrl)

    fun normalizeHomeserverForSession(homeserver: String): String =
        homeserver.trim().trimEnd('/')

    /**
     * 校验 E2EE 发送流水线的操作顺序满足「to-device 同步先于加密」。
     *
     * to-device 事件负责下发新的 Megolm 房间密钥，若在它之前就加密发送，用的是过期会话状态。
     */
    fun verifySendOperationOrder(operations: List<String>): Boolean {
        val syncIndex = operations.indexOf(OP_SYNC_TO_DEVICE)
        val encryptIndex = operations.indexOf(OP_ENCRYPT_AND_SEND)
        if (syncIndex == -1 || encryptIndex == -1) return false
        return syncIndex < encryptIndex
    }

    fun getCanonicalSendOperationOrder(): List<String> =
        listOf(OP_SYNC_TO_DEVICE, OP_ENCRYPT_AND_SEND)

    /**
     * 串行化发送流水线：同一时刻只允许一次发送操作共享的 SDK 客户端、房间密钥与设备密钥同步。
     */
    fun getCanonicalSerializedSendOperationOrder(): List<String> = listOf(
        OP_ACQUIRE_SEND_LOCK,
        OP_SYNC_TO_DEVICE,
        OP_ENCRYPT_AND_SEND,
        OP_POST_SEND_SYNC,
        OP_RELEASE_SEND_LOCK,
    )

    fun verifySerializedSendOperationOrder(operations: List<String>): Boolean {
        val acquireIndex = operations.indexOf(OP_ACQUIRE_SEND_LOCK)
        val syncIndex = operations.indexOf(OP_SYNC_TO_DEVICE)
        val encryptIndex = operations.indexOf(OP_ENCRYPT_AND_SEND)
        val postSendIndex = operations.indexOf(OP_POST_SEND_SYNC)
        val releaseIndex = operations.indexOf(OP_RELEASE_SEND_LOCK)
        if (acquireIndex == -1 ||
            syncIndex == -1 ||
            encryptIndex == -1 ||
            postSendIndex == -1 ||
            releaseIndex == -1
        ) {
            return false
        }
        return acquireIndex < syncIndex &&
            syncIndex < encryptIndex &&
            encryptIndex < postSendIndex &&
            postSendIndex < releaseIndex
    }

    /**
     * 把异常归类为日志用的失败原因。
     *
     * 协程超时（`withTimeout`）与 socket 读超时都算超时，其余算未知错误。socket 超时分支需要
     * `java.net.SocketTimeoutException`，由 androidMain 的包装层补齐（见
     * `MatrixE2eeRuntime.categorizeFailureReason`）。
     */
    fun categorizeFailureReason(error: Exception): String = when (error) {
        is TimeoutCancellationException -> FAILURE_ENCRYPTION_TIMEOUT
        else -> FAILURE_ENCRYPTION_ERROR
    }

    /** 计算 SHA-256 十六进制串。crypto store 目录名由它派生，必须是确定性的纯函数。 */
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
