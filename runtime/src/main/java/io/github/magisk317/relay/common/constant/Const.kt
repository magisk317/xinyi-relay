package io.github.magisk317.relay.common.constant

import io.github.magisk317.relay.runtime.BuildConfig

/**
 * Constant about 3rd app
 */
object Const {

    /* Alipay begin */
    const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
    const val ALIPAY_QRCODE_URI_PREFIX = "alipayqr://platformapi/startapp?saId=10000007&qrcode="

    // 收款码 URL
    const val ALIPAY_QRCODE_URL = "HTTPS://QR.ALIPAY.COM/FKX074142EKXD0OIMV8B60"

    /* Alipay end */

    /* QQ begin */
    const val QQ_GROUP_URL = "https://qm.qq.com/q/4mMpX3vk4U"
    const val TELEGRAM_GROUP_URL = "https://t.me/+NR2QaQ4dlEgxYmNl"
    /* QQ end */

    /* Xposed SmsCode begin */
    const val HOME_ACTIVITY_ALIAS = BuildConfig.APPLICATION_ID + ".HomeActivityAlias"
    const val EXTRA_ACTION = "extra_action"

    const val PROJECT_SOURCE_CODE_URL = "https://github.com/magisk317/xinyi-relay"
    const val PROJECT_GITHUB_LATEST_RELEASE_URL = PROJECT_SOURCE_CODE_URL + "/releases/latest"
    const val PROJECT_DOC_BASE_URL = "https://magisk317.github.io/SmsCode"
    const val PRIVACY_POLICY_URL = "https://github.com/magisk317/xinyi-relay/blob/main/docs/PRIVACY.md"
    const val DOC_SMS_CODE_RULE_HELP = "sms_code_rule_help"
    /* Xposed SmsCode end */

    const val LSPOSED_MANAGER_PACKAGE_NAME = "org.lsposed.manager"
    const val LSPOSED_RELEASE_URL = "https://github.com/LSPosed/LSPosed/releases/latest"
    const val XPOSED_SMSCODE_PACKAGE_NAME = "com.github.tianma8023.xposed.smscode"

    /* Rule Edit Types */
    const val EDIT_TYPE_CREATE = 0
    const val EDIT_TYPE_EDIT = 1
    const val KEY_RULE_EDIT_TYPE = "key_rule_edit_type"
    const val KEY_CODE_RULE = "key_code_rule"
    const val KEY_RULE_ID = "key_rule_id"
    const val EXTRA_IMPORT_URI = "extra_import_uri"

    /* Wechat */
    const val WECHAT_PACKAGE_NAME = "com.tencent.mm"

    /* UI Dimensions (dp) */
    const val PADDING_SMALL = 8
    const val PADDING_MEDIUM = 16
    const val PADDING_LARGE = 24
    const val BOTTOM_SPACE_HEIGHT = 80

    /* UI Measurements */
    const val TOP_BAR_HEIGHT = 64
    const val SPACING_EXTRA_SMALL = 4
    const val SPACING_SMALL = 8
    const val SPACING_MEDIUM = 16
    const val FLOW_STOP_TIMEOUT_MS = 5000L
}
