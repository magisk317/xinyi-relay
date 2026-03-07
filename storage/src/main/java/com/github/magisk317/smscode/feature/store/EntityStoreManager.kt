package com.github.magisk317.smscode.feature.store

import android.content.Context
import com.github.magisk317.smscode.common.utils.JsonUtils
import com.github.magisk317.smscode.common.utils.StorageUtils
import com.github.magisk317.smscode.common.utils.XLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.ArrayList

/**
 * Put and get blocked app info in files.
 */
object EntityStoreManager {

    private const val CODE_RULE_TEMPLATE_FILE_NAME = "code_rule_template"
    private const val CODE_RULES_FILE_NAME = "code_rules"
    private const val BLOCKED_APPS_FILE_NAME = "blocked_apps"
    private const val FORWARDING_APPS_FILE_NAME = "forwarding_apps"
    private const val APP_CONFIGS_FILE_NAME = "app_configs"
    private const val PREV_CODE_RECORD = "prev_code_record"

    fun getStoreFile(context: Context, entityType: EntityType): File {
        val filename = when (entityType) {
            EntityType.BLOCKED_APP -> BLOCKED_APPS_FILE_NAME
            EntityType.FORWARDING_APP -> FORWARDING_APPS_FILE_NAME
            EntityType.APP_CONFIG -> APP_CONFIGS_FILE_NAME
            EntityType.CODE_RULES -> CODE_RULES_FILE_NAME
            EntityType.CODE_RULE_TEMPLATE -> CODE_RULE_TEMPLATE_FILE_NAME
            EntityType.PREV_SMS_MSG -> PREV_CODE_RECORD
        }
        return File(StorageUtils.getFilesDir(context), filename)
    }

    // @JvmStatic // Removed for inline
    @JvmStatic
    fun <T : Any> storeEntitiesToFile(
        context: Context,
        entityType: EntityType,
        entities: List<T>,
        clazz: Class<T>,
    ): Boolean {
        var osw: OutputStreamWriter? = null
        try {
            val storeFile = getStoreFile(context, entityType)
            // Truncate file first
            val jsonString = if (entities.isEmpty()) {
                "[]"
            } else {
                JsonUtils.listToJson(entities, clazz)
            }
            if (jsonString.isEmpty()) {
                XLog.e(
                    "store entities to file failed: empty json, type=$entityType size=${entities.size} clazz=${clazz.name}",
                )
                return false
            }

            osw = OutputStreamWriter(FileOutputStream(storeFile), StandardCharsets.UTF_8)
            osw.write(jsonString)

            // set file world writable
            StorageUtils.setFileWorldWritable(storeFile, 0)
            return true
        } catch (e: Exception) {
            XLog.e("store entities to file failed", e)
        } finally {
            if (osw != null) {
                try {
                    osw.close()
                } catch (ignored: IOException) {
                    // ignore
                }
            }
        }
        return false
    }

    // @JvmStatic // Removed for inline
    @JvmStatic
    fun <T : Any> storeEntityToFile(context: Context, entityType: EntityType, entity: T, clazz: Class<T>): Boolean {
        val entities = ArrayList<T>()
        entities.add(entity)
        return storeEntitiesToFile(context, entityType, entities, clazz)
    }

    @JvmStatic
    fun <T : Any> loadEntitiesFromFile(context: Context, entityType: EntityType, entityClass: Class<T>): List<T> {
        return loadEntitiesFromFile(getStoreFile(context, entityType), entityClass)
    }

    @JvmStatic
    fun <T : Any> loadEntitiesFromFile(storeFile: File, entityClass: Class<T>): List<T> {
        if (!storeFile.exists()) {
            return ArrayList()
        }
        if (storeFile.length() == 0L) {
            return ArrayList()
        }
        var isr: InputStreamReader? = null
        try {
            isr = InputStreamReader(
                FileInputStream(storeFile),
                StandardCharsets.UTF_8,
            )

            return JsonUtils.listFromJson(isr, entityClass)
        } catch (e: Exception) {
            XLog.e("load entities from file failed: ${storeFile.absolutePath}", e)
        } finally {
            if (isr != null) {
                try {
                    isr.close()
                } catch (ignored: IOException) {
                    XLog.e("Failed to close InputStreamReader", ignored)
                }
            }
        }
        return ArrayList()
    }

    @JvmStatic
    fun <T : Any> loadEntityFromFile(context: Context, entityType: EntityType, entityClass: Class<T>): T? {
        val entities = loadEntitiesFromFile(context, entityType, entityClass)
        return if (entities.isNotEmpty()) {
            entities[0]
        } else {
            null
        }
    }
}
