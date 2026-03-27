# ==========================
# jsoup proguard start
-keeppackagenames org.jsoup.nodes
# jsoup proguard end
# ==========================


# ==========================
# okhttp3 start
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# okhttp3 end
# ==========================


# ==========================
# okio start
-dontwarn okio.**
# okio end
# ==========================

# ==========================
# retrofit2 start
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
# retrofit2 end
# ==========================

# ==========================
# Kotlin Serialization start
-keepattributes *Annotation*
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$Companion$* {
    ** INSTANCE;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-dontwarn kotlinx.serialization.**
# Kotlin Serialization end
# ==========================

# ==========================
# Room start
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class io.github.magisk317.relay.data.db.AppDatabase_Impl {
    public <init>();
}
# Room end
# ==========================

# ==========================
# Xposed start
-keep class io.github.magisk317.relay.xp.LibXposedEntry { *; }
-keep class io.github.magisk317.relay.xp.HookEntry { *; }
-keep class io.github.magisk317.relay.xp.hook.** { *; }
-keep class io.github.magisk317.relay.xp.compat.** { *; }
-keep class io.github.magisk317.relay.xp.runtime.** { *; }
# Xposed end
# ==========================

# ==========================
# Jakarta Mail / SMTP start
-keep class jakarta.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn jakarta.mail.**
-dontwarn com.sun.mail.**
# Jakarta Mail / SMTP end
# ==========================

# ==========================
# Ktor debug detector (JVM-only management API) start
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
# Ktor debug detector end
# ==========================

# ==========================
# Ktor Netty on Android start
# Netty contains optional integrations for OpenSSL/JFR/JNDI/Log4j which are not
# packaged in Android runtime. Suppress missing-class warnings for these paths.
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn io.netty.internal.tcnative.**
-dontwarn javax.naming.ldap.**
-dontwarn jdk.jfr.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
# Ktor Netty on Android end
# ==========================
