package io.github.magisk317.relay.receiver

object AutoInputActions {
    const val ACTION_NAMESPACE = "io.github.magisk317.relay"

    const val ACTION_AUTO_INPUT = "$ACTION_NAMESPACE.ACTION_AUTO_INPUT"
    const val ACTION_AUTO_INPUT_RESULT = "$ACTION_NAMESPACE.ACTION_AUTO_INPUT_RESULT"

    val requestAction: String
        get() = ACTION_AUTO_INPUT

    val resultAction: String
        get() = ACTION_AUTO_INPUT_RESULT
}
