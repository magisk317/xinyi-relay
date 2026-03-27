import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun versionString(name: String): String = libsCatalog.findVersion(name).get().requiredVersion

fun CommonExtension.configureRelayAndroidCommon() {
    compileSdk = versionString("compileSdk").toInt()
    compileSdkExtension = versionString("compileSdkExtension").toInt()

    if (!flavorDimensions.contains("distribution")) {
        flavorDimensions += "distribution"
    }
    if (!flavorDimensions.contains("xposedApi")) {
        flavorDimensions += "xposedApi"
    }

    productFlavors {
        maybeCreate("play").apply {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "false")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
            buildConfigField("boolean", "ENABLE_ACCESSIBILITY_AUTO_INPUT", "false")
        }
        maybeCreate("github").apply {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
            buildConfigField("boolean", "ENABLE_ACCESSIBILITY_AUTO_INPUT", "true")
        }
        maybeCreate("fdroid").apply {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "false")
            buildConfigField("boolean", "ENABLE_ACCESSIBILITY_AUTO_INPUT", "true")
        }
        maybeCreate("legacy").apply {
            dimension = "xposedApi"
            buildConfigField("String", "XPOSED_API_FLAVOR", "\"legacy\"")
        }
        maybeCreate("api101").apply {
            dimension = "xposedApi"
            buildConfigField("String", "XPOSED_API_FLAVOR", "\"api101\"")
        }
    }

    compileOptions.apply {
        val javaVersion = JavaVersion.toVersion(versionString("javaBytecode"))
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<CommonExtension> {
        configureRelayAndroidCommon()
    }
    extensions.getByType<ApplicationAndroidComponentsExtension>().beforeVariants(
        extensions.getByType<ApplicationAndroidComponentsExtension>().selector().all(),
    ) { variantBuilder ->
        if (variantBuilder.productFlavors.toMap()["distribution"] == "fdroid") {
            variantBuilder.enable = false
        }
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<CommonExtension> {
        configureRelayAndroidCommon()
    }
    extensions.getByType<LibraryAndroidComponentsExtension>().beforeVariants(
        extensions.getByType<LibraryAndroidComponentsExtension>().selector().all(),
    ) { variantBuilder ->
        if (variantBuilder.productFlavors.toMap()["distribution"] == "fdroid") {
            variantBuilder.enable = false
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
