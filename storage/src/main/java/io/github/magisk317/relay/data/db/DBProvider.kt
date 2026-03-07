package io.github.magisk317.relay.data.db

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import io.github.magisk317.relay.storage.BuildConfig
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.data.db.entity.SmsMsg

class DBProvider : ContentProvider() {
    private var mDbManager: DBManager? = null

    override fun onCreate(): Boolean {
        context?.let {
            mDbManager = DBManager.get(it)
        }
        return true
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val uriType = sUriMatcher.match(uri)
        val id: Long
        val path: String
        when (uriType) {
            SMS_MSG_DIR -> {
                val msg = SmsMsg(
                    sender = values?.getAsString("sender"),
                    body = values?.getAsString("body"),
                    date = values?.getAsLong("date") ?: 0L,
                    company = values?.getAsString("company"),
                    smsCode = values?.getAsString("sms_code"),
                    packageName = values?.getAsString("package_name"),
                    notifyChannelId = values?.getAsString("notify_channel_id").orEmpty(),
                    msgType = values?.getAsInteger("msg_type") ?: SmsMsg.MSG_TYPE_SMS,
                    callType = values?.getAsInteger("call_type") ?: 0,
                    forwardStatus = values?.getAsInteger("forward_status") ?: SmsMsg.FORWARD_STATUS_NONE,
                    forwardTarget = values?.getAsString("forward_target"),
                    forwardMessage = values?.getAsString("forward_message"),
                    forwardTime = values?.getAsLong("forward_time") ?: 0L,
                )
                id = mDbManager!!.addSmsMsg(msg)
                path = "$PATH_SMS_MSG/$id"
            }

            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        context?.contentResolver?.notifyChange(uri, null)
        return Uri.parse(path)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val uriType = sUriMatcher.match(uri)
        return when (uriType) {
            SMS_CODE_RULE_DIR -> querySmsCodeRules(projection)
            SMS_CODE_RULE_ID -> querySmsCodeRuleById(projection, uri)
            SMS_MSG_DIR -> querySmsMsgs(projection, sortOrder)
            SMS_MSG_ID -> querySmsMsgById(projection, uri)
            APP_INFO_DIR -> queryAppInfo(projection, selection, selectionArgs)
            APP_INFO_ITEM -> queryAppInfoByPackageName(projection, uri)
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val uriType = sUriMatcher.match(uri)
        val rowsDeleted: Int = when (uriType) {
            SMS_MSG_DIR -> deleteSmsMsg(selection, selectionArgs)
            SMS_MSG_ID -> uri.lastPathSegment?.toLongOrNull()?.let { mDbManager!!.removeSmsMsgById(it) } ?: 0
            APP_INFO_ITEM -> deleteAppInfoByPackageName(uri)
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        if (rowsDeleted > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsDeleted
    }

    private fun deleteSmsMsg(selection: String?, selectionArgs: Array<String>?): Int {
        if (selection == null || selectionArgs.isNullOrEmpty()) {
            return 0
        }
        val normalized = selection.replace("`", "").trim().lowercase()
        if (normalized == "_id = ?" || normalized == "id = ?") {
            val id = selectionArgs.firstOrNull()?.toLongOrNull() ?: return 0
            return mDbManager!!.removeSmsMsgById(id)
        }
        throw IllegalArgumentException("Unsupported delete selection: $selection")
    }

    private fun querySmsCodeRules(projection: Array<String>?): Cursor {
        val rules = mDbManager!!.queryAllSmsCodeRules()
        val columns = projection ?: arrayOf("company", "code_keyword", "code_regex", "_id")
        val cursor = MatrixCursor(columns)
        rules.forEach { rule ->
            cursor.addRow(buildRow(columns) { column -> valueFromSmsCodeRule(rule, column) })
        }
        return cursor
    }

    private fun querySmsMsgs(projection: Array<String>?, sortOrder: String?): Cursor {
        val rows = mDbManager!!.queryAllSmsMsg().let { list ->
            when (sortOrder?.trim()?.lowercase()) {
                "date asc" -> list.sortedBy { it.date }
                "date desc", null, "" -> list.sortedByDescending { it.date }
                else -> list
            }
        }
        val columns = projection ?: arrayOf(
            "_id",
            "sender",
            "body",
            "date",
            "company",
            "sms_code",
            "package_name",
            "notify_channel_id",
            "msg_type",
            "call_type",
            "forward_status",
            "forward_target",
            "forward_message",
            "forward_time",
        )
        val cursor = MatrixCursor(columns)
        rows.forEach { msg ->
            cursor.addRow(buildRow(columns) { column -> valueFromSmsMsg(msg, column) })
        }
        return cursor
    }

    private fun querySmsMsgById(projection: Array<String>?, uri: Uri): Cursor {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IllegalArgumentException("Invalid URI: $uri")
        val columns = projection ?: arrayOf(
            "_id",
            "sender",
            "body",
            "date",
            "company",
            "sms_code",
            "package_name",
            "notify_channel_id",
            "msg_type",
            "call_type",
            "forward_status",
            "forward_target",
            "forward_message",
            "forward_time",
        )
        val cursor = MatrixCursor(columns)
        val msg = mDbManager!!.querySmsMsgById(id)
        if (msg != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromSmsMsg(msg, column) })
        }
        return cursor
    }

    private fun querySmsCodeRuleById(projection: Array<String>?, uri: Uri): Cursor {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IllegalArgumentException("Invalid URI: $uri")
        val columns = projection ?: arrayOf("company", "code_keyword", "code_regex", "_id")
        val cursor = MatrixCursor(columns)
        val rule = mDbManager!!.querySmsCodeRuleById(id)
        if (rule != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromSmsCodeRule(rule, column) })
        }
        return cursor
    }

    private fun queryAppInfo(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Cursor {
        var rows = mDbManager!!.queryAllAppInfos()
        if (!selection.isNullOrBlank()) {
            val normalized = selection.replace("`", "").trim().lowercase()
            if (normalized == "blocked = ?" && !selectionArgs.isNullOrEmpty()) {
                val blocked = selectionArgs[0] == "1" || selectionArgs[0].equals("true", ignoreCase = true)
                rows = rows.filter { it.blocked == blocked }
            } else if (normalized == "forwarding = ?" && !selectionArgs.isNullOrEmpty()) {
                val forwarding = selectionArgs[0] == "1" || selectionArgs[0].equals("true", ignoreCase = true)
                rows = rows.filter { it.forwarding == forwarding }
            }
        }
        val columns = projection ?: arrayOf("package_name", "label", "blocked", "forwarding", "notify_template")
        val cursor = MatrixCursor(columns)
        rows.forEach { app: AppInfo ->
            cursor.addRow(buildRow(columns) { column -> valueFromAppInfo(app, column) })
        }
        return cursor
    }

    private fun queryAppInfoByPackageName(projection: Array<String>?, uri: Uri): Cursor {
        val packageName = uri.lastPathSegment.orEmpty()
        val columns = projection ?: arrayOf("package_name", "label", "blocked", "forwarding", "notify_template")
        val cursor = MatrixCursor(columns)
        if (packageName.isBlank()) {
            return cursor
        }
        val app = mDbManager!!.queryAppInfoByPackageName(packageName)
        if (app != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromAppInfo(app, column) })
        }
        return cursor
    }

    private fun buildRow(columns: Array<String>, resolver: (String) -> Any?): Array<Any?> =
        Array(columns.size) { idx -> resolver(columns[idx]) }

    private fun valueFromSmsCodeRule(rule: SmsCodeRule, column: String): Any? =
        when (column) {
            "_id", "id" -> rule.id
            "company" -> rule.company
            "code_keyword" -> rule.codeKeyword
            "code_regex" -> rule.codeRegex
            else -> null
        }

    private fun valueFromSmsMsg(msg: SmsMsg, column: String): Any? =
        when (column) {
            "_id", "id" -> msg.id
            "sender" -> msg.sender
            "body" -> msg.body
            "date" -> msg.date
            "company" -> msg.company
            "sms_code" -> msg.smsCode
            "package_name" -> msg.packageName
            "notify_channel_id" -> msg.notifyChannelId
            "msg_type" -> msg.msgType
            "call_type" -> msg.callType
            "forward_status" -> msg.forwardStatus
            "forward_target" -> msg.forwardTarget
            "forward_message" -> msg.forwardMessage
            "forward_time" -> msg.forwardTime
            else -> null
        }

    private fun valueFromAppInfo(app: AppInfo, column: String): Any? =
        when (column) {
            "package_name" -> app.packageName
            "label" -> app.label
            "blocked" -> if (app.blocked) 1 else 0
            "forwarding" -> if (app.forwarding) 1 else 0
            "notify_template" -> app.notifyTemplate
            else -> null
        }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val uriType = sUriMatcher.match(uri)
        val rowsUpdated = when (uriType) {
            SMS_MSG_DIR -> updateSmsMsg(values, selection, selectionArgs)
            SMS_MSG_ID -> updateSmsMsgByUriId(uri, values)
            APP_INFO_DIR -> updateAppInfo(values, selection, selectionArgs)
            APP_INFO_ITEM -> updateAppInfoByUri(uri, values)
            else -> 0
        }
        if (rowsUpdated > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsUpdated
    }

    private fun updateSmsMsgByUriId(uri: Uri, values: ContentValues?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        return updateSmsMsgById(id, values)
    }

    private fun updateSmsMsg(values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (selection.isNullOrBlank() || selectionArgs.isNullOrEmpty()) {
            return 0
        }
        val normalized = selection.replace("`", "").trim().lowercase()
        if (normalized == "_id = ?" || normalized == "id = ?") {
            val id = selectionArgs.firstOrNull()?.toLongOrNull() ?: return 0
            return updateSmsMsgById(id, values)
        }
        return 0
    }

    private fun updateSmsMsgById(id: Long, values: ContentValues?): Int {
        val existing = mDbManager!!.querySmsMsgById(id) ?: return 0
        val updated = existing.copy(
            sender = values?.getAsString("sender") ?: existing.sender,
            body = values?.getAsString("body") ?: existing.body,
            date = values?.getAsLong("date") ?: existing.date,
            company = values?.getAsString("company") ?: existing.company,
            smsCode = values?.getAsString("sms_code") ?: existing.smsCode,
            packageName = values?.getAsString("package_name") ?: existing.packageName,
            notifyChannelId = values?.getAsString("notify_channel_id") ?: existing.notifyChannelId,
            msgType = values?.getAsInteger("msg_type") ?: existing.msgType,
            callType = values?.getAsInteger("call_type") ?: existing.callType,
            forwardStatus = values?.getAsInteger("forward_status") ?: existing.forwardStatus,
            forwardTarget = values?.getAsString("forward_target") ?: existing.forwardTarget,
            forwardMessage = values?.getAsString("forward_message") ?: existing.forwardMessage,
            forwardTime = values?.getAsLong("forward_time") ?: existing.forwardTime,
        )
        return mDbManager!!.updateSmsMsg(updated)
    }

    private fun updateAppInfo(values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (selection.isNullOrBlank() || selectionArgs.isNullOrEmpty()) {
            return 0
        }
        val normalized = selection.replace("`", "").trim().lowercase()
        if (normalized != "package_name = ?") {
            return 0
        }
        val packageName = selectionArgs.firstOrNull().orEmpty()
        if (packageName.isBlank()) {
            return 0
        }
        return updateAppInfoByPackageName(packageName, values)
    }

    private fun updateAppInfoByUri(uri: Uri, values: ContentValues?): Int {
        val packageName = uri.lastPathSegment.orEmpty()
        if (packageName.isBlank()) {
            return 0
        }
        return updateAppInfoByPackageName(packageName, values)
    }

    private fun updateAppInfoByPackageName(packageName: String, values: ContentValues?): Int {
        val existing = mDbManager!!.queryAppInfoByPackageName(packageName) ?: AppInfo(packageName = packageName)
        val blocked = parseBooleanValue(values, "blocked", existing.blocked)
        val forwarding = parseBooleanValue(values, "forwarding", existing.forwarding)
        val label = when {
            values?.containsKey("label") == true -> values.getAsString("label")
            else -> existing.label
        }
        val notifyTemplate = when {
            values?.containsKey("notify_template") == true -> values.getAsString("notify_template").orEmpty()
            else -> existing.notifyTemplate
        }
        return mDbManager!!.upsertAppInfo(
            existing.copy(
                label = label,
                blocked = blocked,
                forwarding = forwarding,
                notifyTemplate = notifyTemplate,
            ),
        )
    }

    private fun parseBooleanValue(values: ContentValues?, key: String, defaultValue: Boolean): Boolean {
        if (values?.containsKey(key) != true) return defaultValue
        val raw = values.get(key)
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw == "1" || raw.equals("true", ignoreCase = true)
            else -> defaultValue
        }
    }

    private fun deleteAppInfoByPackageName(uri: Uri): Int {
        val packageName = uri.lastPathSegment.orEmpty()
        if (packageName.isBlank()) {
            return 0
        }
        val app = mDbManager!!.queryAppInfoByPackageName(packageName) ?: return 0
        return mDbManager!!.removeAppInfosByPackage(listOf(app.packageName))
    }

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".db.provider"
        private const val PATH_SMS_MSG = "sms_msg"
        private const val PATH_SMS_CODE_RULE = "sms_code_rule"
        private const val PATH_APP_INFO = "app_info"

        @JvmField
        val SMS_MSG_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SMS_MSG")

        @JvmField
        val SMS_CODE_RULE_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SMS_CODE_RULE")

        @JvmField
        val APP_INFO_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_APP_INFO")

        private const val SMS_MSG_DIR = 0
        private const val SMS_MSG_ID = 1
        private const val SMS_CODE_RULE_DIR = 2
        private const val SMS_CODE_RULE_ID = 3
        private const val APP_INFO_DIR = 4
        private const val APP_INFO_ITEM = 5
        private val sUriMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            sUriMatcher.addURI(AUTHORITY, PATH_SMS_MSG, SMS_MSG_DIR)
            sUriMatcher.addURI(AUTHORITY, "$PATH_SMS_MSG/#", SMS_MSG_ID)
            sUriMatcher.addURI(AUTHORITY, PATH_SMS_CODE_RULE, SMS_CODE_RULE_DIR)
            sUriMatcher.addURI(AUTHORITY, "$PATH_SMS_CODE_RULE/#", SMS_CODE_RULE_ID)
            sUriMatcher.addURI(AUTHORITY, PATH_APP_INFO, APP_INFO_DIR)
            sUriMatcher.addURI(AUTHORITY, "$PATH_APP_INFO/*", APP_INFO_ITEM)
        }
    }
}
