package com.github.magisk317.smscode.xp.hook

abstract class BaseSubHook(@JvmField protected val mClassLoader: ClassLoader) {
    abstract fun startHook()
}
