plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.sender"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        // Each e2ee flavor gets its own MatrixE2eeUtils implementation.
        // The "main" source set contains the shared logic; the flavor
        // source sets provide the E2EE-aware or plain-text fallback.
        getByName("noE2ee") {
            java.srcDir("src/noE2ee/java")
        }
        getByName("withE2ee") {
            java.srcDir("src/withE2ee/java")
        }
    }

    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }

}

dependencies {
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:contract"))
    implementation(project(":relay:engine:api"))
    implementation(project(":relay:net"))
    implementation(project(":smscode-core:contract"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jakarta.mail)
    implementation(libs.paho.mqtt)
    // matrix-rust-sdk FFI for E2EE support
    // Published as "sdk-android" on Maven Central by element-hq
    add("withE2eeImplementation", libs.matrix.sdk.android)
    // Play Feature Delivery for on-demand E2EE module installation
    add("playImplementation", libs.play.feature.delivery)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
