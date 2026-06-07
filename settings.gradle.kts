pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            name = "JitPack"
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
            name = "SonatypeSnapshots"
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

include(
    ":app",
    ":hook:entry",
    ":runtime",
    ":core",
    ":mobile:ui",
    ":mobile:feature:common",
    ":mobile:feature:overview",
    ":mobile:feature:settings",
    ":mobile:feature:verification",
    ":mobile:feature:appconfig",
    ":mobile:feature:relayconfig",
    ":mobile:feature:sender",
    ":mobile:feature:forward",
    ":mobile:feature:scheduled",
    ":mobile:feature:record",
    ":mobile:feature:rule",
    ":mobile:feature:backup",
    ":relay:android",
    ":relay:sender:api",
    ":relay:sender",
    ":relay:contract",
    ":relay:net",
    ":relay:engine",
    ":relay:engine:api",
    ":xpbridge:core",
    ":smscode-core:xposed",
    ":smscode-core:hook",
    ":smscode-core:rule",
    ":smscode-core:domain",
    ":smscode-core:contract",
    ":smscode-core:runtime",
    ":smscode-core:verification",
    ":magisk-ui-kit",
)
