import com.android.build.api.dsl.CommonExtension
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

    productFlavors {
        maybeCreate("play").apply {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "false")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
        }
        maybeCreate("github").apply {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
        }
        maybeCreate("fdroid").apply {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "false")
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
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<CommonExtension> {
        configureRelayAndroidCommon()
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
