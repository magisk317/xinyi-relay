package io.github.magisk317.relay.android.diagnostics

import android.content.Context
import java.io.File
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleClearResult as SharedLogBundleClearResult
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleExportResult as SharedLogBundleExportResult
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleExporter as SharedLogBundleExporter

typealias LogBundleExportResult = SharedLogBundleExportResult
typealias LogBundleClearResult = SharedLogBundleClearResult

object LogBundleExporter {
    fun buildLogBundle(context: Context): LogBundleExportResult {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedLogBundleExporter.buildLogBundle(context)
    }

    fun shareLogBundle(context: Context, file: File) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedLogBundleExporter.shareLogBundle(context, file)
    }

    fun clearLogFolders(context: Context): LogBundleClearResult {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedLogBundleExporter.clearLogFolders(context)
    }
}
