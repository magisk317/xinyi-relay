package io.github.magisk317.relay.model.setting

import java.io.Serializable

data class GotifySetting(
    var webServer: String = "",
    val title: String = "",
    val priority: String = "",
) : Serializable