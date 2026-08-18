package io.github.magisk317.relay.android.data.db

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import androidx.room.withTransaction
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.dao.SmsMsgDao
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.android.platform.ipc.ProviderCallerPolicy
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.runtime.common.ipc.RuntimeStateProviderContract
import io.github.magisk317.smscode.runtime.common.record.SmsMsgCursorContract
import io.github.magisk317.smscode.runtime.common.utils.SharedRuntimeGate
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.smscode.domain.utils.CodeRecordSimilarityUtils
import kotlinx.coroutines.runBlocking

class DBProvider : ContentProvider() {
    private var mDatabase: AppDatabase? = null
    private lateinit var uriMatcher: UriMatcher
    private lateinit var authority: String

    override fun onCreate(): Boolean {
        context?.let {
            mDatabase = AppDatabase.getInstance(it)
            StorageUtils.repairExternalAppDataPermissions(it)
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

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!isCallerAllowedForRuntimeState(ctx)) {
            XLog.w("DBProvider: deny call uid=%d method=%s", Binder.getCallingUid(), method)
            return null
        }
        return when (method) {
            RuntimeStateProviderContract.METHOD_CLAIM_RUNTIME_GATE -> claimRuntimeGate(ctx, arg, extras)
            RuntimeStateProviderContract.METHOD_RECORD_HOOK_HEARTBEAT -> recordHookHeartbeat(ctx, extras)
            else -> super.call(method, arg, extras)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context ?: return null
        val uriType = uriMatcher.match(uri)
        if (!isCallerAllowedForInsert(ctx, uriType)) {
            XLog.w("DBProvider: deny insert uid=%d uri=%s", Binder.getCallingUid(), uri)
            return null
        }
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
                    simSlot = values?.getAsInteger("sim_slot") ?: -1,
                    subId = values?.getAsInteger("sub_id") ?: 0,
                    contactName = values?.getAsString("contact_name").orEmpty(),
                    phoneArea = values?.getAsString("phone_area").orEmpty(),
                    msgType = values?.getAsInteger("msg_type") ?: SmsMsg.MSG_TYPE_SMS,
                    callType = values?.getAsInteger("call_type") ?: 0,
                    sessionKey = values?.getAsString("session_key").orEmpty(),
                    forwardStatus = values?.getAsInteger("forward_status") ?: SmsMsg.FORWARD_STATUS_NONE,
                    forwardTarget = values?.getAsString("forward_target"),
                    forwardMessage = values?.getAsString("forward_message"),
                    forwardTime = values?.getAsLong("forward_time") ?: 0L,
                )
                val database = mDatabase ?: return null
                val outcome = runBlocking {
                    database.withTransaction {
                        insertSmsMsgOrGetExisting(
                            dao = database.smsMsgDao(),
                            incoming = msg,
                            deduplicate = parseBooleanValue(values, KEY_DEDUPLICATE, true),
                        )
                    }
                }
                val resultUri = ContentUris.withAppendedId(uri, outcome.id)
                    .buildUpon()
                    .apply {
                        if (outcome.duplicate) appendQueryParameter(QUERY_DUPLICATE, "true")
                    }
                    .build()
                // This is also the app-owned signal used by RemoteAgentInitializer to restore
                // RelayRecordRepository's record-upload scheduling after hook writes moved to
                // the provider process. Notify accepted duplicates too, matching the old
                // repository path which scheduled after duplicate resolution.
                context?.contentResolver?.notifyChange(resultUri, null)
                return resultUri
            }

            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
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

    private suspend fun insertSmsMsgOrGetExisting(
        dao: SmsMsgDao,
        incoming: SmsMsg,
        deduplicate: Boolean,
    ): SmsMsgInsertOutcome {
        dao.getByFingerprint(
            sender = incoming.sender,
            body = incoming.body,
            date = incoming.date,
            msgType = incoming.msgType,
        )?.let { existing ->
            dao.update(mergeSmsMsgForInsert(existing, incoming))
            return SmsMsgInsertOutcome(id = existing.id, duplicate = true)
        }

        if (deduplicate) {
            findWindowDuplicate(dao, incoming)?.let { existing ->
                return SmsMsgInsertOutcome(id = existing.id, duplicate = true)
            }
        }

        val insertedId = dao.insertIfAbsent(incoming)
        if (insertedId > 0L) {
            return SmsMsgInsertOutcome(id = insertedId, duplicate = false)
        }

        val raced = dao.getByFingerprint(
            sender = incoming.sender,
            body = incoming.body,
            date = incoming.date,
            msgType = incoming.msgType,
        ) ?: error("SMS fingerprint conflict without an existing row")
        dao.update(mergeSmsMsgForInsert(raced, incoming))
        return SmsMsgInsertOutcome(id = raced.id, duplicate = true)
    }

    private suspend fun findWindowDuplicate(dao: SmsMsgDao, incoming: SmsMsg): SmsMsg? {
        val timestamp = incoming.date.takeIf { it > 0L } ?: System.currentTimeMillis()
        val from = (timestamp - CodeRecordSimilarityUtils.DEFAULT_WINDOW_MS).coerceAtLeast(0L)
        val to = timestamp + CodeRecordSimilarityUtils.DEFAULT_WINDOW_MS
        val code = incoming.smsCode
        if (!code.isNullOrBlank()) {
            dao.getByCodeInRange(code, incoming.msgType, from, to)
                .firstOrNull { existing ->
                    CodeRecordSimilarityUtils.crossSourceMatchScore(
                        existingCode = existing.smsCode,
                        existingBody = existing.body,
                        existingCompany = existing.company,
                        existingSender = existing.sender,
                        incomingCode = incoming.smsCode,
                        incomingBody = incoming.body,
                        incomingCompany = incoming.company,
                        incomingSender = incoming.sender,
                    ) > 0
                }
                ?.let { return it }

            incoming.packageName?.takeIf(String::isNotBlank)?.let { packageName ->
                dao.getByCodeAndPackageInRange(code, packageName, incoming.msgType, from, to)
                    ?.let { return it }
            }
            incoming.company?.takeIf(String::isNotBlank)?.let { company ->
                dao.getByCodeAndCompanyInRange(code, company, incoming.msgType, from, to)
                    ?.let { return it }
            }
        }

        if (!incoming.sender.isNullOrBlank() && !incoming.body.isNullOrBlank()) {
            dao.getByFingerprintInRange(incoming.sender, incoming.body, incoming.msgType, from, to)
                ?.let { return it }
        }
        return null
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
        if (ProviderCallerPolicy.isSelf(ctx)) return true
        if (!ProviderCallerPolicy.isSelfOrSystemScope(ctx)) return false
        return when (uriType) {
            SMS_CODE_RULE_DIR, SMS_CODE_RULE_ID, APP_INFO_DIR, APP_INFO_ITEM -> true
            else -> false
        }
    }

    private fun isCallerAllowedForMutation(ctx: Context): Boolean =
        ProviderCallerPolicy.isSelf(ctx)

    private fun isCallerAllowedForInsert(ctx: Context, uriType: Int): Boolean =
        when (uriType) {
            SMS_MSG_DIR -> ProviderCallerPolicy.isSelf(ctx) || ProviderCallerPolicy.isSelfOrSystemScope(ctx)
            else -> ProviderCallerPolicy.isSelf(ctx)
        }

    private fun isCallerAllowedForRuntimeState(ctx: Context): Boolean =
        ProviderCallerPolicy.isSelf(ctx) || ProviderCallerPolicy.isSelfOrSystemScope(ctx)

    private fun claimRuntimeGate(ctx: Context, fileName: String?, extras: Bundle?): Bundle? {
        val resolvedFileName = fileName?.takeIf(String::isNotBlank) ?: return null
        val keys = extras?.getStringArrayList(RuntimeStateProviderContract.EXTRA_KEYS).orEmpty()
        val windowMs = extras?.getLong(RuntimeStateProviderContract.EXTRA_WINDOW_MS, 0L) ?: 0L
        val maxEntries = extras?.getInt(
            RuntimeStateProviderContract.EXTRA_MAX_ENTRIES,
            RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES,
        ) ?: RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES
        if (windowMs <= 0L || maxEntries !in 1..MAX_RUNTIME_GATE_ENTRIES) return null

        val result = runCatching {
            SharedRuntimeGate.claimAllWithinWindow(
                file = SharedRuntimeGate.internalGateFile(ctx, resolvedFileName),
                keys = keys,
                windowMs = windowMs,
                maxEntries = maxEntries,
            )
        }.onFailure { error ->
            XLog.w(
                "DBProvider: runtime gate failed file=%s err=%s",
                resolvedFileName,
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrNull() ?: return null

        return Bundle().apply {
            putBoolean(RuntimeStateProviderContract.RESULT_OK, true)
            putBoolean(RuntimeStateProviderContract.RESULT_CLAIMED, result.claimed)
            putLong(
                RuntimeStateProviderContract.RESULT_AGE_MS,
                result.ageMs ?: RuntimeStateProviderContract.NO_AGE_MS,
            )
            result.key?.let { putString(RuntimeStateProviderContract.RESULT_BLOCKED_KEY, it) }
        }
    }

    private fun recordHookHeartbeat(ctx: Context, extras: Bundle?): Bundle? {
        val packageName = extras
            ?.getString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME)
            ?.toRuntimeStateValue()
            .orEmpty()
        val processName = extras
            ?.getString(RuntimeStateProviderContract.EXTRA_PROCESS_NAME)
            ?.toRuntimeStateValue()
            .orEmpty()
        val source = extras
            ?.getString(RuntimeStateProviderContract.EXTRA_SOURCE)
            ?.toRuntimeStateValue()
            .orEmpty()
        if (packageName.isBlank() || processName.isBlank() || source.isBlank()) return null

        val ok = runCatching {
            ActivationDiagnosticsStore.recordHookHeartbeat(
                context = ctx.applicationContext ?: ctx,
                packageName = packageName,
                processName = processName,
                source = source,
                verboseLogging = extras?.getBoolean(
                    RuntimeStateProviderContract.EXTRA_VERBOSE_LOGGING,
                    false,
                ) ?: false,
                route = extras
                    ?.getString(RuntimeStateProviderContract.EXTRA_ROUTE)
                    ?.toRuntimeStateValue()
                    ?.takeIf(String::isNotBlank)
                    ?: RuntimeLogStore.ROUTE_SMS_HOOK,
            )
        }.onFailure { error ->
            XLog.w(
                "DBProvider: hook heartbeat failed source=%s err=%s",
                source,
                error.message ?: error.javaClass.simpleName,
            )
        }.isSuccess
        return Bundle().apply { putBoolean(RuntimeStateProviderContract.RESULT_OK, ok) }
    }

    private fun String.toRuntimeStateValue(): String =
        replace('\n', ' ').replace('\r', ' ').take(MAX_RUNTIME_STATE_VALUE_LENGTH)

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
        private const val KEY_DEDUPLICATE = "deduplicate"
        private const val QUERY_DUPLICATE = "duplicate"

        private const val SMS_MSG_DIR = 0
        private const val SMS_MSG_ID = 1
        private const val SMS_CODE_RULE_DIR = 2
        private const val SMS_CODE_RULE_ID = 3
        private const val APP_INFO_DIR = 4
        private const val APP_INFO_ITEM = 5
        private const val MAX_RUNTIME_GATE_ENTRIES = 4_096
        private const val MAX_RUNTIME_STATE_VALUE_LENGTH = 256

        fun authority(context: Context): String = "${context.packageName}.db.provider"

        fun smsMsgContentUri(context: Context): Uri =
            Uri.parse("content://${context.packageName}.db.provider/$PATH_SMS_MSG")

        fun smsCodeRuleContentUri(context: Context): Uri =
            Uri.parse("content://${context.packageName}.db.provider/$PATH_SMS_CODE_RULE")

        fun appInfoContentUri(context: Context): Uri =
            Uri.parse("content://${context.packageName}.db.provider/$PATH_APP_INFO")
    }

    private data class SmsMsgInsertOutcome(
        val id: Long,
        val duplicate: Boolean,
    )
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
