package io.github.magisk317.relay.engine.model

/**
 * 路由层唯一需要的通道身份：主键。
 *
 * 平台专属的通道模型（Android 侧的 `Sender`）实现该接口，让引擎侧的过滤与路由逻辑停在
 * commonMain，不把 Parcelable/Date 这类 Android 模型拖进公共源集。
 */
interface SenderIdentity {
    val id: Long
}
