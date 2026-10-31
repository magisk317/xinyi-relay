package io.github.magisk317.relay.ui.record

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.R as UiKitR

/** Scope of a record export: only the visible tab or every tab at once. */
internal enum class RecordExportScope {
    CURRENT_TAB,
    ALL_TABS,
}

internal val FORWARD_SUCCESS_COLOR = Color(AndroidColor.parseColor("#2E7D32"))
internal val FORWARD_FAILED_COLOR = Color(AndroidColor.parseColor("#C62828"))
internal val FORWARD_WARNING_COLOR = Color(AndroidColor.parseColor("#B26A00"))
internal val RECORD_TAB_ITEM_HEIGHT = 60.dp
internal const val RECORD_DATE_FORMAT = "yyyy.MM.dd HH:mm:ss"
internal val RECORD_ICON_SIZE = 40.dp
internal val APP_NOTIFY_ICON_SIZE = 40.dp

internal fun recordEnableTitleRes(tab: Int): Int = when (tab) {
    0 -> UiKitR.string.pref_enable_code_records_title
    1 -> R.string.pref_enable_plain_sms_records_title
    2 -> R.string.pref_enable_app_notify_records_title
    else -> R.string.pref_enable_call_notify_records_title
}

internal fun recordHistoryLimitKey(tab: Int): String = when (tab) {
    0 -> PrefConst.KEY_HISTORY_LIMIT_CODE
    1 -> PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS
    2 -> PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY
    else -> PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY
}

internal fun recordTabNameRes(tab: Int): Int = when (tab) {
    0 -> R.string.record_settings_target_code
    1 -> R.string.record_settings_target_plain
    2 -> R.string.record_settings_target_app_notify
    else -> R.string.record_settings_target_call_notify
}

internal fun recordColumnTitleRes(tab: Int): Int = when (tab) {
    0 -> R.string.records_column_code_title
    1 -> R.string.records_column_plain_title
    2 -> R.string.records_column_app_notify_title
    else -> R.string.records_column_call_notify_title
}

internal fun recordColumnShortTitleRes(tab: Int): Int = when (tab) {
    0 -> R.string.records_column_code_short_title
    1 -> R.string.records_column_plain_short_title
    2 -> R.string.records_column_app_notify_short_title
    else -> R.string.records_column_call_notify_short_title
}
