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
    ":runtime",
    ":core",
    ":xpbridge-core",
    ":smscode-core:smscode-xposed-core",
    ":smscode-core:smscode-domain",
    ":smscode-core:smscode-runtime-common",
    ":smscode-core:smscode-verification-core",
    ":magisk-ui-kit",
)
