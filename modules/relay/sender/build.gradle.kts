plugins {
    id("magisk.android.library")
    id("relay.android.common")
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
}

android {
    namespace = "io.github.magisk317.relay.sender"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }

    sourceSets {
        // Play keeps the dynamic-feature path; GitHub keeps the plaintext
        // MatrixE2eeUtils/MatrixE2eeVerificationManager stubs (the real
        // implementation arrives at runtime from the e2ee plugin APK).
        listOf("play", "github").forEach { flavor ->
            getByName(flavor).kotlin.directories.add("src/matrixE2eeStub/kotlin")
        }
        getByName("github").kotlin.directories.add("src/nonPlaySms/kotlin")
    }
}

dependencies {
    api(project(":magisk-xposed-kit:logging"))
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:security"))
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
    // Play Feature Delivery for on-demand E2EE module installation
    add("playImplementation", libs.play.feature.delivery)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.platform.launcher)
}
