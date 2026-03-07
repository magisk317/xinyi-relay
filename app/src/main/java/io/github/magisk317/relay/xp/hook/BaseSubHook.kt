package io.github.magisk317.relay.xp.hook

abstract class BaseSubHook(@JvmField protected val mClassLoader: ClassLoader) {
    abstract fun startHook()
}
