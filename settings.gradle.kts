import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication

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
        maven {
            name = "GitLabPackages"
            url = uri(
                providers.gradleProperty("gitlab.maven.url").orNull
                    ?: System.getenv("GITLAB_MAVEN_URL")
                    ?: "https://gitlab.com/api/v4/projects/84113188/packages/maven",
            )

            val jobToken = System.getenv("CI_JOB_TOKEN")
            val privateToken = System.getenv("GITLAB_TOKEN")
                ?: System.getenv("GITLAB_PRIVATE_TOKEN")
                ?: providers.gradleProperty("gitlab.token").orNull
            val deployToken = System.getenv("GITLAB_DEPLOY_TOKEN")
                ?: providers.gradleProperty("gitlab.deployToken").orNull

            when {
                !jobToken.isNullOrBlank() -> {
                    credentials(HttpHeaderCredentials::class) {
                        name = "Job-Token"
                        value = jobToken
                    }
                    authentication {
                        create<HttpHeaderAuthentication>("header")
                    }
                }
                !privateToken.isNullOrBlank() -> {
                    credentials(HttpHeaderCredentials::class) {
                        name = "Private-Token"
                        value = privateToken
                    }
                    authentication {
                        create<HttpHeaderAuthentication>("header")
                    }
                }
                !deployToken.isNullOrBlank() -> {
                    credentials(HttpHeaderCredentials::class) {
                        name = "Deploy-Token"
                        value = deployToken
                    }
                    authentication {
                        create<HttpHeaderAuthentication>("header")
                    }
                }
            }

            content {
                includeGroup("org.matrix.rustcomponents")
                includeGroup("rustls")
            }
        }
    }
}

include(
    ":app",
    ":benchmark:macro",
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
