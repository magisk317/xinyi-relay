plugins {
    alias(libs.plugins.android.library)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.magisk317.relay.storage"
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkExtension = libs.versions.compileSdkExtension.get().toInt()

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "false")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
        }
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "false")
        }
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

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("String", "LOG_TAG", "\"relay\"")
        buildConfigField("String", "APPLICATION_ID", "\"io.github.magisk317.xinyi.relay\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("int", "LOG_LEVEL", "4")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("int", "LOG_LEVEL", "2")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
    testOptions {
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":smscode-core:core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson)
    
    // Networking (needed by GithubUpdateChecker)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.jakarta.mail)
    implementation(libs.paho.mqtt)
    
    // Database (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    implementation(libs.timber)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
