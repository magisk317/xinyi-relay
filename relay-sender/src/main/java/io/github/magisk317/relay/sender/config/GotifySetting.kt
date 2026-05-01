package io.github.magisk317.relay.sender.config

import java.io.Serializable

data class GotifySetting(
    var webServer: String = "",
    val title: String = "",
    val priority: String = "",
) : Serializable
