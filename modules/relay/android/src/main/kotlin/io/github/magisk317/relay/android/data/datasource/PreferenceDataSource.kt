package io.github.magisk317.relay.android.data.datasource

import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceChangeSet
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import kotlinx.coroutines.flow.Flow

interface PreferenceDataSource {
    suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /**
     * 三态读取，沿用共享 core 的 [PrefRead] 词汇：[PrefRead.Miss] 表示这个键从未写过，调用方必须按
     * 自己的规则回退（例如「检测CI构建」未设置时回落到构建来源）；[PrefRead.Unavailable] 表示读不到。
     * 两者都不许被一个凭空默认值毒化——这正是 [getBoolean] 无法表达的差别。
     */
    suspend fun readBoolean(key: String): PrefRead<Boolean>
    suspend fun setBoolean(key: String, value: Boolean)

    suspend fun getString(key: String, defaultValue: String): String
    suspend fun setString(key: String, value: String)

    /**
     * SIM 卡槽备注的唯一读取入口（slot→key 映射只此一份）：
     * slot 0/1 返回对应备注，其他 slot 返回空串。正文与标题渲染共用。
     */
    suspend fun getSimSlotRemark(simSlot: Int): String

    suspend fun getInt(key: String, defaultValue: Int): Int
    suspend fun setInt(key: String, value: Int)

    suspend fun getFloat(key: String, defaultValue: Float): Float
    suspend fun setFloat(key: String, value: Float)

    /**
     * Execute multiple writes atomically in a single DataStore transaction.
     * If any write fails, all changes are rolled back.
     */
    suspend fun batchEdit(block: suspend PreferenceWriteScope.() -> Unit)

    /** Persist a pre-staged shared change set atomically and report backend acceptance. */
    suspend fun persist(changes: PreferenceChangeSet): Boolean

    fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean>
    fun getStringFlow(key: String, defaultValue: String): Flow<String>
    fun getIntFlow(key: String, defaultValue: Int): Flow<Int>
    fun getFloatFlow(key: String, defaultValue: Float): Flow<Float>
}

interface PreferenceWriteScope {
    suspend fun setBoolean(key: String, value: Boolean)
    suspend fun setString(key: String, value: String)
    suspend fun setInt(key: String, value: Int)
    suspend fun setFloat(key: String, value: Float)
}
