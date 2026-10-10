# ==========================
# Xposed module entry / hooks
# Loaded reflectively by LSPosed via META-INF/xposed/java_init.list.
# Must stay alive under R8; app process code does not reference these classes.
# ==========================
-keep class io.github.magisk317.relay.xp.** { *; }
-keep class io.github.magisk317.xposed.** { *; }
-keep class io.github.magisk317.smscode.xposed.** { *; }

# Safety net for Hooker implementations outside the packages above. The two production
# implementations both live in io.github.magisk317.xposed, so this rule is redundant today, but
# without it a Hooker added elsewhere would have intercept() renamed and its hooks would silently
# never fire (AbstractMethodError at dispatch time).
-keepclassmembers class * implements io.github.libxposed.api.XposedInterface$Hooker {
    <methods>;
}

# LSPosed instantiates the entry reflectively; keep both constructor forms.
-keepclassmembers class * extends io.github.libxposed.api.XposedModule {
    <init>(...);
}

# LibXposed API is provided at runtime by LSPosed framework (compileOnly dependency).
# Suppress R8 missing-class errors for these interfaces/classes.
-dontwarn io.github.libxposed.api.**

# ==========================
# Matrix E2EE plugin contract start
# The GitHub flavor loads features/matrix-e2ee-plugin as a separate APK through a
# DexClassLoader (E2eePluginLoader). That plugin is built unminified and bundles
# its own copies of the relay modules and of kotlin/kotlinx/androidx, so
# parent-first delegation resolves every name against this app first. Three
# failure modes follow:
#   * a contract type renamed here - the plugin's bundled copy loads instead, its
#     method descriptors no longer match this app's interfaces, and every call
#     fails with AbstractMethodError (MatrixE2eeVerification.getState is the
#     first one the UI hits);
#   * a contract type shrunk away here - the plugin cannot resolve it at all,
#     because it only compiles against the api surface (NoClassDefFoundError);
#   * a member shrunk out of a class that survives here - the plugin calls it and
#     dies with NoSuchMethodError whose only message is the missing signature.
#     R8 strips every Intrinsics null check from this app's own frames, so
#     checkNotNullParameter is gone while kotlin.jvm.internal.Intrinsics itself
#     survives, and the plugin's very first statement - the parameter null check
#     on PluginEntry.install - fails behind a null-message
#     InvocationTargetException from the reflective entry point.
# Keep the whole relay namespace and every namespace the plugin bundles
# unobfuscated and unshrunk. -keep,allowshrinking is not enough: it preserves the
# names of reachable classes but still lets R8 delete their members, and the
# plugin needs those members even when this app itself never calls them.
-keep class io.github.magisk317.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
# Library consumer rules keep these androidx types unobfuscated in this app while
# the plugin bundles its own copies, so parent-first delegation resolves them
# here; their members must stay complete for the plugin as well.
-keep class androidx.core.app.** { *; }
-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.core.graphics.drawable.IconCompat** { *; }
-keep class androidx.core.widget.NestedScrollView { *; }
-keep class androidx.datastore.** { *; }
-keep class androidx.concurrent.** { *; }
-keep class androidx.versionedparcelable.** { *; }
-keep class androidx.startup.** { *; }
-keep class androidx.profileinstaller.** { *; }
-keep class androidx.savedstate.serialization.** { *; }
# Matrix E2EE plugin contract end
# ==========================

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
# rustls-platform-verifier start
-keep, includedescriptorclasses class org.rustls.platformverifier.** { *; }
# rustls-platform-verifier end
# ==========================
