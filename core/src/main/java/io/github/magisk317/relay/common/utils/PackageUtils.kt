package io.github.magisk317.relay.common.utils

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.packageenv.PackageEnvCore
import io.github.magisk317.smscode.runtime.common.packageenv.PackageState as SharedPackageState
import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.smscode.runtime.common.utils.FrameworkInfo

/**
 * 包相关工具类
 */
object PackageUtils {

    private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"
    private const val HOOKER_ANNOTATION_ERROR_TEXT = "Hooker should be annotated with @XposedHooker"

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
        when (PackageEnvCore.getPackageState(context, packageName)) {
            SharedPackageState.ENABLED -> PACKAGE_ENABLED
            SharedPackageState.DISABLED -> PACKAGE_DISABLED
            SharedPackageState.NOT_INSTALLED -> PACKAGE_NOT_INSTALLED
        }

    /**
     * 指定的包名对应的App是否已安装
     */
    @JvmStatic
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return PackageEnvCore.isPackageInstalled(context, packageName)
    }

    /**
     * 对应包名的应用是否已启用
     */
    @JvmStatic
    fun isPackageEnabled(context: Context, packageName: String): Boolean {
        return PackageEnvCore.isPackageEnabled(context, packageName)
    }

    @JvmStatic
    fun getPackageVersion(context: Context, packageName: String): Pair<String, Long>? {
        val info = PackageEnvCore.getPackageVersion(context, packageName) ?: return null
        return info.versionName to info.versionCode
    }

    @JvmStatic
    fun resolveFrameworkInfo(context: Context): FrameworkInfo? {
        return PackageEnvCore.resolveFrameworkInfo(context)
    }

    @JvmStatic
    fun inspectFrameworkIssue(context: Context): FrameworkCompatibilityMonitor.FrameworkIssue? {
        return PackageEnvCore.inspectFrameworkIssue(context)
    }

    @JvmStatic
    fun getLsposedModuleVersion(context: Context): String? {
        return resolveFrameworkInfo(context)?.displayVersion
    }

    @JvmStatic
    fun getLsposedModuleInfo(context: Context): Pair<String, String>? {
        val info = resolveFrameworkInfo(context) ?: return null
        return info.displayName to info.displayVersion
    }

    @JvmStatic
    fun hasRootAccess(): Boolean {
        return PackageEnvCore.hasRootAccess()
    }

    private fun checkAlipayStateMessage(context: Context): String? {
        val packageState = checkPackageState(context, Const.ALIPAY_PACKAGE_NAME)
        return when (packageState) {
            PACKAGE_ENABLED -> null
            PACKAGE_DISABLED -> context.getString(R.string.alipay_enable_prompt)
            PACKAGE_NOT_INSTALLED -> context.getString(R.string.alipay_install_prompt)
            else -> context.getString(R.string.alipay_install_prompt)
        }
    }

    /**
     * 打开支付宝
     */
    @JvmStatic
    fun startAlipayActivity(context: Context): String? {
        val message = checkAlipayStateMessage(context)
        if (message != null) return message
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(Const.ALIPAY_PACKAGE_NAME)
        context.startActivity(intent)
        return null
    }

    /**
     * 打开支付宝捐赠页
     */
    @JvmStatic
    fun startAlipayDonatePage(context: Context): String? {
        val message = checkAlipayStateMessage(context)
        if (message != null) return message
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(Const.ALIPAY_QRCODE_URI_PREFIX + Const.ALIPAY_QRCODE_URL)
        context.startActivity(intent)
        return null
    }

    /**
     * Join QQ group
     */
    @JvmStatic
    fun joinQQGroup(context: Context): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Const.QQ_GROUP_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return null
        } catch (ignored: Exception) {
            return context.getString(R.string.prompt_join_qq_group_failed)
        }
    }

    @JvmStatic
    fun isInstalledFromPlay(context: Context): Boolean = try {
        PackageEnvCore.isInstalledFromPlay(context)
    } catch (ignored: Exception) { false }

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
    fun openPlayStoreOrGithub(context: Context): String? {
        return when (resolveUpdateDestination(isPlayStoreAvailable(context))) {
            UpdateDestination.PLAY -> {
                showAppDetailsInPlayStore(context)
                null
            }
            UpdateDestination.GITHUB -> Utils.showWebPage(context, Const.PROJECT_GITHUB_LATEST_RELEASE_URL)
        }
    }

    fun resolveUpdateDestination(playStoreAvailable: Boolean): UpdateDestination =
        if (playStoreAvailable) UpdateDestination.PLAY else UpdateDestination.GITHUB

    @JvmStatic
    @SuppressLint("MissingPermission")
    fun isOnWifi(context: Context): Boolean {
        return try {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            PackageEnvCore.isOnWifi(context)
        } catch (_: SecurityException) {
            false
        }
    }

    private fun checkWechatStateMessage(context: Context): String? {
        val packageState = checkPackageState(context, Const.WECHAT_PACKAGE_NAME)
        return when (packageState) {
            PACKAGE_ENABLED -> null
            PACKAGE_DISABLED -> context.getString(R.string.wechat_enable_prompt)
            PACKAGE_NOT_INSTALLED -> context.getString(R.string.wechat_install_prompt)
            else -> context.getString(R.string.wechat_install_prompt)
        }
    }

    /**
     * 打开微信
     */
    @JvmStatic
    fun startWechatActivity(context: Context): String? {
        val message = checkWechatStateMessage(context)
        if (message != null) return message
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(Const.WECHAT_PACKAGE_NAME)
        context.startActivity(intent)
        return null
    }

    @JvmStatic
    fun copyAlipayPocketToken(context: Context): String {
        Utils.copyToClipboard(context, Const.ALIPAY_POCKET_TOKEN)
        val text = context.getString(R.string.alipay_red_packet_code_copied, Const.ALIPAY_POCKET_TOKEN)
        return text
    }
}
