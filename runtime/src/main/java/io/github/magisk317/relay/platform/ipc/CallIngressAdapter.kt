package io.github.magisk317.relay.platform.ipc

object CallIngressAdapter {
    fun ringingPayload(
        packageName: String,
        fallbackTitle: String,
        phoneNumber: String?,
        incomingBody: String,
        company: String,
        timestamp: Long,
        callType: Int,
    ): ForwardBroadcastPayload {
        val display = displayName(phoneNumber, fallbackTitle)
        return ForwardPayloadFactory.callPayload(
            packageName = packageName,
            sender = display,
            body = incomingBody,
            company = company,
            timestamp = timestamp,
            callType = callType,
            callStage = "ringing",
        )
    }

    fun stagePayload(
        packageName: String,
        fallbackTitle: String,
        phoneNumber: String?,
        body: String,
        company: String,
        timestamp: Long,
        callType: Int,
        stage: String,
    ): ForwardBroadcastPayload {
        val display = displayName(phoneNumber, fallbackTitle)
        return ForwardPayloadFactory.callPayload(
            packageName = packageName,
            sender = display,
            body = body,
            company = company,
            timestamp = timestamp,
            callType = callType,
            callStage = stage,
        )
    }

    fun displayName(
        phoneNumber: String?,
        fallbackTitle: String,
    ): String {
        return phoneNumber?.ifBlank { null } ?: fallbackTitle
    }
}
