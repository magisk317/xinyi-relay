package io.github.magisk317.relay.android.data.db.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import io.github.magisk317.relay.engine.model.AppInfoData
import kotlinx.serialization.Serializable

@Entity(tableName = "app_info")
@Parcelize
@Serializable
data class AppInfo @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    @SerialName("packageName")
    override val packageName: String = "",

    @ColumnInfo(name = "label")
    @SerialName("label")
    override val label: String? = null,

    @ColumnInfo(name = "blocked")
    @SerialName("blocked")
    @get:JvmName("isBlocked")
    override val blocked: Boolean = false,

    @ColumnInfo(name = "forwarding", defaultValue = "0")
    @SerialName("forwarding")
    @get:JvmName("isForwarding")
    override val forwarding: Boolean = false,

    @ColumnInfo(name = "forwarding_configured", defaultValue = "0")
    @SerialName("forwardingConfigured")
    override val forwardingConfigured: Boolean = false,

    @ColumnInfo(name = "notify_template", defaultValue = "")
    @SerialName("notifyTemplate")
    override val notifyTemplate: String = "",
) : Parcelable, AppInfoData
