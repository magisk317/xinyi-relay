package io.github.magisk317.relay.model.setting

import java.io.Serializable

data class NtfySetting(
    var server: String = "",
    var topic: String = "",
    var token: String = "",
    var title: String = "",
    var priority: String = "3",
    var tags: String = "",
) : Serializable
