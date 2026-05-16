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
    ":hook-entry",
    ":runtime",
    ":core",
    ":mobile-ui",
    ":relay-android",
    ":relay-sender",
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
