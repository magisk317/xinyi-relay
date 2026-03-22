package io.github.magisk317.relay.model.setting

import java.io.Serializable

data class ServerchanSetting(
    var sendKey: String = "",
    var channel: String = "",
    var openid: String = "",
    var titleTemplate: String = "",
) : Serializable