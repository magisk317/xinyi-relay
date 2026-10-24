plugins {
    id("magisk.android.library")
    id("magisk.android.distribution")
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
        getByName("github") {
            kotlin.directories.add("src/nonPlay/kotlin")
        }
    }

}

dependencies {
    implementation(libs.okhttp)
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":mobile:core"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":mobile:feature:setup"))
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
    implementation(project(":magisk-ui-kit:update"))
    implementation(libs.mobile.entitlement.android)
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:db"))
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
    add("playImplementation", libs.play.services.auth)
    add("githubImplementation", libs.play.services.auth)
}
