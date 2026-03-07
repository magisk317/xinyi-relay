package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.core.content.pm.PackageInfoCompat
import com.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.xinyi.relay.core.R
import io.github.magisk317.xinyi.relay.storage.BuildConfig

/**
 * 包相关工具类
 */
object PackageUtils {

    private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"

    enum class UpdateDestination {
        PLAY,
        GITHUB,
    }

    /**
     * not installed
     */
    private const val PACKAGE_NOT_INSTALLED = 0

    /**
     * installed & disabled
     */
    private const val PACKAGE_DISABLED = 1

    /**
     * installed & enabled
     */
    private const val PACKAGE_ENABLED = 2

    @IntDef(PACKAGE_NOT_INSTALLED, PACKAGE_DISABLED, PACKAGE_ENABLED)
    @Retention(AnnotationRetention.SOURCE)
    annotation class PackageState

    private fun checkPackageState(context: Context, packageName: String): Int =
        if (isPackageEnabled(context, packageName)) {
            PACKAGE_ENABLED
        } else {
            if (isPackageInstalled(context, packageName)) {
                PACKAGE_DISABLED
            } else {
                PACKAGE_NOT_INSTALLED
            }
        }

    /**
     * 指定的包名对应的App是否已安装
     */
    @JvmStatic
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            getPackageInfoCompat(pm, packageName)
            true
        } catch (ignored: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 对应包名的应用是否已启用
     */
    @JvmStatic
    fun isPackageEnabled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            val appInfo = getApplicationInfoCompat(pm, packageName)
            appInfo.enabled
        } catch (ignored: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getPackageVersion(context: Context, packageName: String): Pair<String, Long>? {
        val pm = context.packageManager
        return try {
            val packageInfo = getPackageInfoCompat(pm, packageName)
            val versionName = packageInfo.versionName ?: ""
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            versionName to versionCode
        } catch (ignored: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getPackageInfoCompat(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }

    private fun getApplicationInfoCompat(pm: PackageManager, packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }

    @JvmStatic
    fun getLsposedModuleVersion(): String? {
        val propPath = "/data/adb/modules/zygisk_lsposed/module.prop"
        val output = runSuCommand("cat $propPath") ?: return null
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val rawVersion = lines.firstOrNull { it.startsWith("version=") }
            ?.substringAfter("version=")
            ?.trim()
        val versionCode = lines.firstOrNull { it.startsWith("versionCode=") }
            ?.substringAfter("versionCode=")
            ?.trim()
        val normalized = rawVersion?.removePrefix("v")?.trim()
        return when {
            !normalized.isNullOrBlank() && !versionCode.isNullOrBlank() && !normalized.contains("(") ->
                "$normalized ($versionCode)"

            !normalized.isNullOrBlank() -> normalized

            !versionCode.isNullOrBlank() -> versionCode

            else -> null
        }
    }

    @JvmStatic
    fun getLsposedModuleInfo(): Pair<String, String>? {
        val propPath = "/data/adb/modules/zygisk_lsposed/module.prop"
        val output = runSuCommand("cat $propPath") ?: return null
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val name = lines.firstOrNull { it.startsWith("name=") }
            ?.substringAfter("name=")
            ?.trim()
        val rawVersion = lines.firstOrNull { it.startsWith("version=") }
            ?.substringAfter("version=")
            ?.trim()
        val versionCode = lines.firstOrNull { it.startsWith("versionCode=") }
            ?.substringAfter("versionCode=")
            ?.trim()
        val normalized = rawVersion?.removePrefix("v")?.trim()
        val version = when {
            !normalized.isNullOrBlank() && !versionCode.isNullOrBlank() && !normalized.contains("(") ->
                "$normalized ($versionCode)"

            !normalized.isNullOrBlank() -> normalized

            !versionCode.isNullOrBlank() -> versionCode

            else -> null
        }
        return if (!name.isNullOrBlank() && !version.isNullOrBlank()) name to version else null
    }

    @JvmStatic
    fun hasRootAccess(): Boolean {
        val uid = runSuCommand("id -u")?.trim()
        return uid == "0"
    }

    private fun runSuCommand(command: String): String? = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotBlank()) output else null
    } catch (ignored: Exception) {
        null
    }

    private fun checkAlipayExists(context: Context): Boolean {
        val packageState = checkPackageState(context, Const.ALIPAY_PACKAGE_NAME)
        return when (packageState) {
            PACKAGE_ENABLED -> true

            PACKAGE_DISABLED -> {
                Toast.makeText(context, R.string.alipay_enable_prompt, Toast.LENGTH_SHORT).show()
                false
            }

            PACKAGE_NOT_INSTALLED -> {
                Toast.makeText(context, R.string.alipay_install_prompt, Toast.LENGTH_SHORT).show()
                false
            }

            else -> false
        }
    }

    /**
     * 打开支付宝
     */
    @JvmStatic
    fun startAlipayActivity(context: Context) {
        if (checkAlipayExists(context)) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(Const.ALIPAY_PACKAGE_NAME)
            context.startActivity(intent)
        }
    }

    /**
     * 打开支付宝捐赠页
     */
    @JvmStatic
    fun startAlipayDonatePage(context: Context) {
        if (checkAlipayExists(context)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(Const.ALIPAY_QRCODE_URI_PREFIX + Const.ALIPAY_QRCODE_URL)
            context.startActivity(intent)
        }
    }

    /**
     * Join QQ group
     */
    @JvmStatic
    fun joinQQGroup(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Const.QQ_GROUP_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (ignored: Exception) {
            Toast.makeText(context, R.string.prompt_join_qq_group_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun isInstalledFromPlay(context: Context): Boolean = try {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(BuildConfig.APPLICATION_ID).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(BuildConfig.APPLICATION_ID)
        }
        installer == PLAY_STORE_PACKAGE_NAME
    } catch (ignored: Exception) {
        false
    }

    @JvmStatic
    fun showAppDetailsInPlayStore(context: Context) {
        val packageName = BuildConfig.APPLICATION_ID
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            setPackage(PLAY_STORE_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(marketIntent)
        } catch (ignored: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(webIntent)
        }
    }

    @JvmStatic
    fun isPlayStoreAvailable(context: Context): Boolean =
        isPackageEnabled(context, PLAY_STORE_PACKAGE_NAME)

    @JvmStatic
    fun openPlayStoreOrGithub(context: Context) {
        when (resolveUpdateDestination(isPlayStoreAvailable(context))) {
            UpdateDestination.PLAY -> showAppDetailsInPlayStore(context)
            UpdateDestination.GITHUB -> Utils.showWebPage(context, Const.PROJECT_GITHUB_LATEST_RELEASE_URL)
        }
    }

    fun resolveUpdateDestination(playStoreAvailable: Boolean): UpdateDestination =
        if (playStoreAvailable) UpdateDestination.PLAY else UpdateDestination.GITHUB

    @JvmStatic
    fun isOnWifi(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (_: SecurityException) {
            false
        }
    }

    private fun checkWechatExists(context: Context): Boolean {
        val packageState = checkPackageState(context, Const.WECHAT_PACKAGE_NAME)
        return when (packageState) {
            PACKAGE_ENABLED -> true

            PACKAGE_DISABLED -> {
                Toast.makeText(context, R.string.wechat_enable_prompt, Toast.LENGTH_SHORT).show()
                false
            }

            PACKAGE_NOT_INSTALLED -> {
                Toast.makeText(context, R.string.wechat_install_prompt, Toast.LENGTH_SHORT).show()
                false
            }

            else -> false
        }
    }

    /**
     * 打开微信
     */
    @JvmStatic
    fun startWechatActivity(context: Context) {
        if (checkWechatExists(context)) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(Const.WECHAT_PACKAGE_NAME)
            context.startActivity(intent)
        }
    }

    @JvmStatic
    fun copyAlipayPocketToken(context: Context) {
        Utils.copyToClipboard(context, Const.ALIPAY_POCKET_TOKEN)
        val text = context.getString(R.string.alipay_red_packet_code_copied, Const.ALIPAY_POCKET_TOKEN)
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
