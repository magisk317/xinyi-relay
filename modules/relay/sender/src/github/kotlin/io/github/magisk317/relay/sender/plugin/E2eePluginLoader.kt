package io.github.magisk317.relay.sender.plugin

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dalvik.system.DexClassLoader
import io.github.magisk317.relay.sender.BuildConfig
import io.github.magisk317.relay.sender.E2eeModuleStatus
import io.github.magisk317.relay.sender.MatrixE2eeSenderProvider
import io.github.magisk317.relay.sender.MatrixE2eeVerificationManager
import io.github.magisk317.relay.sender.MatrixE2eeVerificationProvider
import io.github.magisk317.relay.sender.SLog
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Downloads, verifies and DexClassLoader-loads the Matrix E2EE plugin APK.
 *
 * The plugin is published per upstream matrix-rust-sdk version under
 * `xinyi-e2ee-plugin/<sdkVersion>/` in the GitLab generic package registry, so
 * this loader only ever fetches the exact version the host app was built
 * against. Security model: the plugin APK must carry the same signing
 * certificate as the host app (release keystore), which is enforced before any
 * plugin code runs.
 *
 * The DexClassLoader and its entry method stay cached for the life of the
 * process. Neither can be rebuilt after an in-process uninstall: a
 * DexClassLoader cannot be unloaded, and ART binds every native library to the
 * classloader that opened it first, so a second loader fails to re-open
 * libjnidispatch.so / libmatrix_sdk_ffi.so (JNA then falls back to a classpath
 * resource the plugin APK does not carry). Re-activating the module after an
 * uninstall therefore reuses the resident loader instead of building a new one.
 */
internal class E2eePluginLoader {

    @Volatile
    private var currentState: E2eeModuleStatus = E2eeModuleStatus.NOT_INSTALLED

    @Volatile
    private var currentProgress: Int = 0

    @Volatile
    private var lastError: String? = null

    private var appContext: Context? = null

    /**
     * Plugin class loader and entry method of the current process, or null
     * while the module has never been loaded here. Guarded by [mutex]; see the
     * class KDoc for why re-install must reuse it.
     */
    private var residentPlugin: ResidentPlugin? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    val state: E2eeModuleStatus get() = currentState
    val progress: Int get() = currentProgress
    val errorMessage: String? get() = lastError

    /** Load an already downloaded plugin on process start, if any. */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch { loadCachedIfPossible(context.applicationContext) }
    }

    /**
     * Download (if needed) and load the E2EE plugin, then install the
     * sender/verification providers from inside the plugin. Callbacks fire on
     * the main thread.
     */
    fun install(
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
    ) {
        if (currentState == E2eeModuleStatus.AVAILABLE) {
            onSuccess?.invoke()
            return
        }
        if (currentState == E2eeModuleStatus.DOWNLOADING) return
        val context = appContext ?: run {
            onFailure?.invoke("E2EE plugin loader not initialized")
            return
        }
        scope.launch {
            mutex.withLock {
                if (currentState == E2eeModuleStatus.AVAILABLE) {
                    withContext(Dispatchers.Main) { onSuccess?.invoke() }
                    return@withLock
                }
                currentState = E2eeModuleStatus.DOWNLOADING
                currentProgress = 0
                lastError = null
                val failure = runCatching {
                    val apk = downloadPlugin(context) { percent ->
                        currentProgress = percent
                        scope.launch(Dispatchers.Main) { onProgress?.invoke(percent) }
                    }
                    verifyManifest(context, apk)
                    verifySignature(context, apk)
                    activatePlugin(context, apk)
                }.exceptionOrNull()
                if (failure == null) {
                    currentState = E2eeModuleStatus.AVAILABLE
                    currentProgress = 100
                    withContext(Dispatchers.Main) { onSuccess?.invoke() }
                } else {
                    val message = failure.describeFailure()
                    lastError = message
                    currentState = if (currentState == E2eeModuleStatus.DOWNLOADING) {
                        E2eeModuleStatus.INSTALL_FAILED
                    } else {
                        E2eeModuleStatus.LOAD_FAILED
                    }
                    SLog.e(TAG, "E2EE plugin install failed: $message", failure)
                    withContext(Dispatchers.Main) { onFailure?.invoke(message) }
                }
            }
        }
    }

    /**
     * Remove the downloaded plugin and fall back to plaintext delivery.
     *
     * Deletes the plugin APK, its checksum sidecar, the extracted native
     * libraries and the optimized dex cache (roughly 64 MB per ABI split),
     * drops the plugin-provided sender and restores the plaintext
     * verification stub. The already-loaded plugin classes stay resident for
     * the life of the process — Android cannot unload a DexClassLoader — but
     * no code path reaches them afterwards: MatrixE2eeRuntime checks module
     * availability before sending and MatrixE2eeUtils checks the sender
     * provider, both of which now report the plaintext stubs.
     *
     * Safe to call in any state; a failed or partial download is cleaned up
     * too. Callbacks fire on the main thread.
     */
    fun uninstall(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
    ) {
        val context = appContext ?: run {
            onFailure?.invoke("E2EE plugin loader not initialized")
            return
        }
        scope.launch {
            val failure = mutex.withLock {
                runCatching {
                    // residentPlugin is deliberately kept: the class loader and
                    // the native libraries it opened stay mapped for the life of
                    // the process, and a later install must re-activate through
                    // them (see activatePlugin).
                    MatrixE2eeSenderProvider.uninstall()
                    MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationManager)
                    var freed = deleteRecursively(pluginDir(context, expectedSdkVersion()))
                    freed += deleteRecursively(File(context.codeCacheDir, LIB_DIR))
                    freed += deleteRecursively(File(context.codeCacheDir, OPTIMIZED_DIR))
                    currentState = E2eeModuleStatus.NOT_INSTALLED
                    currentProgress = 0
                    lastError = null
                    SLog.i(TAG, "E2EE plugin uninstalled, freed $freed bytes")
                }.exceptionOrNull()
            }
            if (failure == null) {
                withContext(Dispatchers.Main) { onSuccess?.invoke() }
            } else {
                val message = failure.describeFailure()
                lastError = message
                SLog.e(TAG, "E2EE plugin uninstall failed: $message", failure)
                withContext(Dispatchers.Main) { onFailure?.invoke(message) }
            }
        }
    }

    private suspend fun loadCachedIfPossible(context: Context) {
        mutex.withLock {
            if (currentState == E2eeModuleStatus.AVAILABLE) return@withLock
            val expected = expectedSdkVersion()
            val apk = pluginDir(context, expected).resolve(PLUGIN_APK_NAME)
            if (!apk.isFile || apk.length() <= 0L) return@withLock
            val failure = runCatching {
                verifyManifest(context, apk)
                verifySignature(context, apk)
                loadPlugin(context, apk)
            }.exceptionOrNull()
            if (failure == null) {
                currentState = E2eeModuleStatus.AVAILABLE
                SLog.i(TAG, "Cached E2EE plugin loaded (sdk=$expected)")
            } else {
                // A stale/corrupt cache must not wedge startup; drop it so the
                // next requestInstall starts from a clean download.
                apk.delete()
                SLog.w(TAG, "Cached E2EE plugin unusable, deleted: ${failure.describeFailure()}", failure)
            }
        }
    }

    private fun downloadPlugin(context: Context, onProgress: (Int) -> Unit): File {
        val version = expectedSdkVersion()
        val dir = pluginDir(context, version)
        val apk = dir.resolve(PLUGIN_APK_NAME)
        if (apk.isFile && apk.length() > 0L) {
            onProgress(100)
            return apk
        }
        val abi = supportedAbi()
        val fileName = pluginFileName(version, abi)
        downloadFile("$BASE_URL/$version/$fileName", apk, onProgress)
        val checksumUrl = "$BASE_URL/$version/$fileName.sha256"
        val checksumFile = dir.resolve("$PLUGIN_APK_NAME.sha256")
        val downloaded = runCatching { downloadToFile(checksumUrl, checksumFile) }.getOrDefault(false)
        if (downloaded) {
            val expectedHash = checksumFile.readText().trim().substringBefore(' ').substringAfterLast(':')
            val actualHash = sha256(apk)
            if (!expectedHash.equals(actualHash, ignoreCase = true)) {
                apk.delete()
                error("plugin checksum mismatch")
            }
        } else {
            checksumFile.delete()
            SLog.w(TAG, "No .sha256 sidecar published for $fileName; relying on signature check")
        }
        return apk
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Int) -> Unit) {
        if (!downloadToFile(url, dest, onProgress)) error("download failed: $url")
        onProgress(100)
    }

    private fun downloadToFile(url: String, dest: File, onProgress: ((Int) -> Unit)? = null): Boolean {
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, "${dest.name}.part")
        val request = Request.Builder().url(url).build()
        return runCatching {
            httpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Plugin download HTTP ${response.code}: $url")
                    return@use false
                }
                val body = response.body ?: return@use false
                val total = body.contentLength()
                var downloaded = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    currentProgress = percent
                                    // The UI only follows this callback; without it
                                    // the bar sits at 0% until the final onProgress(100).
                                    // install()'s lambda already hops to the main
                                    // thread, so calling here from the IO thread is safe.
                                    onProgress?.invoke(percent)
                                }
                            }
                        }
                    }
                }
                if (!temp.renameTo(dest)) {
                    temp.copyTo(dest, overwrite = true)
                    temp.delete()
                }
                true
            }
        }.getOrElse { error ->
            SLog.e(TAG, "Plugin download error: ${error.message}", error)
            temp.delete()
            false
        }
    }

    private fun verifyManifest(context: Context, apk: File) {
        val json = ZipFile(apk).use { zip ->
            val entry = zip.getEntry(PLUGIN_MANIFEST_ASSET) ?: error("plugin manifest missing")
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val manifest = JSONObject(json)
        val sdkVersion = manifest.optString("sdkVersion")
        if (sdkVersion != expectedSdkVersion()) {
            error("plugin sdk $sdkVersion does not match expected ${expectedSdkVersion()}")
        }
        val contractVersion = manifest.optInt("contractVersion", 0)
        if (contractVersion != CONTRACT_VERSION) {
            error("plugin contract $contractVersion does not match host contract $CONTRACT_VERSION")
        }
        val minBaseVersionCode = manifest.optInt("minBaseVersionCode", 0)
        val hostVersionCode = hostVersionCode(context)
        if (minBaseVersionCode > hostVersionCode) {
            error("plugin requires base app versionCode >= $minBaseVersionCode, host is $hostVersionCode")
        }
    }

    @Suppress("DEPRECATION")
    private fun hostVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }

    private fun verifySignature(context: Context, apk: File) {
        val hostSignatures = hostSignatures(context)
        if (hostSignatures.isEmpty()) error("host signatures unavailable")
        @Suppress("DEPRECATION")
        val info: PackageInfo? = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNATURES,
        )
        val pluginSignatures = info?.signatures?.toList().orEmpty()
        if (pluginSignatures.isEmpty()) error("cannot read plugin signatures")
        val hostHashes = hostSignatures.map { it.toCharsString() }.sorted()
        val pluginHashes = pluginSignatures.map { it.toCharsString() }.sorted()
        if (hostHashes != pluginHashes) error("plugin signature does not match host app")
    }

    @Suppress("DEPRECATION")
    private fun hostSignatures(context: Context): List<android.content.pm.Signature> {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
    }

    /**
     * Make the plugin's sender/verification providers active. Reuses the
     * resident class loader when this process already loaded the plugin once
     * (the only case that can reach here is a re-install after an in-process
     * uninstall); otherwise loads the freshly verified APK.
     */
    private fun activatePlugin(context: Context, apk: File) {
        val resident = residentPlugin
        if (resident != null && resident.sdkVersion == expectedSdkVersion()) {
            reinstallThroughResident(resident, context)
            return
        }
        loadPlugin(context, apk)
    }

    /**
     * Re-run the plugin entry point through the resident class loader. The
     * plugin classes and their native libraries are still mapped in this
     * process, so this only puts the providers back; the re-downloaded APK on
     * disk is what the next cold start loads.
     */
    private fun reinstallThroughResident(resident: ResidentPlugin, context: Context) {
        val senderBefore = MatrixE2eeSenderProvider.getOrNull()
        val verificationBefore = MatrixE2eeVerificationProvider.getOrNull()
        runCatching {
            resident.installEntry.invoke(null, context.applicationContext)
        }.getOrElse { failure ->
            // The resident loader is wedged (its native libraries were opened
            // by this class loader and no other one can open them again); only
            // a process restart can produce a fresh loader.
            throw IllegalStateException(
                "E2EE plugin is no longer loadable in this process; " +
                    "restart the app before reinstalling",
                failure,
            )
        }
        if (MatrixE2eeSenderProvider.getOrNull() === senderBefore ||
            MatrixE2eeVerificationProvider.getOrNull() === verificationBefore
        ) {
            error("plugin entry did not install E2EE providers")
        }
        SLog.i(TAG, "E2EE plugin re-activated through the resident loader (sdk=${resident.sdkVersion})")
    }

    private fun loadPlugin(context: Context, apk: File) {
        val optimizedDir = File(context.codeCacheDir, OPTIMIZED_DIR).apply { mkdirs() }
        // ART refuses to open a writable file as dex ("Writable dex file ... is
        // not allowed"): a dex that can still be rewritten after signature
        // verification would defeat it. The APK lands 0600 from the download,
        // so flip it read-only here — this also covers the cached-load path.
        // Deleting it later still works: unlink needs a writable parent dir,
        // not a writable file.
        apk.setReadOnly()
        // JNA (pulled in by matrix-rust-sdk) resolves libjnidispatch.so and
        // libmatrix_sdk_ffi.so through System.loadLibrary, which only searches
        // the DexClassLoader's librarySearchPath. DexPathList drops a plain
        // file from that path silently — only "!/" zip paths and directories
        // become search elements — so the APK can never serve the libraries
        // and every System.loadLibrary fails. Unpack the .so files ourselves;
        // ART resolves a directory element flat, so the lib/<abi>/ prefix must
        // be stripped from the entry names.
        val libDir = extractNativeLibraries(context, apk)
        val loader = DexClassLoader(
            apk.absolutePath,
            optimizedDir.absolutePath,
            libDir.absolutePath,
            context.classLoader,
        )
        // The host pre-installs plaintext fallbacks at startup; a successful
        // plugin load must replace both provider instances, so compare against
        // the pre-load instances instead of a plain null check.
        val senderBefore = MatrixE2eeSenderProvider.getOrNull()
        val verificationBefore = MatrixE2eeVerificationProvider.getOrNull()
        val entryClass = loader.loadClass(PLUGIN_ENTRY_CLASS)
        val installEntry = entryClass.getMethod("install", Context::class.java)
        // Cache the resident loader before invoking the entry: if the entry
        // throws, this class loader and the native libraries it just opened are
        // still wedged into the process, so every later attempt must go through
        // this instance (and surface a restart hint) instead of building a
        // second DexClassLoader that ART would refuse.
        residentPlugin = ResidentPlugin(expectedSdkVersion(), loader, installEntry)
        installEntry.invoke(null, context.applicationContext)
        if (MatrixE2eeSenderProvider.getOrNull() === senderBefore ||
            MatrixE2eeVerificationProvider.getOrNull() === verificationBefore
        ) {
            error("plugin entry did not install E2EE providers")
        }
        SLog.i(TAG, "E2EE plugin loaded from ${apk.name}")
    }

    /**
     * Unpacks the plugin APK's native libraries (the .so files under
     * lib/<abi>/) into a private directory and returns it for use as the
     * DexClassLoader librarySearchPath. The APK is abi-specific and immutable
     * once verified, so the marker skips the work while the APK is unchanged;
     * a re-download changes its length/mtime.
     */
    private fun extractNativeLibraries(context: Context, apk: File): File {
        val abi = supportedAbi()
        val libDir = File(context.codeCacheDir, LIB_DIR).apply { mkdirs() }
        val marker = libDir.resolve(".extracted")
        val stamp = "${apk.length()}-${apk.lastModified()}-$abi"
        if (marker.isFile && marker.readText() == stamp) return libDir
        libDir.listFiles()?.forEach { it.delete() }
        val prefix = "lib/$abi/"
        var extracted = 0
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (!entry.name.startsWith(prefix) || !entry.name.endsWith(".so")) continue
                val out = libDir.resolve(entry.name.substringAfterLast('/'))
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                extracted++
            }
        }
        if (extracted == 0) error("plugin APK has no native libraries under $prefix")
        marker.writeText(stamp)
        SLog.i(TAG, "Extracted $extracted native libraries for $abi from ${apk.name}")
        return libDir
    }

    private fun expectedSdkVersion(): String = BuildConfig.MATRIX_PLUGIN_SDK_VERSION

    /**
     * The reflective PluginEntry.install call wraps whatever actually broke in an
     * InvocationTargetException whose own message is null, so walk the cause chain
     * to surface the real error (e.g. NoSuchMethodError on a member this app's R8
     * shrank out of a class that survived).
     */
    private fun Throwable.describeFailure(): String = buildString {
        var current: Throwable? = this@describeFailure
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (isNotEmpty()) append(" <- ")
            append(current.javaClass.name)
            current.message?.let { append(": ").append(it) }
            current = current.cause
            depth++
        }
        if (current != null) append(" <- ...")
    }

    /**
     * Plugin cache directory for one sdk version. Deliberately does not create
     * it, so [uninstall] can delete it without recreating it as a side effect;
     * the download path creates parents through `downloadToFile` anyway.
     */
    private fun pluginDir(context: Context, version: String): File =
        File(context.filesDir, "e2ee-plugin/$version")

    /** Recursively delete [file] and return the number of bytes released. */
    private fun deleteRecursively(file: File): Long {
        if (!file.exists()) return 0L
        var freed = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> freed += deleteRecursively(child) }
        }
        freed += file.length()
        if (!file.delete() && file.exists()) {
            SLog.w(TAG, "Failed to delete ${file.absolutePath}")
        }
        return freed
    }

    private fun supportedAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        return abis.firstOrNull { it in PUBLISHED_ABIS } ?: abis.firstOrNull() ?: "arm64-v8a"
    }

    private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "E2eePluginLoader"

        private const val MAX_CAUSE_DEPTH = 8

        /** Public GitLab project (xinyi-relay) hosting the generic package. */
        private const val PROJECT_ID = "84113188"
        private const val BASE_URL =
            "https://gitlab.com/api/v4/projects/$PROJECT_ID/packages/generic/xinyi-e2ee-plugin"

        private const val PLUGIN_APK_NAME = "plugin.apk"
        private const val PLUGIN_MANIFEST_ASSET = "assets/plugin-manifest.json"
        private const val PLUGIN_ENTRY_CLASS =
            "io.github.magisk317.relay.matrix.e2ee.plugin.PluginEntry"

        /** Extracted native libraries, used as the DexClassLoader librarySearchPath. */
        private const val LIB_DIR = "e2ee-plugin-lib"

        /** Optimized dex output directory handed to DexClassLoader. */
        private const val OPTIMIZED_DIR = "e2ee-plugin"

        /** Provider contract revision; bump on incompatible provider changes. */
        private const val CONTRACT_VERSION = 1

        /** ABIs the plugin publishes (mirrors the plugin module's splits). */
        private val PUBLISHED_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

        fun pluginFileName(version: String, abi: String): String =
            "xinyi-e2ee-plugin_v${version}_${abi}.apk"
    }
}

/**
 * The plugin's class loader and entry method, resident for the life of the
 * process once [E2eePluginLoader.loadPlugin] has run. Kept across an uninstall
 * because neither can be recreated in the same process.
 */
private class ResidentPlugin(
    val sdkVersion: String,
    val classLoader: DexClassLoader,
    val installEntry: Method,
)
