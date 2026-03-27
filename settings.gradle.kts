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
    ":smscode-core:smscode-xposed-core",
    ":smscode-core:smscode-domain",
    ":smscode-core:smscode-verification-core",
    ":xposed-stub",
    ":magisk-ui-kit",
)
