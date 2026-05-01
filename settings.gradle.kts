pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(
    ":app",
    ":hook-entry",
    ":runtime",
    ":core",
    ":mobile-ui",
    ":relay-android",
    ":relay-contract",
    ":relay-engine",
    ":xpbridge-core",
    ":smscode-core:smscode-xposed-core",
    ":smscode-core:smscode-hook-core",
    ":smscode-core:smscode-rule-core",
    ":smscode-core:smscode-domain",
    ":smscode-core:smscode-runtime-contract",
    ":smscode-core:smscode-runtime-common",
    ":smscode-core:smscode-verification-core",
    ":magisk-ui-kit",
)
