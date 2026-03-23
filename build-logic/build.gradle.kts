plugins {
    `kotlin-dsl`
}

val libsCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:${libsCatalog.findVersion("agp").get().requiredVersion}")
    implementation(kotlin("gradle-plugin", libsCatalog.findVersion("kotlin").get().requiredVersion))
    implementation("com.google.gms:google-services:${libsCatalog.findVersion("google-services").get().requiredVersion}")
}
