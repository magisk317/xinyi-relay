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
        maven {
            name = "GitLabPackages"
            url = uri(
                providers.gradleProperty("gitlab.maven.url").orNull
                    ?: System.getenv("GITLAB_MAVEN_URL")
                    ?: "https://gitlab.com/api/v4/projects/84113188/packages/maven",
            )
            content {
                includeGroup("rustls")
            }
        }
        maven {
            name = "MagiskMobilePrivate"
            url = uri(
                providers.gradleProperty("mobile.private.maven.url").orNull
                    ?: System.getenv("MOBILE_PRIVATE_MAVEN_URL")
                    ?: "https://gitlab.com/api/v4/projects/85187820/packages/maven",
            )
            content {
                includeGroup("com.magisk317.mobile")
            }
        }
    }
}

include(
    ":app",
    ":desktop",
    ":desktop:data",
    ":desktop:core",
    ":benchmark:macro",
    ":hook:entry",
    ":runtime",
    ":mobile:core",
    ":policy",
    ":mobile:ui",
    ":mobile:feature:common",
    ":mobile:feature:setup",
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
    ":relay:security",
    ":relay:sender:api",
    ":relay:sender",
    ":relay:matrix-e2ee",
    ":relay:contract",
    ":relay:net",
    ":relay:engine",
    ":relay:engine:api",
    ":xpbridge:core",
    ":xpbridge:android:api",
    ":smscode-core:hook",
    ":smscode-core:rule",
    ":smscode-core:domain",
    ":smscode-core:contract",
    ":smscode-core:runtime",
    ":smscode-core:verification",
    ":smscode-core:db",
    ":magisk-ui-kit",
    ":magisk-ui-kit:billing",
    ":magisk-ui-kit:update",
    ":magisk-xposed-kit",
    ":magisk-xposed-kit:logging",
    ":magisk-xposed-kit:diagnostics",
    ":magisk-xposed-kit:capability",
    ":magisk-xposed-kit:runtime",
    ":magisk-forward-core:contract",
    ":magisk-forward-core:capture",
    ":magisk-forward-core:runtime",
    ":magisk-forward-core:engine",
    ":magisk-forward-core:engine:api",
    ":magisk-forward-core:sender:api",
    ":magisk-forward-core:net",
    ":magisk-forward-core:security",
    ":magisk-forward-core:sender",
    ":magisk-forward-core:db",
    ":magisk-forward-core",
    ":magisk-xposed-kit:permission",
    ":magisk-xposed-kit:entitlement",
    ":magisk-xposed-kit:keepalive",
    ":features:matrix_e2ee",
    ":features:matrix_e2ee_plugin",
)

// Explicitly remap moved smscode-core physical paths
project(":smscode-core:hook").projectDir = file("smscode/core/hook")
project(":smscode-core:rule").projectDir = file("smscode/core/rule")
project(":smscode-core:domain").projectDir = file("smscode/core/domain")
project(":smscode-core:contract").projectDir = file("smscode/core/contract")
project(":smscode-core:runtime").projectDir = file("smscode/core/runtime")
project(":smscode-core:verification").projectDir = file("smscode/core/verification")
project(":smscode-core:db").projectDir = file("smscode/core/db")
project(":smscode-core").projectDir = file("smscode/core")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
project(":magisk-xposed-kit:capability").projectDir = file("magisk-xposed-kit/capability")
project(":magisk-xposed-kit:runtime").projectDir = file("magisk-xposed-kit/runtime")
project(":magisk-forward-core:contract").projectDir = file("forward/core/contract")
project(":magisk-forward-core:capture").projectDir = file("forward/core/capture")
project(":magisk-forward-core:runtime").projectDir = file("forward/core/runtime")
project(":magisk-forward-core:engine").projectDir = file("forward/core/engine")
project(":magisk-forward-core:engine:api").projectDir = file("forward/core/engine/api")
project(":magisk-forward-core:sender:api").projectDir = file("forward/core/sender/api")
project(":magisk-forward-core:net").projectDir = file("forward/core/net")
project(":magisk-forward-core:security").projectDir = file("forward/core/security")
project(":magisk-forward-core:sender").projectDir = file("forward/core/sender")
project(":magisk-forward-core:db").projectDir = file("forward/core/db")
project(":magisk-forward-core").projectDir = file("forward/core")
project(":magisk-xposed-kit:permission").projectDir = file("magisk-xposed-kit/permission")
project(":magisk-xposed-kit:entitlement").projectDir = file("magisk-xposed-kit/entitlement")
project(":magisk-xposed-kit:keepalive").projectDir = file("magisk-xposed-kit/keepalive")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
project(":magisk-ui-kit:update").projectDir = file("magisk-ui-kit/update")

// Explicitly remap moved android libraries physical paths to 'modules/'
project(":policy").projectDir = file("modules/policy")
project(":hook:entry").projectDir = file("modules/hook/entry")
project(":relay:android").projectDir = file("modules/relay/android")
project(":relay:sender:api").projectDir = file("modules/relay/sender/api")
project(":relay:security").projectDir = file("modules/relay/security")
project(":relay:sender").projectDir = file("modules/relay/sender")
project(":relay:matrix-e2ee").projectDir = file("modules/relay/matrix-e2ee")
project(":relay:contract").projectDir = file("modules/relay/contract")
project(":relay:net").projectDir = file("modules/relay/net")
project(":relay:engine").projectDir = file("modules/relay/engine")
project(":relay:engine:api").projectDir = file("modules/relay/engine/api")
project(":runtime").projectDir = file("modules/runtime")
project(":xpbridge:core").projectDir = file("modules/xpbridge/core")
project(":xpbridge:android:api").projectDir = file("modules/xpbridge/android/api")
project(":features:matrix_e2ee").projectDir = file("features/matrix-e2ee")
project(":features:matrix_e2ee_plugin").projectDir = file("features/matrix-e2ee-plugin")

// Map intermediate projects so Gradle knows their directories
project(":hook").projectDir = file("modules/hook")
project(":relay").projectDir = file("modules/relay")
project(":xpbridge").projectDir = file("modules/xpbridge")
project(":xpbridge:android").projectDir = file("modules/xpbridge/android")
project(":desktop:data").projectDir = file("modules/desktop/data")
project(":desktop:core").projectDir = file("modules/desktop/core")