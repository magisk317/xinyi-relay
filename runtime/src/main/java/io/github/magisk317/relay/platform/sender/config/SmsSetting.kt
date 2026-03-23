package io.github.magisk317.relay.platform.sender.config

import java.io.Serializable

data class SmsSetting(
    var simSlot: Int = 0,
    var mobiles: String = "",
    var onlyNoNetwork: Boolean = false,
) : Serializable {

}
