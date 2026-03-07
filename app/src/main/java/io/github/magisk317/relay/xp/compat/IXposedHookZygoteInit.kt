package io.github.magisk317.relay.xp.compat

interface IXposedHookZygoteInit {
    @Throws(Throwable::class)
    fun initZygote(startupParam: StartupParam)

    class StartupParam {
        var modulePath: String? = null
        var startsSystemServer: Boolean = false
    }
}
