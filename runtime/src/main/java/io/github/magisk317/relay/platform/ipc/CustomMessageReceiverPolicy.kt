package io.github.magisk317.relay.platform.ipc

object CustomMessageReceiverPolicy {
    fun isTokenAccepted(receivedToken: String?, expectedToken: String): Boolean {
        return expectedToken.isNotBlank() && receivedToken == expectedToken
    }

    fun normalizeTargetSenderIds(targetSenderIds: LongArray?): List<Long>? {
        return targetSenderIds
            ?.asList()
            ?.filter { it > 0L }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
    }

    fun resolvePackageName(explicitPackageName: String?, sentFromPackage: String?): String {
        return explicitPackageName?.trim().orEmpty().ifBlank { sentFromPackage?.trim().orEmpty() }
    }

    fun resolveAppName(explicitAppName: String?, packageName: String): String {
        return explicitAppName?.trim().orEmpty().ifBlank { packageName }
    }

    fun resolveNotifyChannelId(explicitNotifyChannelId: String?): String {
        return explicitNotifyChannelId?.trim().orEmpty()
            .ifBlank { CustomMessageBroadcastContract.DEFAULT_NOTIFY_CHANNEL_ID }
    }

    fun resolveEventId(
        eventId: String?,
        message: String,
        title: String,
        packageName: String,
    ): String {
        return eventId?.trim().orEmpty().ifBlank {
            ForwardBroadcastContract.buildEventId(
                prefix = "custom",
                seed = "$packageName|$title|$message",
            )
        }
    }
}
