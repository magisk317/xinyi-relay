package io.github.magisk317.relay.xp.hook.me

import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.smscode.xposed.hook.me.ModuleUtilsHook as SharedModuleUtilsHook

class ModuleUtilsHook : SharedModuleUtilsHook(
    targetPackage = BuildConfig.APPLICATION_ID,
    moduleVersion = BuildConfig.MODULE_VERSION,
)
