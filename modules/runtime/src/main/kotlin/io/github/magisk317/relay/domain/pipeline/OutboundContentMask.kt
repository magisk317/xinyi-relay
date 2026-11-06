package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.forward.engine.model.MsgInfo

/**
 * Outbound content masking for forwarded messages.
 *
 * When enabled, the sender identity (phone number, package name, contact
 * name) is masked and every occurrence of the parsed verification code is
 * replaced inside the outgoing text. The message body itself stays intact:
 * this keeps the content stream readable while removing the two fields the
 * mask feature targets. The default path is a no-op passthrough.
 */
object OutboundContentMask {
    private const val KEEP_TAIL_CHARS = 4
    private const val KEEP_TAIL_THRESHOLD = 8

    fun apply(msgInfo: MsgInfo, enabled: Boolean): MsgInfo {
        if (!enabled) return msgInfo
        val code = msgInfo.smsCode.trim()
        return msgInfo.copy(
            from = maskSender(msgInfo.from),
            contactName = maskSender(msgInfo.contactName),
            content = maskCode(msgInfo.content, code),
            message = maskCode(msgInfo.message, code),
            title = maskCode(msgInfo.title, code),
        )
    }

    private fun maskSender(value: String): String {
        // Short values (short contact names) would leak most of their content if the
        // tail stayed visible, so only long values keep the last four characters.
        if (value.length < KEEP_TAIL_THRESHOLD) return "*".repeat(value.length)
        return "*".repeat(value.length - KEEP_TAIL_CHARS) + value.takeLast(KEEP_TAIL_CHARS)
    }

    private fun maskCode(text: String, code: String): String {
        if (code.isEmpty() || !text.contains(code)) return text
        return text.replace(code, "*".repeat(code.length))
    }
}
