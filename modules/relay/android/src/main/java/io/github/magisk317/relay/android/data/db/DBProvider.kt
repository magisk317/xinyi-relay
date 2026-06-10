package io.github.magisk317.relay.android.data.db

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.common.record.SmsMsgCursorContract
import kotlinx.coroutines.runBlocking

class DBProvider : ContentProvider() {
    private var mDatabase: AppDatabase? = null
    private lateinit var uriMatcher: UriMatcher
    private lateinit var authority: String

    override fun onCreate(): Boolean {
        context?.let {
            mDatabase = AppDatabase.getInstance(it)
            authority = "${it.packageName}.db.provider"
            uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
                addURI(authority, PATH_SMS_MSG, SMS_MSG_DIR)
                addURI(authority, "$PATH_SMS_MSG/#", SMS_MSG_ID)
                addURI(authority, PATH_SMS_CODE_RULE, SMS_CODE_RULE_DIR)
                addURI(authority, "$PATH_SMS_CODE_RULE/#", SMS_CODE_RULE_ID)
                addURI(authority, PATH_APP_INFO, APP_INFO_DIR)
                addURI(authority, "$PATH_APP_INFO/*", APP_INFO_ITEM)
            }
        }
        return true
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context ?: return null
        val uriType = uriMatcher.match(uri)
        if (!isCallerAllowedForMutation(ctx)) {
            XLog.w("DBProvider: deny insert uid=%d uri=%s", Binder.getCallingUid(), uri)
            return null
        }
        val path: String
        when (uriType) {
            SMS_MSG_DIR -> {
                val msg = SmsMsg(
                    sender = values?.getAsString("sender"),
                    body = values?.getAsString("body"),
                    date = values?.getAsLong("date") ?: 0L,
                    processedTime = values?.getAsLong("processed_time") ?: System.currentTimeMillis(),
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
                val dao = mDatabase!!.smsMsgDao()
                val id = synchronized(dao) {
                    runBlocking {
                        val existing = dao.getByFingerprint(
                            sender = msg.sender,
                            body = msg.body,
                            date = msg.date,
                            msgType = msg.msgType,
                        )
                        if (existing != null) {
                            dao.update(mergeSmsMsgForInsert(existing, msg))
                            existing.id
                        } else {
                            dao.insert(msg)
                        }
                    }
                }
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
        val ctx = context ?: return null
        val uriType = uriMatcher.match(uri)
        if (!isCallerAllowedForQuery(ctx, uriType)) {
            XLog.w("DBProvider: deny query uid=%d uri=%s", Binder.getCallingUid(), uri)
            return null
        }
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
        val ctx = context ?: return 0
        val uriType = uriMatcher.match(uri)
        if (!isCallerAllowedForMutation(ctx)) {
            XLog.w("DBProvider: deny delete uid=%d uri=%s", Binder.getCallingUid(), uri)
            return 0
        }
        val rowsDeleted: Int = when (uriType) {
            SMS_MSG_DIR -> deleteSmsMsg(selection, selectionArgs)
            SMS_MSG_ID -> uri.lastPathSegment?.toLongOrNull()?.let { deleteSmsMsgById(it) } ?: 0
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
            return deleteSmsMsgById(id)
        }
        throw IllegalArgumentException("Unsupported delete selection: $selection")
    }

    private fun deleteSmsMsgById(id: Long): Int = runBlocking {
        val dao = mDatabase!!.smsMsgDao()
        val msg = dao.getById(id) ?: return@runBlocking 0
        dao.delete(msg)
        1
    }

    private fun querySmsCodeRules(projection: Array<String>?): Cursor = runBlocking {
        val rules = mDatabase!!.smsCodeRuleDao().getAll()
        val columns = projection ?: arrayOf("company", "code_keyword", "code_regex", "_id")
        val cursor = MatrixCursor(columns)
        rules.forEach { rule ->
            cursor.addRow(buildRow(columns) { column -> valueFromSmsCodeRule(rule, column) })
        }
        return@runBlocking cursor
    }

    private fun querySmsMsgs(projection: Array<String>?, sortOrder: String?): Cursor = runBlocking {
        val rows = mDatabase!!.smsMsgDao().getAll().let { list ->
            when (sortOrder?.trim()?.lowercase()) {
                "date asc" -> list.sortedBy { it.date }
                "date desc", null, "" -> list.sortedByDescending { it.date }
                else -> list
            }
        }
        val columns = projection ?: SmsMsgCursorContract.defaultColumns
        val cursor = MatrixCursor(columns)
        rows.forEach { msg ->
            cursor.addRow(buildRow(columns) { column -> valueFromSmsMsg(msg, column) })
        }
        return@runBlocking cursor
    }

    private fun querySmsMsgById(projection: Array<String>?, uri: Uri): Cursor = runBlocking {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IllegalArgumentException("Invalid URI: $uri")
        val columns = projection ?: SmsMsgCursorContract.defaultColumns
        val cursor = MatrixCursor(columns)
        val msg = mDatabase!!.smsMsgDao().getById(id)
        if (msg != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromSmsMsg(msg, column) })
        }
        return@runBlocking cursor
    }

    private fun querySmsCodeRuleById(projection: Array<String>?, uri: Uri): Cursor = runBlocking {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IllegalArgumentException("Invalid URI: $uri")
        val columns = projection ?: arrayOf("company", "code_keyword", "code_regex", "_id")
        val cursor = MatrixCursor(columns)
        val rule = mDatabase!!.smsCodeRuleDao().getById(id)
        if (rule != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromSmsCodeRule(rule, column) })
        }
        return@runBlocking cursor
    }

    private fun queryAppInfo(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Cursor = runBlocking {
        var rows = mDatabase!!.appInfoDao().getAll()
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
        return@runBlocking cursor
    }

    private fun queryAppInfoByPackageName(projection: Array<String>?, uri: Uri): Cursor = runBlocking {
        val packageName = uri.lastPathSegment.orEmpty()
        val columns = projection ?: arrayOf("package_name", "label", "blocked", "forwarding", "notify_template")
        val cursor = MatrixCursor(columns)
        if (packageName.isBlank()) {
            return@runBlocking cursor
        }
        val app = mDatabase!!.appInfoDao().getByPackageName(packageName)
        if (app != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromAppInfo(app, column) })
        }
        return@runBlocking cursor
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
        SmsMsgCursorContract.valueFromRecord(msg, column) ?: when (column) {
            "sim_slot" -> msg.simSlot
            "sub_id" -> msg.subId
            "contact_name" -> msg.contactName
            "phone_area" -> msg.phoneArea
            else -> null
        }

    private fun valueFromAppInfo(app: AppInfo, column: String): Any? =
        when (column) {
            "package_name" -> app.packageName
            "label" -> app.label
            "blocked" -> if (app.blocked) 1 else 0
            "forwarding" -> if (app.forwarding) 1 else 0
            "forwarding_configured" -> if (app.forwardingConfigured) 1 else 0
            "notify_template" -> app.notifyTemplate
            else -> null
        }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val ctx = context ?: return 0
        val uriType = uriMatcher.match(uri)
        if (!isCallerAllowedForMutation(ctx)) {
            XLog.w("DBProvider: deny update uid=%d uri=%s", Binder.getCallingUid(), uri)
            return 0
        }
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

    private fun isCallerAllowedForQuery(ctx: Context, uriType: Int): Boolean {
        if (isCallerSelf(ctx)) return true
        if (!isPrivilegedCaller(ctx)) return false
        return when (uriType) {
            SMS_CODE_RULE_DIR, SMS_CODE_RULE_ID, APP_INFO_DIR, APP_INFO_ITEM -> true
            else -> false
        }
    }

    private fun isCallerAllowedForMutation(ctx: Context): Boolean = isCallerSelf(ctx)

    private fun isCallerSelf(ctx: Context): Boolean = Binder.getCallingUid() == ctx.applicationInfo?.uid

    private fun isPrivilegedCaller(ctx: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (uid < Process.FIRST_APPLICATION_UID) return true
        return try {
            val packages = ctx.packageManager.getPackagesForUid(uid) ?: return false
            packages.any { packageName -> isSystemApp(ctx, packageName) }
        } catch (_: Exception) {
            false
        }
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        (info.flags and
            (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    } catch (_: Exception) {
        false
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

    private fun updateSmsMsgById(id: Long, values: ContentValues?): Int = runBlocking {
        val dao = mDatabase!!.smsMsgDao()
        val existing = dao.getById(id) ?: return@runBlocking 0
        val updated = existing.copy(
            sender = values?.getAsString("sender") ?: existing.sender,
            body = values?.getAsString("body") ?: existing.body,
            date = values?.getAsLong("date") ?: existing.date,
            processedTime = if (values?.containsKey("processed_time") == true) {
                values.getAsLong("processed_time") ?: 0L
            } else {
                existing.processedTime
            },
            company = values?.getAsString("company") ?: existing.company,
            smsCode = values?.getAsString("sms_code") ?: existing.smsCode,
            packageName = values?.getAsString("package_name") ?: existing.packageName,
            notifyChannelId = values?.getAsString("notify_channel_id") ?: existing.notifyChannelId,
            simSlot = values?.getAsInteger("sim_slot") ?: existing.simSlot,
            subId = values?.getAsInteger("sub_id") ?: existing.subId,
            contactName = values?.getAsString("contact_name") ?: existing.contactName,
            phoneArea = values?.getAsString("phone_area") ?: existing.phoneArea,
            msgType = values?.getAsInteger("msg_type") ?: existing.msgType,
            callType = values?.getAsInteger("call_type") ?: existing.callType,
            forwardStatus = values?.getAsInteger("forward_status") ?: existing.forwardStatus,
            forwardTarget = values?.getAsString("forward_target") ?: existing.forwardTarget,
            forwardMessage = values?.getAsString("forward_message") ?: existing.forwardMessage,
            forwardTime = values?.getAsLong("forward_time") ?: existing.forwardTime,
        )
        dao.update(updated)
        1
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
        val dao = mDatabase!!.appInfoDao()
        val existing = runBlocking {
            dao.getByPackageName(packageName)
        } ?: AppInfo(packageName = packageName)
        val blocked = parseBooleanValue(values, "blocked", existing.blocked)
        val forwarding = parseBooleanValue(values, "forwarding", existing.forwarding)
        val forwardingConfigured = when {
            values?.containsKey("forwarding_configured") == true -> parseBooleanValue(
                values,
                "forwarding_configured",
                existing.forwardingConfigured,
            )
            values?.containsKey("forwarding") == true -> true
            else -> existing.forwardingConfigured
        }
        val label = when {
            values?.containsKey("label") == true -> values.getAsString("label")
            else -> existing.label
        }
        val notifyTemplate = when {
            values?.containsKey("notify_template") == true -> values.getAsString("notify_template").orEmpty()
            else -> existing.notifyTemplate
        }
        runBlocking {
            dao.insert(
                existing.copy(
                    label = label,
                    blocked = blocked,
                    forwarding = forwarding,
                    forwardingConfigured = forwardingConfigured,
                    notifyTemplate = notifyTemplate,
                ),
            )
        }
        return 1
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

    private fun deleteAppInfoByPackageName(uri: Uri): Int = runBlocking {
        val packageName = uri.lastPathSegment.orEmpty()
        if (packageName.isBlank()) {
            return@runBlocking 0
        }
        val dao = mDatabase!!.appInfoDao()
        val app = dao.getByPackageName(packageName) ?: return@runBlocking 0
        dao.delete(app)
        1
    }

    companion object {
        private const val PATH_SMS_MSG = "sms_msg"
        private const val PATH_SMS_CODE_RULE = "sms_code_rule"
        private const val PATH_APP_INFO = "app_info"

        private const val SMS_MSG_DIR = 0
        private const val SMS_MSG_ID = 1
        private const val SMS_CODE_RULE_DIR = 2
        private const val SMS_CODE_RULE_ID = 3
        private const val APP_INFO_DIR = 4
        private const val APP_INFO_ITEM = 5

        fun authority(context: Context): String = "${context.packageName}.db.provider"

        fun smsMsgContentUri(context: Context): Uri =
            Uri.parse("content://${context.packageName}.db.provider/$PATH_SMS_MSG")

        fun smsCodeRuleContentUri(context: Context): Uri =
            Uri.parse("content://${context.packageName}.db.provider/$PATH_SMS_CODE_RULE")

        fun appInfoContentUri(context: Context): Uri =
            Uri.parse("content://${context.packageName}.db.provider/$PATH_APP_INFO")
    }
}

fun mergeSmsMsgForInsert(existing: SmsMsg, incoming: SmsMsg): SmsMsg {
    val incomingHasForwardState = incoming.forwardStatus != SmsMsg.FORWARD_STATUS_NONE ||
        !incoming.forwardTarget.isNullOrBlank() ||
        !incoming.forwardMessage.isNullOrBlank() ||
        incoming.forwardTime > 0L
    val preferred = preferRicherSmsMsg(existing, incoming)
    val fallback = if (preferred === existing) incoming else existing
    return existing.copy(
        sender = preferred.sender ?: fallback.sender ?: existing.sender,
        body = preferred.body ?: fallback.body ?: existing.body,
        date = incoming.date.takeIf { it > 0L } ?: existing.date,
        processedTime = existing.processedTime.takeIf { it > 0L }
            ?: incoming.processedTime.takeIf { it > 0L }
            ?: 0L,
        company = preferred.company?.takeIf { it.isNotBlank() }
            ?: fallback.company?.takeIf { it.isNotBlank() }
            ?: existing.company,
        smsCode = preferred.smsCode?.takeIf { it.isNotBlank() }
            ?: fallback.smsCode?.takeIf { it.isNotBlank() }
            ?: existing.smsCode,
        packageName = preferred.packageName?.takeIf { it.isNotBlank() }
            ?: fallback.packageName?.takeIf { it.isNotBlank() }
            ?: existing.packageName,
        notifyChannelId = preferred.notifyChannelId.takeIf { it.isNotBlank() }
            ?: fallback.notifyChannelId.takeIf { it.isNotBlank() }
            ?: existing.notifyChannelId,
        simSlot = preferred.simSlot.takeIf { it >= 0 }
            ?: fallback.simSlot.takeIf { it >= 0 }
            ?: existing.simSlot,
        subId = preferred.subId.takeIf { it > 0 }
            ?: fallback.subId.takeIf { it > 0 }
            ?: existing.subId,
        contactName = preferred.contactName.takeIf { it.isNotBlank() }
            ?: fallback.contactName.takeIf { it.isNotBlank() }
            ?: existing.contactName,
        phoneArea = preferred.phoneArea.takeIf { it.isNotBlank() }
            ?: fallback.phoneArea.takeIf { it.isNotBlank() }
            ?: existing.phoneArea,
        msgType = incoming.msgType,
        callType = preferred.callType.takeIf { it != 0 }
            ?: fallback.callType.takeIf { it != 0 }
            ?: existing.callType,
        sessionKey = preferred.sessionKey.takeIf { it.isNotBlank() }
            ?: fallback.sessionKey.takeIf { it.isNotBlank() }
            ?: existing.sessionKey,
        forwardStatus = if (incomingHasForwardState) incoming.forwardStatus else existing.forwardStatus,
        forwardTarget = if (incomingHasForwardState) incoming.forwardTarget else existing.forwardTarget,
        forwardMessage = if (incomingHasForwardState) incoming.forwardMessage else existing.forwardMessage,
        forwardTime = if (incomingHasForwardState) incoming.forwardTime else existing.forwardTime,
    )
}

private fun preferRicherSmsMsg(existing: SmsMsg, incoming: SmsMsg): SmsMsg {
    val existingScore = scoreSmsMsg(existing)
    val incomingScore = scoreSmsMsg(incoming)
    return when {
        incomingScore > existingScore -> incoming
        incomingScore < existingScore -> existing
        incoming.date > existing.date -> incoming
        else -> existing
    }
}

private fun scoreSmsMsg(record: SmsMsg): Int {
    var score = 0
    val pkg = record.packageName.orEmpty()
    if (pkg.isNotBlank() && !isSystemSmsPackage(pkg)) score += 4
    if (record.company.orEmpty().isNotBlank()) score += 2
    if (record.simSlot >= 0) score += 2
    if (record.sender.orEmpty().isNotBlank()) score += 1
    if (record.contactName.isNotBlank()) score += 1
    return score
}

private fun isSystemSmsPackage(packageName: String): Boolean {
    return packageName == "com.android.mms" ||
        packageName == "com.android.messaging" ||
        packageName == "com.google.android.apps.messaging"
}
