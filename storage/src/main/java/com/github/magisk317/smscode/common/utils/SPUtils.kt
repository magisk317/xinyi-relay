package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst

object SPUtils {

    // 本地的版本号
    private const val LOCAL_VERSION_CODE = "local_version_code"
    private const val LOCAL_VERSION_CODE_DEFAULT = 16

    /**
     * 获取本地记录的版本号
     */
    suspend fun getLocalVersionCode(context: Context): Int {
        // 如果不存在,则默认返回16,即v1.4.5版本
        return AppPreferencesDataStore.getInt(context, LOCAL_VERSION_CODE, LOCAL_VERSION_CODE_DEFAULT)
    }

    /**
     * 设置当前版本号
     */
    suspend fun setLocalVersionCode(context: Context, versionCode: Int) {
        AppPreferencesDataStore.setInt(context, LOCAL_VERSION_CODE, versionCode)
    }

    /**
     * 获取短信验证码关键字
     */
    suspend fun getSMSCodeKeywords(context: Context): String? = AppPreferencesDataStore.getString(
        context,
        PrefConst.KEY_SMSCODE_KEYWORDS,
        PrefConst.SMSCODE_KEYWORDS_DEFAULT,
    )

    /**
     * 是否同意隐私协议
     */
    suspend fun isPrivacyPolicyAccepted(context: Context): Boolean =
        AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, false)

    /**
     * 设置是否同意隐私协议
     */
    suspend fun setPrivacyPolicyAccepted(context: Context, accepted: Boolean) {
        AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, accepted)
    }

    /**
     * 获取当前主题模式
     * 0: Follow System, 1: Light, 2: Dark
     */
    suspend fun getThemeMode(context: Context): Int =
        AppPreferencesDataStore.getInt(context, PrefConst.KEY_CHOOSE_THEME, 0)

    /**
     * 设置当前主题模式
     */
    suspend fun setThemeMode(context: Context, mode: Int) {
        AppPreferencesDataStore.setInt(context, PrefConst.KEY_CHOOSE_THEME, mode)
    }
}
