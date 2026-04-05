package io.github.magisk317.relay.data.update

import android.content.Context
import io.github.magisk317.smscode.runtime.common.update.UpdateArtifactConfig
import io.github.magisk317.smscode.runtime.common.update.UpgradeDownloader as SharedUpgradeDownloader
import java.io.File

object UpgradeDownloader {

    data class Progress(
        val bytesRead: Long,
        val totalBytes: Long,
        val percent: Float,
    )

    private val artifactConfig = UpdateArtifactConfig(
        apkFilePrefix = "XinyiRelay",
    )

    suspend fun download(
        context: Context,
        versionCode: Long,
        asset: UpgradeApkAsset,
        onProgress: (Progress) -> Unit = {},
    ): File {
        return SharedUpgradeDownloader.download(
            context = context,
            versionCode = versionCode,
            asset = asset,
            artifactConfig = artifactConfig,
        ) { progress ->
            onProgress(
                Progress(
                    bytesRead = progress.bytesRead,
                    totalBytes = progress.totalBytes,
                    percent = progress.percent,
                ),
            )
        }
    }
}
