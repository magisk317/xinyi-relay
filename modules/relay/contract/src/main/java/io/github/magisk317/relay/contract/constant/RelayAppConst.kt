package io.github.magisk317.relay.contract.constant

object RelayAppConst {
    const val APPLICATION_ID = "io.github.magisk317.xinyi.relay"

    const val TELEGRAM_GROUP_URL = "https://t.me/+NR2QaQ4dlEgxYmNl"

    const val QQ_CHANNEL_URL = "https://pd.qq.com/s/bmleyy1mj"

    const val HOME_ACTIVITY_ALIAS = "$APPLICATION_ID.HomeActivityAlias"
    const val EXTRA_ACTION = "extra_action"
    const val REQUEST_CODE_STANDARD_PERMISSIONS = 1001

    const val PROJECT_SOURCE_CODE_URL = "https://gitlab.com/magisk3171/xinyi-relay"
    const val PROJECT_GITHUB_LATEST_RELEASE_URL =
        "https://github.com/magisk317/xinyi-relay/releases/latest"
    const val PROJECT_DOC_BASE_URL = "https://magisk317.github.io/SmsCode"
    const val PRIVACY_POLICY_URL =
        "https://gitlab.com/magisk3171/xinyi-relay/-/blob/beta/docs/PRIVACY.md"
    const val DOC_SMS_CODE_RULE_HELP = "sms_code_rule_help"

    const val LSPOSED_MANAGER_PACKAGE_NAME = "org.lsposed.manager"
    const val LSPOSED_RELEASE_URL = "https://github.com/LSPosed/LSPosed/releases/latest"
    const val XPOSED_SMSCODE_PACKAGE_NAME = "com.github.tianma8023.xposed.smscode"
    const val TARGET_RELAY_PACKAGE = XPOSED_SMSCODE_PACKAGE_NAME

    const val EDIT_TYPE_CREATE = 0
    const val EDIT_TYPE_EDIT = 1
    const val KEY_RULE_EDIT_TYPE = "key_rule_edit_type"
    const val KEY_CODE_RULE = "key_code_rule"
    const val KEY_RULE_ID = "key_rule_id"
    const val EXTRA_IMPORT_URI = "extra_import_uri"

    const val PADDING_SMALL = 8
    const val PADDING_MEDIUM = 16
    const val PADDING_LARGE = 24
    const val BOTTOM_SPACE_HEIGHT = 80

    const val TOP_BAR_HEIGHT = 64
    const val SPACING_EXTRA_SMALL = 4
    const val SPACING_SMALL = 8
    const val SPACING_MEDIUM = 16
    const val FLOW_STOP_TIMEOUT_MS = 5000L
}
