plugins {
    id("magisk.android.library")
    id("magisk.android.room")
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

val mobileEntitlementApiOrigin = providers.gradleProperty("mobileEntitlementApiOrigin")
    .orElse("https://activate.magisk317.qzz.io")
    .get()
val mobileEntitlementSigningPublicJwk = providers.gradleProperty("mobileEntitlementSigningPublicJwk")
    .orElse("""{"kty":"EC","x":"4kPpwUt1wFRuF3EqGq6q57J3YmANf7wyiNH90FNkAbI","y":"U4-E1XK6LjWIXMFNEoSAoik7nD1S07BDb7qAipQd4Ts","crv":"P-256","alg":"ES256","use":"sig","kid":"mobile-entitlement-1"}""")
    .get()

val gitCommitHash = providers.exec {
    commandLine("git", "-C", projectDir, "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim()

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "io.github.magisk317.relay.android"

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }

    defaultConfig {
        buildConfigField("String", "LOG_TAG", "\"xinyi\"")
        buildConfigField("String", "APPLICATION_ID", "\"io.github.magisk317.xinyi.relay\"")
        buildConfigField("int", "LOG_LEVEL", "2")
        buildConfigField("boolean", "LOG_TO_XPOSED", "true")
        buildConfigField("String", "COMMIT_HASH", "\"$gitCommitHash\"")
        buildConfigField("String", "MOBILE_ENTITLEMENT_API_ORIGIN", buildConfigString(mobileEntitlementApiOrigin))
        buildConfigField("String", "MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK", buildConfigString(mobileEntitlementSigningPublicJwk))
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets {
        getByName("play") {
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
            manifest.srcFile("src/xposed/AndroidManifest.xml")
        }
        getByName("githubNoE2ee") {
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
            manifest.srcFile("src/xposed/AndroidManifest.xml")
        }
        getByName("githubWithE2ee") {
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
            manifest.srcFile("src/xposed/AndroidManifest.xml")
        }
        getByName("fdroid") {
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
            manifest.srcFile("src/xposed/AndroidManifest.xml")
        }
    }

}

dependencies {
    implementation(libs.mobile.entitlement.android)
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit"))
    implementation(project(":relay:contract"))
    implementation(project(":relay:engine:api"))
    implementation(project(":relay:sender"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:rule"))
    implementation(project(":smscode-core:runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.timber)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
}
