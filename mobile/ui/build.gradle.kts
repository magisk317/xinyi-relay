plugins {
    id("magisk.android.library")
    id("relay.android.common")
    id("magisk.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "io.github.magisk317.relay.mobileui"

    defaultConfig {
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable.add("MissingTranslation")
        disable.add("LocalContextGetResourceValueCall")
        disable.add("NonObservableLocale")
    }

    sourceSets {
        getByName("githubNoE2ee") {
            setRoot("src/github")
        }
        getByName("githubWithE2ee") {
            setRoot("src/github")
        }
    }

}

dependencies {
    implementation(project(":core"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":mobile:feature:overview"))
    implementation(project(":mobile:feature:settings"))
    implementation(project(":mobile:feature:verification"))
    implementation(project(":mobile:feature:appconfig"))
    implementation(project(":mobile:feature:relayconfig"))
    implementation(project(":mobile:feature:sender"))
    implementation(project(":mobile:feature:forward"))
    implementation(project(":mobile:feature:scheduled"))
    implementation(project(":mobile:feature:record"))
    implementation(project(":mobile:feature:rule"))
    implementation(project(":mobile:feature:backup"))
    implementation(project(":relay:engine:api"))
    implementation(project(":relay:android"))
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:contract"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:rule"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.metrics.performance)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.timber)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    add("playImplementation", libs.play.app.update)
    add("playImplementation", libs.play.services.auth)
    add("githubNoE2eeImplementation", libs.play.services.auth)
    add("githubWithE2eeImplementation", libs.play.services.auth)
}
