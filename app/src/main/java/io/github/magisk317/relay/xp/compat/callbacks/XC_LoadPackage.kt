package io.github.magisk317.relay.xp.compat.callbacks

class XC_LoadPackage private constructor() {
    class LoadPackageParam {
        var packageName: String = ""
        var processName: String = ""
        lateinit var classLoader: ClassLoader
    }
}
