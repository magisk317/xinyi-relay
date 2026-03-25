pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":app", ":runtime", ":core", ":smscode-core:smscode-xposed-core", ":smscode-core:smscode-domain", ":magisk-ui-kit")
project(":magisk-ui-kit").projectDir = file("../magisk-ui-kit")
