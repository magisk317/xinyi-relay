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
        // matrix-rust-sdk FFI is published to Maven Central
        // (org.matrix.rustcomponents:sdk-android)

        // rustls-platform-verifier Android bindings hosted on GitHub Packages
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/magisk317/xinyi-relay")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
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
    ":magisk-xposed-kit",
    ":features:matrix_e2ee",
)

// Explicitly remap moved smscode-core physical paths
project(":smscode-core:xposed").projectDir = file("smscode/core/xposed")
project(":smscode-core:hook").projectDir = file("smscode/core/hook")
project(":smscode-core:rule").projectDir = file("smscode/core/rule")
project(":smscode-core:domain").projectDir = file("smscode/core/domain")
project(":smscode-core:contract").projectDir = file("smscode/core/contract")
project(":smscode-core:runtime").projectDir = file("smscode/core/runtime")
project(":smscode-core:verification").projectDir = file("smscode/core/verification")
project(":smscode-core").projectDir = file("smscode/core")

// Explicitly remap moved android libraries physical paths to 'modules/'
project(":core").projectDir = file("modules/core")
project(":hook:entry").projectDir = file("modules/hook/entry")
project(":relay:android").projectDir = file("modules/relay/android")
project(":relay:sender:api").projectDir = file("modules/relay/sender/api")
project(":relay:sender").projectDir = file("modules/relay/sender")
project(":relay:contract").projectDir = file("modules/relay/contract")
project(":relay:net").projectDir = file("modules/relay/net")
project(":relay:engine").projectDir = file("modules/relay/engine")
project(":relay:engine:api").projectDir = file("modules/relay/engine/api")
project(":runtime").projectDir = file("modules/runtime")
project(":xpbridge:core").projectDir = file("modules/xpbridge/core")
project(":features:matrix_e2ee").projectDir = file("features/matrix-e2ee")

// Map intermediate projects so Gradle knows their directories
project(":hook").projectDir = file("modules/hook")
project(":relay").projectDir = file("modules/relay")
project(":xpbridge").projectDir = file("modules/xpbridge")
