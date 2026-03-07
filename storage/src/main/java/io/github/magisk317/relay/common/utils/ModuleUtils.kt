package io.github.magisk317.relay.common.utils

import android.content.Context

/**
 * 当前Xposed模块相关工具类
 */
object ModuleUtils {

    @Volatile
    private var runtimeActivated = false

    /**
     * 标记当前运行时是否已连接到 Xposed Service。
     */
    @JvmStatic
    fun setRuntimeActivated(activated: Boolean) {
        runtimeActivated = activated
    }

    @JvmStatic
    fun isRuntimeActivated(): Boolean = runtimeActivated

    /**
     * 模块是否已激活（兼容新 API 静态作用域场景）
     */
    @JvmStatic
    fun isModuleActivated(context: Context): Boolean {
        return runtimeActivated || ModuleActivationStore.isActivatedRecently(context)
    }
}
