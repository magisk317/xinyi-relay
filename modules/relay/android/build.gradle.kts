plugins {
    id("magisk.android.library")
    alias(libs.plugins.ksp)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

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
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("String", "LOG_TAG", "\"relay\"")
        buildConfigField("String", "APPLICATION_ID", "\"io.github.magisk317.xinyi.relay\"")
        buildConfigField("int", "LOG_LEVEL", "2")
        buildConfigField("boolean", "LOG_TO_XPOSED", "true")
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
    implementation(project(":magisk-xposed-kit:logging"))
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.timber)

    listOf("play", "githubNoE2ee", "githubWithE2ee", "fdroid").forEach { flavor ->
        add("${flavor}Implementation", project(":magisk-xposed-kit"))
    }

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
