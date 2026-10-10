import java.io.File
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication

/**
 * Detect which auth header the given GitLab token validates under.
 *
 * GitLab accepts the same token under two headers, but not every token type
 * is accepted under both: classic PATs / project tokens / OAuth tokens all
 * work with `Authorization: Bearer`, while some legacy setups only accept
 * `Private-Token`. We probe once (HEAD on a known private artifact) and cache
 * the winner per token, so a single settings.gradle.kts auto-adapts to
 * whatever token the host supplies (e.g. lzc vs server) — no manual header
 * guesswork. Any failure (offline, timeout, unexpected response) safely
 * falls back to `Authorization: Bearer`, which is the superset accepted by
 * every GitLab personal/group token.
 */
fun resolveGitlabAuthHeader(token: String): Pair<String, String> {
    val cacheFile = File(System.getProperty("user.home"), ".gradle/gitlab_auth_header_cache")
    val key = token.hashCode().toString()
    runCatching {
        if (cacheFile.exists()) {
            cacheFile.readLines()
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.let { name ->
                    val value = if (name == "Authorization") "Bearer $token" else token
                    return name to value
                }
        }
    }
    val probeUrl = "https://gitlab.com/api/v4/projects/85187820/packages/maven/" +
        "com/magisk317/mobile/entitlement-android/0.2.1/entitlement-android-0.2.1.pom"
    val candidates = listOf("Authorization" to "Bearer $token", "Private-Token" to token)
    var chosen: Pair<String, String>? = null
    for ((name, value) in candidates) {
        val ok = runCatching {
            (java.net.URL(probeUrl).openConnection() as java.net.HttpURLConnection).let { conn ->
                conn.requestMethod = "HEAD"
                conn.setRequestProperty(name, value)
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val code = conn.responseCode
                conn.disconnect()
                code != 401
            }
        }.getOrElse { false }
        if (ok) { chosen = name to value; break }
    }
    val result = chosen ?: ("Authorization" to "Bearer $token")
    runCatching {
        cacheFile.parentFile?.mkdirs()
        val kept = runCatching { cacheFile.readLines() }.getOrElse { emptyList() }
            .filter { !it.startsWith("$key=") } + listOf("$key=${result.first}")
        cacheFile.writeText(kept.takeLast(20).joinToString("\n"))
    }
    return result
}

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
        mavenLocal()
        // The fork registry below is the only source for
        // org.matrix.rustcomponents: the fork shares GAVs with Maven
        // Central, so public repos must exclude the group or resolution
        // depends on repository order.
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

            val jobToken = System.getenv("CI_JOB_TOKEN")
            val privateToken = System.getenv("GITLAB_TOKEN")
                ?: System.getenv("GITLAB_PRIVATE_TOKEN")
                ?: providers.gradleProperty("gitlab.token").orNull
            val deployToken = System.getenv("GITLAB_DEPLOY_TOKEN")
                ?: System.getenv("gitlab.deployToken")

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
                    val (headerName, headerValue) = resolveGitlabAuthHeader(privateToken)
                    credentials(HttpHeaderCredentials::class) {
                        name = headerName
                        value = headerValue
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
            val jobToken = System.getenv("CI_JOB_TOKEN")
            val deployToken = System.getenv("GITLAB_DEPLOY_TOKEN")
            val privateToken = System.getenv("GITLAB_TOKEN")
                ?: System.getenv("GITLAB_PRIVATE_TOKEN")
            if (!jobToken.isNullOrBlank()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = jobToken
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            } else if (!deployToken.isNullOrBlank()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Deploy-Token"
                    value = deployToken
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            } else if (!privateToken.isNullOrBlank()) {
                val (headerName, headerValue) = resolveGitlabAuthHeader(privateToken)
                credentials(HttpHeaderCredentials::class) {
                    name = headerName
                    value = headerValue
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
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
    ":core",
    ":policy",
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
    ":magisk-xposed-kit",
    ":magisk-xposed-kit:logging",
    ":magisk-xposed-kit:diagnostics",
    ":magisk-xposed-kit:permission",
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
project(":magisk-xposed-kit:permission").projectDir = file("magisk-xposed-kit/permission")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")

// Explicitly remap moved android libraries physical paths to 'modules/'
project(":core").projectDir = file("modules/core")
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