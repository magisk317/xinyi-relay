# The host app loads this APK through a DexClassLoader and reflectively calls
# PluginEntry.install(Context). Nothing inside the plugin references that entry
# point, so R8 must be told to keep it or the release build strips the class and
# every provider installation fails at runtime with ClassNotFoundException.
-keep class io.github.magisk317.relay.matrix.e2ee.plugin.PluginEntry {
    public static void install(android.content.Context);
}

# The entry point keeps the relay/matrix-e2ee runtime alive through direct
# references; the matrix-rust-sdk AAR ships its own consumer rules for the
# JNI/native surface.
-dontwarn org.matrix.rustcomponents.sdk.**
-dontwarn kotlinx.coroutines.**
