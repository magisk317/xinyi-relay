package io.github.magisk317.relay.xpbridge

import io.github.magisk317.relay.contract.constant.CodeNotificationOwner

object XpCodeNotificationOwner {
    const val APP: String = CodeNotificationOwner.APP
    const val PHONE: String = CodeNotificationOwner.PHONE

    fun normalize(value: String?): String = CodeNotificationOwner.normalize(value)
}
